package io.github.quanghung304.jobhunter.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.enrich.AnalysisService;
import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.AssessmentStatus;
import io.github.quanghung304.jobhunter.enrich.EnrichTestConfig;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig;
import io.github.quanghung304.jobhunter.llm.PaidCallsDisabledException;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignment;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentRepository;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.tracking.TrackingService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** ADR-0009 for scoring: a PAID SCORE model without consent is never called. */
@PostgresJpaTest
@Import({EnrichTestConfig.class, ScoringService.class, TrackingService.class})
@TestPropertySource(properties = "test.llm.allow-paid=false")
class ScoringPaidConsentIT {

    static final Profile PROFILE = ProfileFixtures.example();

    @Autowired
    ScoringService scoring;

    @Autowired
    AnalysisService analysis;

    @Autowired
    AssessmentService assessments;

    @Autowired
    LlmAssignmentRepository assignments;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    MutableClock clock;

    @Autowired
    TestEntityManager em;

    long jobId;

    @BeforeEach
    void setUp() {
        clock.set(LlmRouterTestConfig.NOW);
        jobId = EnrichTestConfig.insertJob(em.getEntityManager(), "fp-paid", "Senior Java Developer",
                "Contract role, remote in Europe. Java.");
        analysis.analyze(jobId);
        fake.reset();
        assignments.saveAndFlush(new LlmAssignment(LlmPurpose.SCORE, "paid", List.of(), false, true,
                LlmRouterTestConfig.NOW, LlmAssignment.BY_USER));
    }

    @Test
    void scoreWithAPaidModelIsPausedAndTheJobStaysPending() {
        ScoringResult result = scoring.score(jobId, PROFILE);

        assertThat(result.outcome()).isEqualTo(ScoringResult.Outcome.LLM_PAUSED);
        assertThat(result.message()).contains("allow-paid-calls=false");
        assertThat(fake.requests()).isEmpty();
        assertThat(assessments.find(jobId, PROFILE.id()).orElseThrow().status()).isEqualTo(AssessmentStatus.PENDING);
    }

    @Test
    void bulkScoreWithAPaidModelIsRefusedButTheDryRunSaysWhy() {
        BulkScoreReport dryRun = scoring.scoreSelected(List.of(jobId), PROFILE, true);

        assertThat(dryRun.model().paid()).isTrue();
        assertThat(dryRun.model().blockedByConsent()).isTrue();
        assertThatThrownBy(() -> scoring.scoreSelected(List.of(jobId), PROFILE, false))
                .isInstanceOf(PaidCallsDisabledException.class)
                .hasMessageContaining("LLM_ALLOW_PAID_CALLS");
        assertThat(fake.requests()).isEmpty();
    }

    @Test
    void rescoreWithAPaidModelIsRefused() {
        assignments.saveAndFlush(new LlmAssignment(LlmPurpose.SCORE, "primary", List.of(), false, false,
                LlmRouterTestConfig.NOW, LlmAssignment.BY_USER));
        scoring.score(jobId, PROFILE);
        assignments.saveAndFlush(new LlmAssignment(LlmPurpose.SCORE, "paid", List.of(), false, true,
                LlmRouterTestConfig.NOW, LlmAssignment.BY_USER));
        fake.reset();

        RescorePlan plan = scoring.rescoreActive(PROFILE, true);

        assertThat(plan.jobCount()).isEqualTo(1);
        assertThat(plan.model().modelId()).isEqualTo("paid");
        assertThatThrownBy(() -> scoring.rescoreActive(PROFILE, false)).isInstanceOf(PaidCallsDisabledException.class);
        assertThat(fake.requests()).isEmpty();
    }
}
