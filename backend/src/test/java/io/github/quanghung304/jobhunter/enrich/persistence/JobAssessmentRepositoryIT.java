package io.github.quanghung304.jobhunter.enrich.persistence;

import static io.github.quanghung304.jobhunter.PersistenceFixtures.T0;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.assertRejected;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.insertJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.enrich.AssessmentStatus;
import io.github.quanghung304.jobhunter.enrich.Eligibility;
import io.github.quanghung304.jobhunter.enrich.ExtractionStatus;
import io.github.quanghung304.jobhunter.enrich.PrefilterResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresJpaTest
class JobAssessmentRepositoryIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    JobAssessmentRepository assessments;

    @Autowired
    JobExtractionRepository extractions;

    @Autowired
    TestEntityManager em;

    @Test
    void roundTripOfAScoredAssessment() throws Exception {
        long jobId = insertJob(em.getEntityManager(), "fp-scored");
        long extractionId = extractions.saveAndFlush(
                new JobExtraction(jobId, "extract-v1", ExtractionStatus.OK, T0)).getId();
        JsonNode breakdown = JSON.readTree("""
                {"skills": 34, "seniority": 12, "arrangement": 15, "domain": 8, "other": 6}""");
        var assessment = new JobAssessment(jobId, "hugh", "sha-1", AssessmentStatus.DONE, T0, T0.plusSeconds(5));
        assessment.setPrefilterResult(PrefilterResult.PASS);
        assessment.setPrefilterReasons(List.of());
        assessment.setEligibility(Eligibility.UNCERTAIN);
        assessment.setEligibilityReasons(List.of("REGION:UNKNOWN", "FULL_TIME_ONLY_SOFT"));
        assessment.setExtractionId(extractionId);
        assessment.setScore(75);
        assessment.setScoreBreakdown(breakdown);
        assessment.setSummary("Hợp với kinh nghiệm Java/Spring của bạn.");
        assessment.setScorePromptVersion("score-v1");
        assessment.setScoreModel("claude-haiku-5-5");
        assessment.setAttempts(1);
        long id = assessments.saveAndFlush(assessment).getId();
        em.clear();

        var loaded = assessments.findById(id).orElseThrow();

        assertThat(loaded.getJobId()).isEqualTo(jobId);
        assertThat(loaded.getProfileId()).isEqualTo("hugh");
        assertThat(loaded.getProfileHash()).isEqualTo("sha-1");
        assertThat(loaded.getPrefilterResult()).isEqualTo(PrefilterResult.PASS);
        assertThat(loaded.getPrefilterReasons()).isEmpty();
        assertThat(loaded.getEligibility()).isEqualTo(Eligibility.UNCERTAIN);
        assertThat(loaded.getEligibilityReasons()).containsExactly("REGION:UNKNOWN", "FULL_TIME_ONLY_SOFT");
        assertThat(loaded.getExtractionId()).isEqualTo(extractionId);
        assertThat(loaded.getScore()).isEqualTo(75);
        assertThat(loaded.getScoreBreakdown()).isEqualTo(breakdown);
        assertThat(loaded.getSummary()).isEqualTo("Hợp với kinh nghiệm Java/Spring của bạn.");
        assertThat(loaded.getScorePromptVersion()).isEqualTo("score-v1");
        assertThat(loaded.getScoreModel()).isEqualTo("claude-haiku-5-5");
        assertThat(loaded.getStatus()).isEqualTo(AssessmentStatus.DONE);
        assertThat(loaded.getAttempts()).isEqualTo(1);
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
        assertThat(loaded.getUpdatedAt()).isEqualTo(T0.plusSeconds(5));
        assertThat(assessments.findByJobIdAndProfileIdAndScorePromptVersion(jobId, "hugh", "score-v1")).isPresent();
    }

    @Test
    void roundTripOfAPrefilterRejection() {
        long jobId = insertJob(em.getEntityManager(), "fp-rejected");
        var rejected = new JobAssessment(jobId, "hugh", "sha-1", AssessmentStatus.DONE, T0, T0);
        rejected.setPrefilterResult(PrefilterResult.REJECT);
        rejected.setPrefilterReasons(List.of("TITLE_EXCLUDED", "TOO_OLD"));
        long id = assessments.saveAndFlush(rejected).getId();
        em.clear();

        var loaded = assessments.findById(id).orElseThrow();

        assertThat(loaded.getPrefilterReasons()).containsExactly("TITLE_EXCLUDED", "TOO_OLD");
        assertThat(loaded.getEligibility()).isNull();
        assertThat(loaded.getScore()).isNull();
        assertThat(loaded.getScoreBreakdown()).isNull();
        // Spring Data turns a null argument into "is null": unscored rows are found this way.
        assertThat(assessments.findByJobIdAndProfileIdAndScorePromptVersion(jobId, "hugh", null)).isPresent();
    }

    @Test
    void jobProfileAndPromptVersionAreUnique() {
        long jobId = insertJob(em.getEntityManager(), "fp-dup");
        assessments.saveAndFlush(scored(jobId, "hugh", "score-v1"));
        assessments.saveAndFlush(scored(jobId, "hugh", "score-v2"));
        assessments.saveAndFlush(scored(jobId, "other-profile", "score-v1"));

        assertRejected("unique", () -> assessments.saveAndFlush(scored(jobId, "hugh", "score-v1")));
    }

    @Test
    void unscoredRowsAreUniqueToo() {
        long jobId = insertJob(em.getEntityManager(), "fp-null");
        assessments.saveAndFlush(scored(jobId, "hugh", null));

        assertRejected("unique", () -> assessments.saveAndFlush(scored(jobId, "hugh", null)));
    }

    @Test
    void scoreMustBeBetween0And100() {
        long jobId = insertJob(em.getEntityManager(), "fp-range");
        var assessment = scored(jobId, "hugh", "score-v1");
        assessment.setScore(101);

        assertRejected("check", () -> assessments.saveAndFlush(assessment));
    }

    private static JobAssessment scored(long jobId, String profileId, String promptVersion) {
        var assessment = new JobAssessment(jobId, profileId, "sha", AssessmentStatus.PENDING, T0, T0);
        assessment.setScorePromptVersion(promptVersion);
        return assessment;
    }
}
