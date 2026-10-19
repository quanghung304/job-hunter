package io.github.quanghung304.jobhunter.tracking;

import io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Rows for board tests, inserted with SQL (other modules' tables). Prompt versions match
 * {@link LlmRouterTestConfig#properties}: {@code extract-v1} and {@code score-v1}.
 */
final class BoardFixtures {

    static final Instant NOW = LlmRouterTestConfig.NOW;
    static final String HUGH = "hugh";
    static final String OTHER = "other";

    static final String ATTRIBUTES = """
            {"remoteType":"REMOTE_RESTRICTED","allowedRegions":["EU","IT"],"employmentTypes":["CONTRACT","FULL_TIME"],
             "requiredSkills":["Java"],"roleSummary":"Backend role.","isAgencyOrReposting":false,
             "isScamSignal":false,"confidence":"HIGH"}""";

    private final NamedParameterJdbcTemplate jdbc;
    private int counter;

    BoardFixtures(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** An ACTIVE job posted {@code daysAgo} days before {@link #NOW}. */
    long job(String title, String company, int daysAgo) {
        return job(title, company, daysAgo, "ACTIVE");
    }

    long job(String title, String company, int daysAgo, String status) {
        return jdbc.queryForObject("""
                        insert into job (fingerprint, title, company_name, company_key, location_text, remote_hint,
                                         description_text, apply_url, salary_min, salary_currency, salary_period, tags,
                                         posted_at, first_seen_at, last_seen_at, status)
                        values (:fp, :title, :company, lower(:company), 'Remote (EU)', 'REMOTE', 'Build things.',
                                'https://apply.example/1', 60000, 'EUR', 'YEAR', '{java,spring}', :posted, :posted,
                                :now, :status)
                        returning id""",
                new MapSqlParameterSource()
                        .addValue("fp", "fp-" + (++counter) + "-" + title)
                        .addValue("title", title)
                        .addValue("company", company)
                        .addValue("posted", ts(NOW.minus(Duration.ofDays(daysAgo))))
                        .addValue("now", ts(NOW))
                        .addValue("status", status),
                Long.class);
    }

    void posting(long jobId, String sourceCode) {
        jdbc.update("""
                        insert into raw_posting (source_code, external_id, url, payload, payload_hash, first_seen_at,
                                                 last_seen_at, job_id)
                        values (:source, :ext, :url, cast('{}' as jsonb), 'h', :now, :now, :job)""",
                new MapSqlParameterSource()
                        .addValue("source", sourceCode)
                        .addValue("ext", "ext-" + (++counter))
                        .addValue("url", "https://" + sourceCode + ".example/jobs/" + jobId)
                        .addValue("now", ts(NOW))
                        .addValue("job", jobId));
    }

    /** An OK extraction with {@link #ATTRIBUTES}. */
    long extraction(long jobId) {
        return extraction(jobId, "OK", ATTRIBUTES);
    }

    long extraction(long jobId, String status, String attributes) {
        return jdbc.queryForObject("""
                        insert into job_extraction (job_id, prompt_version, model, attributes, status, attempts,
                                                    created_at)
                        values (:job, 'extract-v1', 'primary', cast(:attributes as jsonb), :status, 1, :now)
                        returning id""",
                new MapSqlParameterSource()
                        .addValue("job", jobId)
                        .addValue("attributes", attributes, Types.VARCHAR)
                        .addValue("status", status)
                        .addValue("now", ts(NOW)),
                Long.class);
    }

    /** Prefilter PASS, not analyzed. */
    void passed(long jobId, String profileId) {
        assessment(jobId, profileId, "PASS", null, null, null, "PENDING");
    }

    void rejected(long jobId, String profileId) {
        assessment(jobId, profileId, "REJECT", null, null, null, "DONE");
    }

    /** Analyzed (OK extraction) with this eligibility, not scored. */
    void analyzed(long jobId, String profileId, String eligibility) {
        assessment(jobId, profileId, "PASS", eligibility, extractionOf(jobId), null,
                "INELIGIBLE".equals(eligibility) ? "DONE" : "PENDING");
    }

    void scored(long jobId, String profileId, int score) {
        assessment(jobId, profileId, "PASS", "ELIGIBLE", extractionOf(jobId), score, "DONE");
    }

    void scoreFailed(long jobId, String profileId) {
        assessment(jobId, profileId, "PASS", "ELIGIBLE", extractionOf(jobId), null, "FAILED");
    }

    void tracking(long jobId, String profileId, String status) {
        jdbc.update("""
                        insert into job_tracking (job_id, profile_id, status, notes, updated_at)
                        values (:job, :profile, :status, null, :now)""",
                new MapSqlParameterSource()
                        .addValue("job", jobId)
                        .addValue("profile", profileId)
                        .addValue("status", status)
                        .addValue("now", ts(NOW)));
    }

    private long extractionOf(long jobId) {
        List<Long> ids = jdbc.queryForList("select id from job_extraction where job_id = :job and status = 'OK'",
                new MapSqlParameterSource("job", jobId), Long.class);
        return ids.isEmpty() ? extraction(jobId) : ids.get(0);
    }

    private void assessment(long jobId, String profileId, String prefilter, String eligibility, Long extractionId,
            Integer score, String status) {
        jdbc.update("""
                        insert into job_assessment (job_id, profile_id, profile_hash, prefilter_result,
                                                    prefilter_reasons, eligibility, eligibility_reasons, extraction_id,
                                                    score, score_breakdown, summary, score_prompt_version, score_model,
                                                    status, attempts, created_at, updated_at)
                        values (:job, :profile, 'hash', :prefilter, cast(:reasons as text[]), :eligibility,
                                cast(:eligibilityReasons as text[]), :extraction, :score, cast(:details as jsonb),
                                :summary, 'score-v1', :model, :status, :attempts, :now, :now)""",
                new MapSqlParameterSource()
                        .addValue("job", jobId)
                        .addValue("profile", profileId)
                        .addValue("prefilter", prefilter)
                        .addValue("reasons", "REJECT".equals(prefilter) ? "{TITLE_EXCLUDED}" : "{}")
                        .addValue("eligibility", eligibility, Types.VARCHAR)
                        .addValue("eligibilityReasons", "UNCERTAIN".equals(eligibility) ? "{REGION:UNKNOWN}" : "{}")
                        .addValue("extraction", extractionId, Types.BIGINT)
                        .addValue("score", score, Types.INTEGER)
                        .addValue("details", score == null ? null : details(score), Types.VARCHAR)
                        .addValue("summary", score == null ? null : "Good match " + score, Types.VARCHAR)
                        .addValue("model", score == null ? null : "primary", Types.VARCHAR)
                        .addValue("status", status)
                        .addValue("attempts", score != null || "FAILED".equals(status) ? 1 : 0)
                        .addValue("now", ts(NOW)));
    }

    /** A §5 score object whose breakdown sums to {@code score}. */
    static String details(int score) {
        int skills = Math.min(40, score);
        int experience = Math.min(20, score - skills);
        int arrangement = Math.min(20, score - skills - experience);
        int domain = Math.min(10, score - skills - experience - arrangement);
        int other = score - skills - experience - arrangement - domain;
        return """
                {"breakdown":{"skills":%d,"experience":%d,"arrangement":%d,"domain":%d,"other":%d},"score":%d,
                 "strengths":["Java"],"gaps":["Kafka"],"redFlags":[],"summary":"Good match %d",
                 "applyAdvice":"Mention Spring."}""".formatted(skills, experience, arrangement, domain, other, score,
                score);
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
