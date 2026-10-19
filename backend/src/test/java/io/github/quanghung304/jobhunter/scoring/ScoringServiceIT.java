package io.github.quanghung304.jobhunter.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.enrich.AnalysisService;
import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.AssessmentStatus;
import io.github.quanghung304.jobhunter.enrich.AssessmentView;
import io.github.quanghung304.jobhunter.enrich.EnrichTestConfig;
import io.github.quanghung304.jobhunter.enrich.TooManyJobsException;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient.Step;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignment;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentRepository;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCall;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.tracking.EligibilityDecision;
import io.github.quanghung304.jobhunter.tracking.TrackingService;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** The owner's Score action (ADR-0010) against Postgres and the fake LLM (SCORE model {@code primary}). */
@PostgresJpaTest
@Import({EnrichTestConfig.class, ScoringService.class, TrackingService.class})
class ScoringServiceIT {

    static final Profile PROFILE = ProfileFixtures.example();
    static final String DESCRIPTION = "Contract role, fully remote in Europe. Java, Spring Boot, SQL.";
    static final String INELIGIBLE_EXTRACTION = """
            {"remoteType":"REMOTE_RESTRICTED","allowedRegions":["US"],"employmentTypes":["CONTRACT"],
             "requiredSkills":["Java"],"roleSummary":"US-only role.","isAgencyOrReposting":false,
             "isScamSignal":false,"confidence":"HIGH"}""";
    static final String WRONG_SUM = """
            {"breakdown":{"skills":30,"experience":15,"arrangement":15,"domain":5,"other":5},"score":99,
             "strengths":["Java"],"gaps":[],"redFlags":[],"summary":"Khá phù hợp.","applyAdvice":"Spring Boot."}""";

    @Autowired
    ScoringService scoring;

    @Autowired
    AnalysisService analysis;

    @Autowired
    AssessmentService assessments;

    @Autowired
    TrackingService tracking;

    @Autowired
    LlmAssignmentRepository assignments;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    LlmCallRepository calls;

    @Autowired
    MutableClock clock;

    @Autowired
    TestEntityManager em;

    @BeforeEach
    void setUp() {
        fake.reset();
        clock.set(LlmRouterTestConfig.NOW);
    }

    @Test
    void scoresAnAnalyzedJobOnceInTheProfileLanguage() {
        long id = analyzedJob("a");

        ScoringResult first = scoring.score(id, PROFILE);
        ScoringResult second = scoring.score(id, PROFILE);

        assertThat(first.outcome()).isEqualTo(ScoringResult.Outcome.SCORED);
        assertThat(first.score()).isEqualTo(73);
        assertThat(first.llmCalled()).isTrue();
        assertThat(second.outcome()).isEqualTo(ScoringResult.Outcome.ALREADY_SCORED);
        assertThat(second.llmCalled()).isFalse();
        assertThat(fake.requests()).hasSize(1);
        assertThat(fake.requests().get(0).purpose()).isEqualTo(LlmPurpose.SCORE);
        assertThat(fake.requests().get(0).system()).contains("one sentence in language 'vi'");
        AssessmentView row = row(id);
        assertThat(row.score()).isEqualTo(73);
        assertThat(row.scoreDetails().path("breakdown").path("skills").asInt()).isEqualTo(32);
        assertThat(row.scoreDetails().path("strengths")).hasSize(1);
        assertThat(row.summary()).startsWith("Fake score");
        assertThat(row.scoreModel()).isEqualTo("primary");
        assertThat(row.status()).isEqualTo(AssessmentStatus.DONE);
    }

    @Test
    void wrongSumIsReplacedByTheBreakdownSum() {
        long id = analyzedJob("sum");
        fake.enqueue(Step.text(WRONG_SUM));

        ScoringResult result = scoring.score(id, PROFILE);

        assertThat(result.score()).isEqualTo(70);
        assertThat(row(id).scoreDetails().path("score").asInt()).isEqualTo(70);
        assertThat(fake.requests()).as("no retry for the sum alone").hasSize(1);
    }

    @Test
    void invalidJsonGetsOneCorrectiveRetryThenFails() {
        long id = analyzedJob("bad");
        fake.enqueue(Step.text("I think 80/100"), Step.text("{\"score\": 80}"));

        ScoringResult result = scoring.score(id, PROFILE);

        assertThat(result.outcome()).isEqualTo(ScoringResult.Outcome.FAILED);
        assertThat(fake.requests()).hasSize(2);
        AssessmentView row = row(id);
        assertThat(row.status()).isEqualTo(AssessmentStatus.FAILED);
        assertThat(row.attempts()).isEqualTo(1);
        assertThat(row.score()).isNull();
    }

    @Test
    void threeFailedAttemptsArePermanent() {
        long id = analyzedJob("perm");
        for (int i = 0; i < 3; i++) {
            fake.enqueue(Step.text("no"), Step.text("no"));
        }

        scoring.score(id, PROFILE);
        scoring.score(id, PROFILE);
        ScoringResult third = scoring.score(id, PROFILE);
        ScoringResult fourth = scoring.score(id, PROFILE);

        assertThat(third.outcome()).isEqualTo(ScoringResult.Outcome.FAILED_PERMANENTLY);
        assertThat(fourth.outcome()).isEqualTo(ScoringResult.Outcome.FAILED_PERMANENTLY);
        assertThat(fourth.llmCalled()).isFalse();
        assertThat(fake.requests()).hasSize(6);
    }

    @Test
    void monthlyBudgetStopLeavesTheJobPending() {
        long id = analyzedJob("budget");
        assignments.saveAndFlush(new LlmAssignment(LlmPurpose.SCORE, "paid", List.of(), false, false,
                LlmRouterTestConfig.NOW, LlmAssignment.BY_USER));
        calls.saveAndFlush(LlmCall.builder().purpose(LlmPurpose.SCORE).model("paid", "paid-model", ModelTier.PAID)
                .promptVersion("score-v1").tokens(1, 1).costUsd(LlmRouterTestConfig.MONTHLY_BUDGET).success(true)
                .createdAt(LlmRouterTestConfig.NOW.minusSeconds(60)).build());

        ScoringResult result = scoring.score(id, PROFILE);

        assertThat(result.outcome()).isEqualTo(ScoringResult.Outcome.BUDGET_REACHED);
        assertThat(fake.requests()).isEmpty();
        assertThat(row(id).status()).isEqualTo(AssessmentStatus.PENDING);
        assertThat(row(id).attempts()).as("no attempt used").isZero();
    }

    @Test
    void jobMarkedNotEligibleIsNeverSent() {
        long id = analyzedJob("inel");
        tracking.setEligibilityDecision(id, PROFILE.id(), EligibilityDecision.INELIGIBLE, null);
        fake.reset();

        ScoringResult result = scoring.score(id, PROFILE);

        assertThat(result.outcome()).isEqualTo(ScoringResult.Outcome.MARKED_INELIGIBLE);
        assertThat(fake.requests()).isEmpty();
    }

    @Test
    void failedCheckWithoutADecisionCanBeScoredAndThePromptListsTheChecks() {
        long id = job("fail-check");
        fake.enqueue(Step.text(INELIGIBLE_EXTRACTION));
        analysis.analyze(id);
        fake.reset();

        ScoringResult result = scoring.score(id, PROFILE);

        assertThat(result.outcome()).as("ADR-0011: the owner decides").isEqualTo(ScoringResult.Outcome.SCORED);
        assertThat(fake.requests()).singleElement().satisfies(request -> assertThat(request.messages().getLast()
                .content()).contains("<eligibility_checks>", "REGION: FAIL — US", "SCAM: OK — No signals"));
    }

    @Test
    void notAnalyzedAndPrefilterRejectedJobsAreNeverSent() {
        long fresh = job("fresh");
        long rejected = EnrichTestConfig.insertJob(em.getEntityManager(), "fp-sc-ios", "iOS Engineer", "Swift.");
        analysis.analyze(rejected);
        fake.reset();

        assertThat(scoring.score(fresh, PROFILE).outcome()).isEqualTo(ScoringResult.Outcome.NOT_ANALYZED);
        assertThat(scoring.score(rejected, PROFILE).outcome()).isEqualTo(ScoringResult.Outcome.NOT_ANALYZED);
        assertThat(fake.requests()).isEmpty();
    }

    @Test
    void bulkDryRunNamesTheModelAndSkipsWhatCannotBeScored() {
        long ready = analyzedJob("r1");
        long scored = analyzedJob("r2");
        scoring.score(scored, PROFILE);
        long fresh = job("r3");
        long ineligible = analyzedJob("r4");
        tracking.setEligibilityDecision(ineligible, PROFILE.id(), EligibilityDecision.INELIGIBLE, null);
        fake.reset();

        BulkScoreReport report = scoring.scoreSelected(List.of(ready, scored, fresh, ineligible, 999_999L), PROFILE,
                true);

        assertThat(report.willScore()).isEqualTo(1);
        assertThat(report.estimatedCalls()).isEqualTo(1);
        assertThat(report.skipped()).containsEntry("alreadyScored", 1).containsEntry("notAnalyzed", 1)
                .containsEntry("markedIneligible", 1).containsEntry("notFound", 1);
        assertThat(report.model().modelId()).isEqualTo("primary");
        assertThat(report.model().tier()).isEqualTo(ModelTier.FREE);
        assertThat(report.estimatedCostUsd()).isEqualByComparingTo("0");
        assertThat(fake.requests()).isEmpty();
    }

    @Test
    void bulkRunNeverSendsUnanalyzedOrIneligibleJobs() {
        long a = analyzedJob("b1");
        long b = analyzedJob("b2");
        long fresh = job("b3");

        BulkScoreReport report = scoring.scoreSelected(List.of(fresh, a, b), PROFILE, false);

        assertThat(report.scored()).isEqualTo(2);
        assertThat(fake.requests()).extracting(r -> r.jobId()).containsExactly(a, b);
    }

    @Test
    void bulkRunStopsAtAPause() {
        long a = analyzedJob("p1");
        long b = analyzedJob("p2");
        fake.enqueue(Step.dailyQuota(null));

        BulkScoreReport report = scoring.scoreSelected(List.of(a, b), PROFILE, false);

        assertThat(report.stoppedBy()).isEqualTo(ScoringResult.Outcome.LLM_PAUSED);
        assertThat(report.notProcessed()).containsExactly(a, b);
        assertThat(row(a).status()).as("stays pending").isEqualTo(AssessmentStatus.PENDING);
    }

    @Test
    void bulkIsLimitedTo200Jobs() {
        assertThatThrownBy(() -> scoring.scoreSelected(LongStream.rangeClosed(1, 201).boxed().toList(), PROFILE, true))
                .isInstanceOf(TooManyJobsException.class);
    }

    @Test
    void rescoreScoresActiveScoredJobsAgainAfterTheDryRun() {
        long a = analyzedJob("rs1");
        long b = analyzedJob("rs2");
        scoring.score(a, PROFILE);
        scoring.score(b, PROFILE);
        analyzedJob("rs3");
        fake.reset();

        RescorePlan plan = scoring.rescoreActive(PROFILE, true);
        assertThat(plan.jobCount()).isEqualTo(2);
        assertThat(fake.requests()).isEmpty();

        fake.enqueue(Step.text(WRONG_SUM));
        RescorePlan done = scoring.rescoreActive(PROFILE, false);

        assertThat(done.scored()).isEqualTo(2);
        assertThat(fake.requests()).extracting(r -> r.jobId()).containsExactly(a, b);
        assertThat(row(a).score()).isEqualTo(70);
    }

    @Test
    void bulkDryRunNamesAPaidModel() {
        analyzedJob("pd1");
        assignments.saveAndFlush(new LlmAssignment(LlmPurpose.SCORE, "paid", List.of(), false, false,
                LlmRouterTestConfig.NOW, LlmAssignment.BY_USER));

        BulkScoreReport report = scoring.scoreSelected(List.of(), PROFILE, true);

        assertThat(report.model().modelId()).isEqualTo("paid");
        assertThat(report.model().paid()).isTrue();
        assertThat(report.model().blockedByConsent()).as("consent given in this test context").isFalse();
    }

    private long job(String suffix) {
        return EnrichTestConfig.insertJob(em.getEntityManager(), "fp-sc-" + suffix, "Senior Java Developer",
                DESCRIPTION);
    }

    /** A job analyzed with the canned extraction (ELIGIBLE); the fake's request log is cleared. */
    private long analyzedJob(String suffix) {
        long id = job(suffix);
        analysis.analyze(id);
        fake.reset();
        return id;
    }

    private AssessmentView row(long jobId) {
        em.flush();
        em.clear();
        return assessments.find(jobId, PROFILE.id()).orElseThrow();
    }
}
