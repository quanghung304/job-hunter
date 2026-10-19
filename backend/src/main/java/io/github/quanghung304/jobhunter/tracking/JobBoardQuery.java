package io.github.quanghung304.jobhunter.tracking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.Eligibility;
import io.github.quanghung304.jobhunter.enrich.ExtractionService;
import io.github.quanghung304.jobhunter.enrich.JobAttributes.RemoteType;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The board (api-spec {@code GET /api/jobs}): read-only SQL joining {@code job}, the profile's
 * {@code job_assessment} for the current score prompt version, {@code job_extraction} for the
 * current extract prompt version and {@code job_tracking}. Every join is limited to one profile.
 *
 * <p>Default view (no filter): tracking status NEW or SHORTLISTED, prefilter PASS, eligibility
 * not INELIGIBLE, job ACTIVE. Any filter replaces the default view; EXPIRED jobs stay hidden
 * unless {@code includeExpired}.
 */
@Service
@Transactional(readOnly = true)
public class JobBoardQuery {

    private static final String BASE = """
            with board as (
              select j.id, j.title, j.company_name, j.location_text, j.posted_at, j.first_seen_at,
                     j.status as job_status, coalesce(j.posted_at, j.first_seen_at) as posted_sort,
                     a.score, a.summary, a.eligibility, a.prefilter_result,
                     e.attributes ->> 'remoteType' as remote_type,
                     e.attributes -> 'allowedRegions' as allowed_regions,
                     e.attributes -> 'employmentTypes' as employment_types,
                     coalesce(t.status, 'NEW') as tracking_status,
                     case when a.score is not null then 'SCORED'
                          when a.status = 'FAILED' then 'FAILED'
                          when a.eligibility is not null then 'ANALYZED'
                          when e.status = 'FAILED' then 'FAILED'
                          else 'NOT_ANALYZED' end as analysis_stage
              from job j
              left join job_assessment a on a.job_id = j.id and a.profile_id = :profileId
                                        and a.score_prompt_version = :scoreVersion
              left join job_extraction e on e.job_id = j.id and e.prompt_version = :extractVersion
              left join job_tracking t on t.job_id = j.id and t.profile_id = :profileId
            )
            """;

    private static final String COLUMNS = """
            select b.*,
                   array(select distinct r.source_code from raw_posting r where r.job_id = b.id
                         order by r.source_code) as source_codes
            from board b
            """;

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ExtractionService extraction;
    private final AssessmentService assessments;
    private final ObjectMapper json;
    private final Clock clock;

    public JobBoardQuery(NamedParameterJdbcTemplate jdbc, ExtractionService extraction,
            AssessmentService assessments, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.extraction = extraction;
        this.assessments = assessments;
        this.json = json;
        this.clock = clock;
    }

    /** One page of the board of this profile; {@code page} is 0-based. */
    public JobBoardPage page(String profileId, JobBoardFilter filter, JobSort sort, int page, int size) {
        MapSqlParameterSource params = params(profileId);
        String where = where(filter, params);
        Long total = jdbc.queryForObject(BASE + "select count(*) from board b" + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<JobBoardRow> rows = jdbc.query(BASE + COLUMNS + where + orderBy(sort) + " limit :limit offset :offset",
                params, this::row);
        return new JobBoardPage(rows, page, size, total == null ? 0 : total);
    }

    /** Number of jobs in the default view of this profile ({@code toReview} on the admin page). */
    public long countDefaultView(String profileId) {
        MapSqlParameterSource params = params(profileId);
        Long total = jdbc.queryForObject(BASE + "select count(*) from board b" + where(JobBoardFilter.NONE, params),
                params, Long.class);
        return total == null ? 0 : total;
    }

    /** One job as the board shows it to this profile, whatever its status. */
    public Optional<JobBoardRow> find(long jobId, String profileId) {
        MapSqlParameterSource params = params(profileId).addValue("jobId", jobId);
        return jdbc.query(BASE + COLUMNS + " where b.id = :jobId", params, this::row).stream().findFirst();
    }

    private MapSqlParameterSource params(String profileId) {
        return new MapSqlParameterSource()
                .addValue("profileId", profileId)
                .addValue("scoreVersion", assessments.scorePromptVersion())
                .addValue("extractVersion", extraction.promptVersion());
    }

    private String where(JobBoardFilter filter, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        if (filter.isEmpty()) {
            conditions.add("b.tracking_status in ('NEW', 'SHORTLISTED')");
            conditions.add("b.prefilter_result = 'PASS'");
            conditions.add("(b.eligibility is null or b.eligibility <> 'INELIGIBLE')");
            conditions.add("b.job_status = 'ACTIVE'");
            return " where " + String.join(" and ", conditions);
        }
        if (!filter.includeExpired()) {
            conditions.add("b.job_status = 'ACTIVE'");
        }
        in(conditions, params, "b.tracking_status", "statuses", filter.statuses());
        in(conditions, params, "b.eligibility", "eligibilities", filter.eligibilities());
        in(conditions, params, "b.analysis_stage", "stages", filter.analysisStages());
        if (filter.minScore() != null) {
            conditions.add("b.score >= :minScore");
            params.addValue("minScore", filter.minScore());
        }
        if (filter.maxScore() != null) {
            conditions.add("b.score <= :maxScore");
            params.addValue("maxScore", filter.maxScore());
        }
        if (!filter.sources().isEmpty()) {
            conditions.add("exists (select 1 from raw_posting r where r.job_id = b.id and r.source_code in (:sources))");
            params.addValue("sources", filter.sources());
        }
        if (!filter.employmentTypes().isEmpty()) {
            conditions.add("""
                    exists (select 1 from jsonb_array_elements_text(coalesce(b.employment_types, cast('[]' as jsonb))) et
                            where et in (:employmentTypes))""");
            params.addValue("employmentTypes", names(filter.employmentTypes()));
        }
        if (filter.postedWithinDays() != null) {
            conditions.add("b.posted_sort >= :postedSince");
            params.addValue("postedSince",
                    Timestamp.from(clock.instant().minus(Duration.ofDays(filter.postedWithinDays()))));
        }
        if (filter.q() != null) {
            conditions.add("(b.title ilike :q or b.company_name ilike :q)");
            params.addValue("q", "%" + escapeLike(filter.q()) + "%");
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    private static void in(List<String> conditions, MapSqlParameterSource params, String column, String name,
            Collection<? extends Enum<?>> values) {
        if (!values.isEmpty()) {
            conditions.add(column + " in (:" + name + ")");
            params.addValue(name, names(values));
        }
    }

    private static List<String> names(Collection<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().toList();
    }

    private static String orderBy(JobSort sort) {
        return switch (sort) {
            case SCORE -> " order by b.score desc nulls last, b.posted_sort desc, b.id desc";
            case POSTED -> " order by b.posted_sort desc, b.id desc";
        };
    }

    /** {@code ilike} uses backslash as the escape character. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private JobBoardRow row(ResultSet rs, int rowNum) throws SQLException {
        return new JobBoardRow(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("company_name"),
                rs.getString("location_text"),
                rs.getObject("score", Integer.class),
                rs.getString("summary"),
                enumOrNull(Eligibility.class, rs.getString("eligibility")),
                enumOrNull(RemoteType.class, rs.getString("remote_type")),
                strings(rs.getString("allowed_regions")),
                strings(rs.getString("employment_types")).stream()
                        .map(type -> enumOrNull(EmploymentType.class, type))
                        .filter(type -> type != null)
                        .toList(),
                instant(rs, "posted_at"),
                instant(rs, "first_seen_at"),
                array(rs.getArray("source_codes")),
                TrackingStatus.valueOf(rs.getString("tracking_status")),
                JobStatus.valueOf(rs.getString("job_status")),
                AnalysisStage.valueOf(rs.getString("analysis_stage")));
    }

    private List<String> strings(String jsonArray) {
        if (jsonArray == null) {
            return List.of();
        }
        try {
            List<String> values = json.readValue(jsonArray, STRINGS);
            return values == null ? List.of() : values.stream().filter(v -> v != null).toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unreadable JSON array in job_extraction.attributes", e);
        }
    }

    private static List<String> array(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String name) {
        if (name == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
