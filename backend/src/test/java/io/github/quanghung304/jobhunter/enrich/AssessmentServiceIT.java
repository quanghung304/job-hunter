package io.github.quanghung304.jobhunter.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessment;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessmentRepository;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
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
@Import({AssessmentService.class, Prefilter.class, JobQueryService.class, AssessmentServiceIT.ClockConfig.class})
class AssessmentServiceIT {

    static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    static final Instant EARLIER = NOW.minusSeconds(3600);
    static final String PROFILE = "hugh";
    static final String SCORE_PROMPT = "score-v1";
    static final PrefilterDecision PASS = PrefilterDecision.of(List.of());
    static final PrefilterDecision TITLE_EXCLUDED = PrefilterDecision.of(List.of(PrefilterReason.TITLE_EXCLUDED));

    @Autowired
    AssessmentService service;

    @Autowired
    JobAssessmentRepository assessments;

    @Autowired
    TestEntityManager em;

    @Test
    void recordPrefilterCreatesAPendingRowForTheCurrentScorePromptVersion() {
        long jobId = insertJob("fp-1", "Senior Java Developer");

        service.recordPrefilter(jobId, PROFILE, "hash-1", PASS);
        JobAssessment row = reload(jobId);

        assertThat(row.getPrefilterResult()).isEqualTo(PrefilterResult.PASS);
        assertThat(row.getPrefilterReasons()).isEmpty();
        assertThat(row.getStatus()).isEqualTo(AssessmentStatus.PENDING);
        assertThat(row.getProfileHash()).isEqualTo("hash-1");
        assertThat(row.getScorePromptVersion()).isEqualTo(SCORE_PROMPT);
        assertThat(row.getEligibility()).isNull();
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getCreatedAt()).isEqualTo(NOW);
        assertThat(row.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectedJobsAreDoneWithTheirReasons() {
        long jobId = insertJob("fp-2", "iOS Engineer");

        service.recordPrefilter(jobId, PROFILE, "hash-1",
                PrefilterDecision.of(List.of(PrefilterReason.TITLE_EXCLUDED, PrefilterReason.NOT_RELEVANT)));
        JobAssessment row = reload(jobId);

        assertThat(row.getPrefilterResult()).isEqualTo(PrefilterResult.REJECT);
        assertThat(row.getPrefilterReasons()).containsExactly("TITLE_EXCLUDED", "NOT_RELEVANT");
        assertThat(row.getStatus()).isEqualTo(AssessmentStatus.DONE);
    }

    @Test
    void recordingAgainUpdatesTheSameRow() {
        long jobId = insertJob("fp-3", "Backend Developer");
        service.recordPrefilter(jobId, PROFILE, "hash-1", PASS);
        setEligibility(jobId, Eligibility.ELIGIBLE, AssessmentStatus.PENDING);

        service.recordPrefilter(jobId, PROFILE, "hash-1", TITLE_EXCLUDED);
        JobAssessment rejected = reload(jobId);
        service.recordPrefilter(jobId, PROFILE, "hash-1", PASS);
        JobAssessment passedAgain = reload(jobId);

        assertThat(assessments.count()).isEqualTo(1);
        assertThat(rejected.getPrefilterReasons()).containsExactly("TITLE_EXCLUDED");
        assertThat(rejected.getEligibility()).as("null if prefilter rejected").isNull();
        assertThat(rejected.getEligibilityReasons()).isEmpty();
        assertThat(rejected.getStatus()).isEqualTo(AssessmentStatus.DONE);
        assertThat(passedAgain.getPrefilterResult()).isEqualTo(PrefilterResult.PASS);
        assertThat(passedAgain.getPrefilterReasons()).isEmpty();
        assertThat(passedAgain.getStatus()).isEqualTo(AssessmentStatus.PENDING);
    }

    @Test
    void samePassWithSameHashKeepsEligibilityAndStatus() {
        long jobId = insertJob("fp-4", "Backend Developer");
        service.recordPrefilter(jobId, PROFILE, "hash-1", PASS);
        setEligibility(jobId, Eligibility.ELIGIBLE, AssessmentStatus.DONE);

        service.recordPrefilter(jobId, PROFILE, "hash-1", PASS);
        JobAssessment row = reload(jobId);

        assertThat(row.getEligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(row.getStatus()).isEqualTo(AssessmentStatus.DONE);
    }

    @Test
    void reassessPrefilterReEvaluatesRowsWithAnotherProfileHash() {
        Profile profile = ProfileFixtures.example();
        long nowExcluded = insertJob("fp-a", "iOS Engineer");
        long nowPassing = insertJob("fp-b", "Senior Java Developer");
        long stillPassing = insertJob("fp-c", "Backend Engineer");
        long current = insertJob("fp-d", "iOS Developer");
        service.recordPrefilter(nowExcluded, profile.id(), "old", PASS);
        service.recordPrefilter(nowPassing, profile.id(), "old", TITLE_EXCLUDED);
        service.recordPrefilter(stillPassing, profile.id(), "old", PASS);
        service.recordPrefilter(current, profile.id(), "new", PASS);
        setEligibility(nowExcluded, Eligibility.ELIGIBLE, AssessmentStatus.PENDING);
        setEligibility(stillPassing, Eligibility.UNCERTAIN, AssessmentStatus.DONE);
        setScore(stillPassing, 77);
        insertOtherPromptVersionRow(nowExcluded, profile.id());

        int reassessed = service.reassessPrefilter(profile, "new");

        assertThat(reassessed).isEqualTo(3);
        JobAssessment excluded = reload(nowExcluded);
        assertThat(excluded.getPrefilterResult()).isEqualTo(PrefilterResult.REJECT);
        assertThat(excluded.getPrefilterReasons()).containsExactly("TITLE_EXCLUDED", "NOT_RELEVANT");
        assertThat(excluded.getEligibility()).isNull();
        assertThat(excluded.getStatus()).isEqualTo(AssessmentStatus.DONE);
        assertThat(excluded.getProfileHash()).isEqualTo("new");

        JobAssessment passing = reload(nowPassing);
        assertThat(passing.getPrefilterResult()).isEqualTo(PrefilterResult.PASS);
        assertThat(passing.getPrefilterReasons()).isEmpty();
        assertThat(passing.getStatus()).isEqualTo(AssessmentStatus.PENDING);

        JobAssessment kept = reload(stillPassing);
        assertThat(kept.getPrefilterResult()).isEqualTo(PrefilterResult.PASS);
        assertThat(kept.getEligibility()).as("re-evaluated later, no LLM").isNull();
        assertThat(kept.getStatus()).isEqualTo(AssessmentStatus.DONE);
        assertThat(kept.getScore()).as("re-scoring is manual").isEqualTo(77);
        assertThat(kept.getProfileHash()).isEqualTo("new");

        JobAssessment untouched = reload(current);
        assertThat(untouched.getPrefilterResult()).as("same hash: not re-evaluated").isEqualTo(PrefilterResult.PASS);

        JobAssessment otherVersion = assessments
                .findByJobIdAndProfileIdAndScorePromptVersion(nowExcluded, profile.id(), "score-v0").orElseThrow();
        assertThat(otherVersion.getProfileHash()).isEqualTo("old");

        assertThat(service.reassessPrefilter(profile, "new")).as("idempotent").isZero();
    }

    /** A detached snapshot of the stored row. */
    private JobAssessment reload(long jobId) {
        em.flush();
        em.clear();
        JobAssessment row = assessments
                .findByJobIdAndProfileIdAndScorePromptVersion(jobId, PROFILE, SCORE_PROMPT).orElseThrow();
        em.detach(row);
        return row;
    }

    private void setEligibility(long jobId, Eligibility eligibility, AssessmentStatus status) {
        JobAssessment row = reload(jobId);
        row.setEligibility(eligibility);
        row.setEligibilityReasons(List.of("REGION:UNKNOWN"));
        row.setStatus(status);
        assessments.saveAndFlush(row);
    }

    private void setScore(long jobId, int score) {
        JobAssessment row = reload(jobId);
        row.setScore(score);
        assessments.saveAndFlush(row);
    }

    private void insertOtherPromptVersionRow(long jobId, String profileId) {
        var row = new JobAssessment(jobId, profileId, "old", AssessmentStatus.DONE, EARLIER, EARLIER);
        row.setPrefilterResult(PrefilterResult.PASS);
        row.setScorePromptVersion("score-v0");
        assessments.saveAndFlush(row);
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
    }
}
