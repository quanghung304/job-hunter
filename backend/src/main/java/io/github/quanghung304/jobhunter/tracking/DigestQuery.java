package io.github.quanghung304.jobhunter.tracking;

import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.ExtractionService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only data of the daily Telegram digest (PRD F2.5, T-015) for one profile: ACTIVE jobs whose
 * tracking status is NEW and that the owner has not marked Not eligible (ADR-0011).
 */
@Service
@Transactional(readOnly = true)
public class DigestQuery {

    /** Job, the profile's assessment (current score prompt) and extraction, tracking: NEW, ACTIVE, not INELIGIBLE. */
    private static final String FROM = """
            from job j
            join job_assessment a on a.job_id = j.id and a.profile_id = :profileId
                                 and a.score_prompt_version = :scoreVersion
            left join job_extraction e on e.job_id = j.id and e.prompt_version = :extractVersion
            left join job_tracking t on t.job_id = j.id and t.profile_id = :profileId
            where j.status = 'ACTIVE'
              and coalesce(t.status, 'NEW') = 'NEW'
              and (t.eligibility_decision is null or t.eligibility_decision <> 'INELIGIBLE')
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ExtractionService extraction;
    private final AssessmentService assessments;

    public DigestQuery(NamedParameterJdbcTemplate jdbc, ExtractionService extraction, AssessmentService assessments) {
        this.jdbc = jdbc;
        this.extraction = extraction;
        this.assessments = assessments;
    }

    /**
     * A scored job for the digest; {@code postingUrl} is the first posting seen (attribution link),
     * {@code scoredAt} the last change of its assessment row.
     */
    public record ScoredJob(long id, String title, String company, int score, String summary, String postingUrl,
            Instant scoredAt) {
    }

    /** Prefilter PASS jobs nobody analyzed yet (no checks, no score, no failed attempt). */
    public long countAwaitingAnalysis(String profileId) {
        Long count = jdbc.queryForObject("select count(*) " + FROM + """
                  and a.prefilter_result = 'PASS'
                  and a.eligibility_checks is null and a.score is null and a.status <> 'FAILED'
                  and (e.status is null or e.status <> 'FAILED')
                """, params(profileId), Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Scored jobs with {@code score >= minScore} whose assessment changed after {@code since} (all
     * when null), best first, then oldest id.
     */
    public List<ScoredJob> scoredSince(String profileId, int minScore, Instant since) {
        MapSqlParameterSource params = params(profileId).addValue("minScore", minScore);
        String sinceCondition = "";
        if (since != null) {
            params.addValue("since", Timestamp.from(since));
            sinceCondition = " and a.updated_at > :since";
        }
        return jdbc.query("""
                select j.id, j.title, j.company_name, a.score, a.summary, a.updated_at,
                       (select r.url from raw_posting r where r.job_id = j.id order by r.id limit 1) as posting_url
                """ + FROM + " and a.score is not null and a.score >= :minScore" + sinceCondition
                        + " order by a.score desc, j.id",
                params,
                (rs, i) -> new ScoredJob(rs.getLong("id"), rs.getString("title"), rs.getString("company_name"),
                        rs.getInt("score"), rs.getString("summary"), rs.getString("posting_url"),
                        rs.getTimestamp("updated_at").toInstant()));
    }

    private MapSqlParameterSource params(String profileId) {
        return new MapSqlParameterSource()
                .addValue("profileId", profileId)
                .addValue("scoreVersion", assessments.scorePromptVersion())
                .addValue("extractVersion", extraction.promptVersion());
    }
}
