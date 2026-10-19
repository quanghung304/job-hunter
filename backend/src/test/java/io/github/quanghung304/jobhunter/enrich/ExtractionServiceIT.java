package io.github.quanghung304.jobhunter.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessment;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessmentRepository;
import io.github.quanghung304.jobhunter.enrich.persistence.JobExtraction;
import io.github.quanghung304.jobhunter.enrich.persistence.JobExtractionRepository;
import io.github.quanghung304.jobhunter.ingest.JobDescriptionChanged;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.llm.BudgetGuard;
import io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig;
import io.github.quanghung304.jobhunter.llm.LlmUnavailable;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient.Step;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;

@PostgresJpaTest
@Import(EnrichTestConfig.class)
class ExtractionServiceIT {

    static final String DESCRIPTION = "Contract role, fully remote in Europe. Java, Spring Boot, SQL. English B2.";

    @Autowired
    ExtractionService extraction;

    @Autowired
    JobExtractionRepository extractions;

    @Autowired
    JobAssessmentRepository assessments;

    @Autowired
    JobQueryService jobs;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    BudgetGuard budget;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TestEntityManager em;

    long jobId;

    @BeforeEach
    void setUp() {
        fake.reset();
        EnrichTestConfig.resetClock(clock);
        budget.beginRun();
        jobId = EnrichTestConfig.insertJob(em.getEntityManager(), "fp-x", "Senior Java Developer", DESCRIPTION);
    }

    @Test
    void successIsStoredAndTheSecondRequestMakesNoCall() {
        ExtractionResult first = extraction.extract(jobId);
        ExtractionResult second = extraction.extract(jobId);

        assertThat(first.status()).isEqualTo(ExtractionResult.Status.OK);
        assertThat(first.llmCalled()).isTrue();
        assertThat(first.attributes().remoteType()).isEqualTo(JobAttributes.RemoteType.FULLY_REMOTE);
        assertThat(second.status()).isEqualTo(ExtractionResult.Status.OK);
        assertThat(second.llmCalled()).isFalse();
        assertThat(second.attributes()).isEqualTo(first.attributes());
        assertThat(fake.requests()).hasSize(1);
        JobExtraction row = extractions.findByJobIdAndPromptVersion(jobId, "extract-v1").orElseThrow();
        assertThat(row.getStatus()).isEqualTo(ExtractionStatus.OK);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getModel()).isEqualTo("primary");
        assertThat(row.getAttributes().path("allowedRegions").get(0).asText()).isEqualTo("EUROPE");
        assertThat(first.extractionId()).isEqualTo(row.getId());
    }

    @Test
    void requestCarriesTheJobAndTheExtractPrompt() {
        extraction.extract(jobId);

        var request = fake.requests().get(0);
        assertThat(request.promptId()).isEqualTo("extract-v1");
        assertThat(request.jobId()).isEqualTo(jobId);
        assertThat(request.system()).contains("You extract structured facts");
        assertThat(request.messages().get(0).content())
                .contains("<title>Senior Java Developer</title>", "<source_remote_hint>REMOTE</source_remote_hint>",
                        DESCRIPTION);
    }

    @Test
    void invalidThenValidUsesTheCorrectiveRetry() {
        fake.enqueue(Step.text("{\"remoteType\": \"SOMETIMES\"}"), Step.canned());

        ExtractionResult result = extraction.extract(jobId);

        assertThat(result.status()).isEqualTo(ExtractionResult.Status.OK);
        assertThat(fake.requests()).hasSize(2);
        assertThat(extractions.findByJobIdAndPromptVersion(jobId, "extract-v1").orElseThrow().getAttempts()).isEqualTo(1);
    }

    @Test
    void threeFailedAttemptsArePermanent() {
        for (int i = 0; i < 3; i++) {
            fake.enqueue(Step.text("no json"), Step.text("still no json"));
        }

        ExtractionResult first = extraction.extract(jobId);
        ExtractionResult second = extraction.extract(jobId);
        ExtractionResult third = extraction.extract(jobId);
        ExtractionResult fourth = extraction.extract(jobId);

        assertThat(first.status()).isEqualTo(ExtractionResult.Status.FAILED);
        assertThat(second.status()).isEqualTo(ExtractionResult.Status.FAILED);
        assertThat(third.status()).isEqualTo(ExtractionResult.Status.FAILED_PERMANENTLY);
        assertThat(fourth.status()).isEqualTo(ExtractionResult.Status.FAILED_PERMANENTLY);
        assertThat(fourth.llmCalled()).isFalse();
        assertThat(fake.requests()).as("2 HTTP calls per attempt, none after the third").hasSize(6);
        JobExtraction row = extractions.findByJobIdAndPromptVersion(jobId, "extract-v1").orElseThrow();
        assertThat(row.getStatus()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(3);
        assertThat(row.getAttributes()).isNull();
    }

    @Test
    void budgetStopUsesNoAttempt() {
        for (int i = 0; i < LlmRouterTestConfig.MAX_CALLS_PER_RUN; i++) {
            budget.recordCall();
        }

        ExtractionResult result = extraction.extract(jobId);

        assertThat(result.status()).isEqualTo(ExtractionResult.Status.UNAVAILABLE);
        assertThat(result.unavailable()).isEqualTo(LlmUnavailable.Reason.RUN_CALL_LIMIT);
        assertThat(fake.requests()).isEmpty();
        assertThat(extractions.findByJobIdAndPromptVersion(jobId, "extract-v1")).isEmpty();
    }

    @Test
    void quotaPauseUsesNoAttempt() {
        fake.enqueue(Step.dailyQuota(null));

        ExtractionResult result = extraction.extract(jobId);

        assertThat(result.status()).isEqualTo(ExtractionResult.Status.UNAVAILABLE);
        assertThat(result.unavailable()).isEqualTo(LlmUnavailable.Reason.NO_USABLE_MODEL);
        assertThat(extractions.findByJobIdAndPromptVersion(jobId, "extract-v1")).isEmpty();
    }

    @Test
    void extractAttributesWritesNoExtractionRow() {
        ExtractionResult result = extraction.extractAttributes(jobs.find(jobId).orElseThrow());

        assertThat(result.status()).isEqualTo(ExtractionResult.Status.OK);
        assertThat(result.extractionId()).isNull();
        assertThat(extractions.count()).isZero();
    }

    @Test
    void descriptionChangeInvalidatesExtractionEligibilityAndScore() {
        ExtractionResult extracted = extraction.extract(jobId);
        JobAssessment assessment = new JobAssessment(jobId, "hugh", "hash", AssessmentStatus.DONE,
                LlmRouterTestConfig.NOW, LlmRouterTestConfig.NOW);
        assessment.setScorePromptVersion("score-v2");
        assessment.setPrefilterResult(PrefilterResult.PASS);
        assessment.setExtractionId(extracted.extractionId());
        assessment.setEligibilityChecks(new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode()
                .add(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("code", "REGION")));
        assessment.setScore(80);
        assessment.setSummary("old");
        assessments.saveAndFlush(assessment);

        events.publishEvent(new JobDescriptionChanged(jobId));
        em.flush();
        em.clear();

        assertThat(extractions.findByJobId(jobId)).isEmpty();
        JobAssessment reset = assessments.findByJobId(jobId).get(0);
        assertThat(reset.getExtractionId()).isNull();
        assertThat(reset.getEligibilityChecks()).isNull();
        assertThat(reset.getScore()).isNull();
        assertThat(reset.getSummary()).isNull();
        assertThat(reset.getStatus()).isEqualTo(AssessmentStatus.PENDING);

        assertThat(extraction.extract(jobId).llmCalled()).as("next Analyze extracts again").isTrue();
    }

    @Test
    void longDescriptionsKeepTheBeginningAndTheEnd() {
        String description = "A".repeat(10_000) + "M".repeat(3_000) + "Z".repeat(3_000);

        String truncated = ExtractionService.truncate(description);

        assertThat(truncated).hasSize(9_000 + ExtractionService.TRUNCATION_MARK.length() + 3_000)
                .startsWith("A".repeat(9_000) + "\n[…]\n").endsWith("Z".repeat(3_000));
        assertThat(ExtractionService.truncate("short")).isEqualTo("short");
        assertThat(ExtractionService.truncate("x".repeat(12_000))).hasSize(12_000);
    }
}
