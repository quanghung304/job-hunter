package io.github.quanghung304.jobhunter.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessment;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessmentRepository;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.llm.LlmProperties;
import io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@PostgresJpaTest
@Import({AssessmentService.class, Prefilter.class, EligibilityEvaluator.class, JobQueryService.class,
        AssessmentServiceIT.ClockConfig.class})
class AssessmentServiceIT {

    static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    static final Instant EARLIER = NOW.minusSeconds(3600);
    static final Profile PROFILE = ProfileFixtures.example(); // id "hugh"
    static final String SCORE_PROMPT = "score-v2";
    static final PrefilterDecision PASS = PrefilterDecision.of(List.of());
    static final PrefilterDecision TITLE_EXCLUDED = PrefilterDecision.of(List.of(PrefilterReason.TITLE_EXCLUDED));
    static final String ATTRIBUTES = """
            {"remoteType":"FULLY_REMOTE","allowedRegions":["EU"],"employmentTypes":["CONTRACT"],
             "requiredSkills":["Java"],"roleSummary":"Backend role.","isAgencyOrReposting":false,
             "isScamSignal":false,"confidence":"HIGH"}""";
    static final String US_ONLY = ATTRIBUTES.replace("[\"EU\"]", "[\"US\"]");

    @Autowired
    AssessmentService service;

    @Autowired
    JobAssessmentRepository assessments;

    @Autowired
    JobQueryService jobs;

    @Autowired
    TestEntityManager em;

    @Test
    void recordPrefilterCreatesAPendingRowForTheCurrentScorePromptVersion() {
        long jobId = insertJob("fp-1", "Senior Java Developer");

        service.recordPrefilter(job(jobId), PROFILE, "hash-1", PASS);
        JobAssessment row = reload(jobId);

        assertThat(row.getPrefilterResult()).isEqualTo(PrefilterResult.PASS);
        assertThat(row.getPrefilterReasons()).isEmpty();
        assertThat(row.getStatus()).isEqualTo(AssessmentStatus.PENDING);
        assertThat(row.getProfileHash()).isEqualTo("hash-1");
        assertThat(row.getScorePromptVersion()).isEqualTo(SCORE_PROMPT);
        assertThat(row.getEligibilityChecks()).as("not analyzed").isNull();
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getCreatedAt()).isEqualTo(NOW);
        assertThat(row.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectedJobsAreDoneWithTheirReasons() {
        long jobId = insertJob("fp-2", "iOS Engineer");

        service.recordPrefilter(job(jobId), PROFILE, "hash-1",
                PrefilterDecision.of(List.of(PrefilterReason.TITLE_EXCLUDED, PrefilterReason.NOT_RELEVANT)));
        JobAssessment row = reload(jobId);

        assertThat(row.getPrefilterResult()).isEqualTo(PrefilterResult.REJECT);
        assertThat(row.getPrefilterReasons()).containsExactly("TITLE_EXCLUDED", "NOT_RELEVANT");
        assertThat(row.getStatus()).isEqualTo(AssessmentStatus.DONE);
    }

    @Test
    void recordingAgainUpdatesTheSameRow() {
        long jobId = insertJob("fp-3", "Backend Developer");
        insertExtraction(jobId, ATTRIBUTES);
        service.recordPrefilter(job(jobId), PROFILE, "hash-1", PASS);
        assertThat(reload(jobId).getEligibilityChecks()).as("checks from the stored extraction").isNotNull();

        service.recordPrefilter(job(jobId), PROFILE, "hash-1", TITLE_EXCLUDED);
        JobAssessment rejected = reload(jobId);
        service.recordPrefilter(job(jobId), PROFILE, "hash-1", PASS);
        JobAssessment passedAgain = reload(jobId);

        assertThat(assessments.count()).isEqualTo(1);
        assertThat(rejected.getPrefilterReasons()).containsExactly("TITLE_EXCLUDED");
        assertThat(rejected.getEligibilityChecks()).as("null if prefilter rejected").isNull();
        assertThat(rejected.getStatus()).isEqualTo(AssessmentStatus.DONE);
        assertThat(passedAgain.getPrefilterResult()).isEqualTo(PrefilterResult.PASS);
        assertThat(passedAgain.getPrefilterReasons()).isEmpty();
        assertThat(passedAgain.getStatus()).isEqualTo(AssessmentStatus.PENDING);
        assertThat(passedAgain.getEligibilityChecks()).as("evaluated again, no LLM").isNotNull();
    }

    @Test
    void passAttachesChecksFromAnOkExtractionWithoutAVerdict() {
        long jobId = insertJob("fp-x", "Backend Developer");
        long extractionId = insertExtraction(jobId, US_ONLY);

        service.recordPrefilter(job(jobId), PROFILE, "hash-1", PASS);
        AssessmentView view = service.find(jobId, PROFILE.id()).orElseThrow();

        assertThat(view.analyzed()).isTrue();
        assertThat(view.extractionId()).isEqualTo(extractionId);
        assertThat(view.eligibilityChecks()).extracting(EligibilityCheck::code)
                .containsExactly(EligibilityRule.values());
        assertThat(view.eligibilityChecks()).filteredOn(c -> c.result() != CheckResult.OK)
                .extracting(EligibilityCheck::code, EligibilityCheck::result, EligibilityCheck::value)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(EligibilityRule.REGION, CheckResult.FAIL, "US"));
        assertThat(view.status()).as("a FAIL check decides nothing").isEqualTo(AssessmentStatus.PENDING);
    }

    @Test
    void samePassWithSameHashKeepsChecksAndStatus() {
        long jobId = insertJob("fp-4", "Backend Developer");
        insertExtraction(jobId, ATTRIBUTES);
        service.recordPrefilter(job(jobId), PROFILE, "hash-1", PASS);
        setScore(jobId, 81);
        var before = reload(jobId).getEligibilityChecks();

        service.recordPrefilter(job(jobId), PROFILE, "hash-1", PASS);
        JobAssessment row = reload(jobId);

        assertThat(row.getEligibilityChecks()).isEqualTo(before);
        assertThat(row.getStatus()).isEqualTo(AssessmentStatus.DONE);
    }

    @Test
    void recordChecksStoresTheListAndWaitsForScore() {
        long jobId = insertJob("fp-5", "Backend Developer");
        long extractionId = insertExtraction(jobId, ATTRIBUTES);
        service.recordPrefilter(job(jobId), PROFILE, "hash-1", PASS);
        List<EligibilityCheck> checks = List.of(new EligibilityCheck(EligibilityRule.REGION, true, CheckResult.UNKNOWN,
                null, "Not stated"));

        service.recordChecks(jobId, PROFILE.id(), "hash-2", extractionId, checks);

        AssessmentView view = service.find(jobId, PROFILE.id()).orElseThrow();
        assertThat(view.eligibilityChecks()).containsExactlyElementsOf(checks);
        assertThat(view.profileHash()).isEqualTo("hash-2");
        assertThat(view.status()).isEqualTo(AssessmentStatus.PENDING);
        assertThat(service.countNotAnalyzed(List.of(jobId), PROFILE.id())).isZero();
    }

    @Test
    void reassessPrefilterReEvaluatesPrefilterAndChecksAndKeepsScores() {
        long nowExcluded = insertJob("fp-a", "iOS Engineer");
        long nowPassing = insertJob("fp-b", "Senior Java Developer");
        long stillPassing = insertJob("fp-c", "Backend Engineer");
        long current = insertJob("fp-d", "iOS Developer");
        insertExtraction(stillPassing, ATTRIBUTES);
        service.recordPrefilter(job(nowExcluded), PROFILE, "old", PASS);
        service.recordPrefilter(job(nowPassing), PROFILE, "old", TITLE_EXCLUDED);
        service.recordPrefilter(job(stillPassing), PROFILE, "old", PASS);
        service.recordPrefilter(job(current), PROFILE, "new", PASS);
        setChecksJson(stillPassing, "[]");
        setScore(stillPassing, 77);
        insertOtherPromptVersionRow(nowExcluded, PROFILE.id());

        Reassessment reassessed = service.reassessPrefilter(PROFILE, "new");

        assertThat(reassessed).as("rows, prefilter changed (excluded, passing), checks changed (kept)")
                .isEqualTo(new Reassessment(3, 2, 1));
        JobAssessment excluded = reload(nowExcluded);
        assertThat(excluded.getPrefilterResult()).isEqualTo(PrefilterResult.REJECT);
        assertThat(excluded.getPrefilterReasons()).containsExactly("TITLE_EXCLUDED", "NOT_RELEVANT");
        assertThat(excluded.getEligibilityChecks()).isNull();
        assertThat(excluded.getStatus()).isEqualTo(AssessmentStatus.DONE);
        assertThat(excluded.getProfileHash()).isEqualTo("new");

        JobAssessment passing = reload(nowPassing);
        assertThat(passing.getPrefilterResult()).isEqualTo(PrefilterResult.PASS);
        assertThat(passing.getPrefilterReasons()).isEmpty();
        assertThat(passing.getStatus()).isEqualTo(AssessmentStatus.PENDING);
        assertThat(passing.getEligibilityChecks()).as("no extraction: not analyzed").isNull();

        JobAssessment kept = reload(stillPassing);
        assertThat(kept.getPrefilterResult()).isEqualTo(PrefilterResult.PASS);
        assertThat(kept.getEligibilityChecks()).as("re-evaluated from the extraction, no LLM").hasSize(10);
        assertThat(kept.getStatus()).isEqualTo(AssessmentStatus.DONE);
        assertThat(kept.getScore()).as("re-scoring is manual").isEqualTo(77);
        assertThat(kept.getProfileHash()).isEqualTo("new");

        JobAssessment untouched = reload(current);
        assertThat(untouched.getPrefilterResult()).as("same hash: not re-evaluated").isEqualTo(PrefilterResult.PASS);

        JobAssessment otherVersion = assessments
                .findByJobIdAndProfileIdAndScorePromptVersion(nowExcluded, PROFILE.id(), "score-v0").orElseThrow();
        assertThat(otherVersion.getProfileHash()).isEqualTo("old");

        assertThat(service.reassessPrefilter(PROFILE, "new")).as("idempotent").isEqualTo(new Reassessment(0, 0, 0));
    }

    @Test
    void evaluateMissingChecksFillsAnalyzedJobsOnly() {
        long analyzed = insertJob("fp-m1", "Backend Developer");
        long notAnalyzed = insertJob("fp-m2", "Backend Developer");
        service.recordPrefilter(job(analyzed), PROFILE, "hash-1", PASS);
        service.recordPrefilter(job(notAnalyzed), PROFILE, "hash-1", PASS);
        insertExtraction(analyzed, ATTRIBUTES); // stored after the prefilter, e.g. a row from before V8

        int updated = service.evaluateMissingChecks(PROFILE, "hash-1");

        assertThat(updated).isEqualTo(1);
        assertThat(reload(analyzed).getEligibilityChecks()).hasSize(10);
        assertThat(reload(notAnalyzed).getEligibilityChecks()).isNull();
        assertThat(service.evaluateMissingChecks(PROFILE, "hash-1")).as("idempotent").isZero();
    }

    private JobView job(long jobId) {
        return jobs.find(jobId).orElseThrow();
    }

    /** A detached snapshot of the stored row. */
    private JobAssessment reload(long jobId) {
        em.flush();
        em.clear();
        JobAssessment row = assessments
                .findByJobIdAndProfileIdAndScorePromptVersion(jobId, PROFILE.id(), SCORE_PROMPT).orElseThrow();
        em.detach(row);
        return row;
    }

    private void setChecksJson(long jobId, String json) {
        JobAssessment row = reload(jobId);
        try {
            row.setEligibilityChecks(new ObjectMapper().readTree(json));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assessments.saveAndFlush(row);
    }

    private void setScore(long jobId, int score) {
        JobAssessment row = reload(jobId);
        row.setScore(score);
        row.setStatus(AssessmentStatus.DONE);
        assessments.saveAndFlush(row);
    }

    private void insertOtherPromptVersionRow(long jobId, String profileId) {
        var row = new JobAssessment(jobId, profileId, "old", AssessmentStatus.DONE, EARLIER, EARLIER);
        row.setPrefilterResult(PrefilterResult.PASS);
        row.setScorePromptVersion("score-v0");
        assessments.saveAndFlush(row);
    }

    /** An OK extraction for the current extract prompt version (extract-v1). */
    private long insertExtraction(long jobId, String attributes) {
        Object id = em.getEntityManager().createNativeQuery("""
                        insert into job_extraction (job_id, prompt_version, model, attributes, status, attempts,
                                                    created_at)
                        values (:job, 'extract-v1', 'primary', cast(:attributes as jsonb), 'OK', 1, :t)
                        returning id""")
                .setParameter("job", jobId)
                .setParameter("attributes", attributes)
                .setParameter("t", EARLIER)
                .getSingleResult();
        return ((Number) id).longValue();
    }

    /** A remote job posted yesterday; inserted with SQL to respect module boundaries. */
    private long insertJob(String fingerprint, String title) {
        Object id = em.getEntityManager().createNativeQuery("""
                        insert into job (fingerprint, title, company_name, company_key, remote_hint, description_text,
                                         posted_at, first_seen_at, last_seen_at)
                        values (:fp, :title, 'Acme', 'acme', 'REMOTE', 'Build things.', :posted, :t, :t)
                        returning id""")
                .setParameter("fp", fingerprint)
                .setParameter("title", title)
                .setParameter("posted", NOW.minusSeconds(86_400))
                .setParameter("t", EARLIER)
                .getSingleResult();
        return ((Number) id).longValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        /** Score prompt version score-v2 and extract-v1, as in application.yml. */
        @Bean
        LlmProperties llmProperties() {
            return LlmRouterTestConfig.properties(null, false);
        }
    }
}
