package io.github.quanghung304.jobhunter.llm;

import static io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PersistenceFixtures;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.client.ChatMessage;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient.Step;
import io.github.quanghung304.jobhunter.llm.client.LlmRequest;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignment;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentEvent;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentEventRepository;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentRepository;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCall;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import io.github.quanghung304.jobhunter.llm.persistence.LlmModelState;
import io.github.quanghung304.jobhunter.llm.persistence.LlmModelStateRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@PostgresJpaTest
@Import(LlmRouterTestConfig.class)
@RecordApplicationEvents
class LlmRouterIT {

    /** The part of score-v1's answer these tests read. */
    record Score(@NotNull @Min(0) @Max(100) Integer score, String summary) {
    }

    static final String VALID = "{\"score\": 64, \"summary\": \"ok\"}";
    static final Map<String, String> VARIABLES = Map.of("summaryLanguage", "en", "candidateBlock", "Java dev",
            "title", "Java Developer", "company", "Acme", "extractionJson", "{}", "eligibility", "ELIGIBLE",
            "eligibilityReasons", "", "description", "Build things.");

    @Autowired
    LlmRouter router;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    BudgetGuard budget;

    @Autowired
    LlmCallRepository calls;

    @Autowired
    LlmAssignmentRepository assignments;

    @Autowired
    LlmAssignmentEventRepository assignmentEvents;

    @Autowired
    LlmModelStateRepository states;

    @Autowired
    MutableClock clock;

    @Autowired
    LlmRouterTestConfig.RecordingSleeper sleeper;

    @Autowired
    ApplicationEvents events;

    @Autowired
    TestEntityManager em;

    long jobId;

    @BeforeEach
    void reset() {
        jobId = PersistenceFixtures.insertJob(em.getEntityManager(), "fp-router");
        fake.reset();
        sleeper.clear();
        clock.set(NOW);
        budget.beginRun();
    }

    @Test
    void seedsTheAssignmentFromDefaultsAndUsesTheAssignedModel() {
        LlmOutcome<Score> outcome = score(jobId);

        assertThat(outcome).isInstanceOfSatisfying(LlmResult.class, result -> {
            assertThat(result.value()).isEqualTo(new Score(73, "Fake score: good fit for a Java backend contract."));
            assertThat(result.modelId()).isEqualTo("primary");
            assertThat(result.providerModel()).isEqualTo("primary-model");
            assertThat(result.promptId()).isEqualTo("score-v1");
            assertThat(result.httpCalls()).isEqualTo(1);
        });
        LlmAssignment seeded = assignments.findById(LlmPurpose.SCORE).orElseThrow();
        assertThat(seeded.getModelId()).isEqualTo("primary");
        assertThat(seeded.getFallbacks()).containsExactly("backup", "paid");
        assertThat(seeded.isAutoFallback()).isFalse();
        assertThat(seeded.getUpdatedBy()).isEqualTo("config");
        LlmCall row = onlyCall();
        assertThat(row.isSuccess()).isTrue();
        assertThat(row.getModelId()).isEqualTo("primary");
        assertThat(row.getProviderModel()).isEqualTo("primary-model");
        assertThat(row.getTier()).isEqualTo(ModelTier.FREE);
        assertThat(row.getCostUsd()).as("FREE: no cost").isNull();
        assertThat(row.getPurpose()).isEqualTo(LlmPurpose.SCORE);
        assertThat(row.getJobId()).isEqualTo(jobId);
        assertThat(row.getPromptVersion()).isEqualTo("score-v1");
        assertThat(row.getHttpStatus()).isEqualTo(200);
        assertThat(fake.requests().get(0).system()).contains("careful career advisor");
        assertThat(fake.requests().get(0).messages().get(0).content()).contains("<title>Java Developer</title>");
    }

    @Test
    void changingTheAssignmentRowAppliesToTheNextCall() {
        score(null);
        LlmAssignment row = assignments.findById(LlmPurpose.SCORE).orElseThrow();
        row.setModelId("backup");
        assignments.saveAndFlush(row);

        LlmOutcome<Score> outcome = score(null);

        assertThat(outcome).isInstanceOfSatisfying(LlmResult.class, r -> assertThat(r.modelId()).isEqualTo("backup"));
        assertThat(fake.requests()).extracting(r -> r.model().id()).containsExactly("primary", "backup");
    }

    @Test
    void dailyQuotaMarksExhaustedAndPausesThePurposeWithoutFallback() {
        fake.enqueue(Step.dailyQuota(null));

        LlmOutcome<Score> outcome = score(jobId);

        assertThat(outcome).isInstanceOfSatisfying(LlmUnavailable.class, u -> {
            assertThat(u.purpose()).isEqualTo(LlmPurpose.SCORE);
            assertThat(u.reason()).isEqualTo(LlmUnavailable.Reason.NO_USABLE_MODEL);
        });
        LlmModelState state = states.findById("primary").orElseThrow();
        assertThat(state.getStatus()).isEqualTo(ModelStatus.EXHAUSTED);
        assertThat(state.getUntil()).isEqualTo(Instant.parse("2026-10-18T00:00:00Z"));
        assertThat(onlyCall().getErrorKind()).isEqualTo(LlmErrorKind.QUOTA_EXHAUSTED);
        assertThat(onlyCall().getHttpStatus()).isEqualTo(429);
        assertThat(events.stream(LlmModelStatusChanged.class)).extracting(LlmModelStatusChanged::to)
                .containsExactly(ModelStatus.EXHAUSTED);

        LlmOutcome<Score> again = score(jobId);

        assertThat(again).isInstanceOf(LlmUnavailable.class);
        assertThat(fake.requests()).as("an exhausted model is not called again").hasSize(1);
        assertThat(calls.count()).isEqualTo(1);
    }

    @Test
    void autoFallbackSwitchesTheAssignmentAndDoesNotSwitchBack() {
        enableAutoFallback(false);
        fake.enqueue(Step.dailyQuota(Duration.ofMinutes(30)));

        LlmOutcome<Score> outcome = score(jobId);

        assertThat(outcome).isInstanceOfSatisfying(LlmResult.class, r -> assertThat(r.modelId()).isEqualTo("backup"));
        assertThat(fake.requests()).extracting(r -> r.model().id()).containsExactly("primary", "backup");
        LlmAssignment switched = assignments.findById(LlmPurpose.SCORE).orElseThrow();
        assertThat(switched.getModelId()).isEqualTo("backup");
        assertThat(switched.getUpdatedBy()).isEqualTo(AssignmentService.BY_AUTO_FALLBACK);
        assertThat(assignmentEvents.findByPurposeOrderByIdAsc(LlmPurpose.SCORE))
                .extracting(LlmAssignmentEvent::getFromModelId, LlmAssignmentEvent::getToModelId,
                        LlmAssignmentEvent::getReason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("primary", "backup", AssignmentChangeReason.AUTO_FALLBACK));

        clock.advance(Duration.ofMinutes(31));
        assertThat(score(jobId)).as("primary is AVAILABLE again but not switched back (T-022)")
                .isInstanceOfSatisfying(LlmResult.class, r -> assertThat(r.modelId()).isEqualTo("backup"));
        assertThat(assignmentEvents.findByPurposeOrderByIdAsc(LlmPurpose.SCORE)).hasSize(1);
    }

    @Test
    void paidFallbackOnlyWithAllowPaid() {
        enableAutoFallback(false);
        fake.enqueue(Step.dailyQuota(null), Step.dailyQuota(null));

        LlmOutcome<Score> withoutPaid = score(jobId);

        assertThat(withoutPaid).isInstanceOf(LlmUnavailable.class);
        assertThat(fake.requests()).extracting(r -> r.model().id()).containsExactly("primary", "backup");

        enableAutoFallback(true);
        LlmOutcome<Score> withPaid = score(jobId);

        assertThat(withPaid).isInstanceOfSatisfying(LlmResult.class, r -> assertThat(r.modelId()).isEqualTo("paid"));
        LlmCall paidCall = calls.findAll().stream().filter(c -> c.getModelId().equals("paid")).findFirst().orElseThrow();
        assertThat(paidCall.getTier()).isEqualTo(ModelTier.PAID);
        assertThat(paidCall.getCostUsd()).as("PAID: priced from the registry").isNotNull().isPositive();
    }

    @Test
    void modelWithoutKeyIsErrorAndNeverCalled() {
        LlmAssignment row = assignment();
        row.setModelId("nokey");
        assignments.saveAndFlush(row);

        LlmOutcome<Score> outcome = score(jobId);

        assertThat(outcome).isInstanceOfSatisfying(LlmUnavailable.class,
                u -> assertThat(u.detail()).contains("nokey: missing key"));
        assertThat(fake.requests()).isEmpty();
        assertThat(calls.count()).isZero();
        assertThat(states.findById("nokey").orElseThrow().getStatus()).isEqualTo(ModelStatus.ERROR);
    }

    @Test
    void invalidJsonGetsOneCorrectiveRetry() {
        fake.enqueue(Step.text("Sure! Here is the score: high"), Step.text(VALID));

        LlmOutcome<Score> outcome = score(jobId);

        assertThat(outcome).isInstanceOfSatisfying(LlmResult.class, r -> {
            assertThat(((Score) r.value()).score()).isEqualTo(64);
            assertThat(r.httpCalls()).isEqualTo(2);
        });
        LlmRequest retry = fake.requests().get(1);
        assertThat(retry.messages()).extracting(ChatMessage::role).containsExactly(
                ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT, ChatMessage.Role.USER);
        assertThat(retry.messages().get(1).content()).isEqualTo("Sure! Here is the score: high");
        assertThat(retry.messages().get(2).content()).contains("The answer contains no JSON object.");
        assertThat(calls.findAll()).extracting(LlmCall::isSuccess, LlmCall::getErrorKind).containsExactly(
                org.assertj.core.groups.Tuple.tuple(false, LlmErrorKind.INVALID_OUTPUT),
                org.assertj.core.groups.Tuple.tuple(true, null));
    }

    @Test
    void invalidTwiceFails() {
        fake.enqueue(Step.text("{\"score\": 300}"), Step.text("{\"score\": \"high\"}"));

        LlmOutcome<Score> outcome = score(jobId);

        assertThat(outcome).isInstanceOfSatisfying(LlmFailed.class, f -> {
            assertThat(f.kind()).isEqualTo(LlmErrorKind.INVALID_OUTPUT);
            assertThat(f.modelId()).isEqualTo("primary");
        });
        assertThat(calls.count()).isEqualTo(2);
        assertThat(states.findById("primary")).map(LlmModelState::getStatus).hasValue(ModelStatus.AVAILABLE);
    }

    @Test
    void serverErrorsAreRetriedWithBackoff() {
        fake.enqueue(Step.httpError(500, "boom", null), Step.timeout(), Step.canned());

        LlmOutcome<Score> outcome = score(jobId);

        assertThat(outcome).isInstanceOf(LlmResult.class);
        assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(8));
        assertThat(calls.findAll()).extracting(LlmCall::getErrorKind)
                .containsExactly(LlmErrorKind.SERVER, LlmErrorKind.TIMEOUT, null);
    }

    @Test
    void serverErrorsAfterRetriesFailTheAttempt() {
        fake.enqueue(Step.httpError(503, "a", null), Step.httpError(502, "b", null), Step.httpError(500, "c", null));

        LlmOutcome<Score> outcome = score(jobId);

        assertThat(outcome).isInstanceOfSatisfying(LlmFailed.class,
                f -> assertThat(f.kind()).isEqualTo(LlmErrorKind.SERVER));
        assertThat(calls.count()).isEqualTo(3);
        assertThat(states.findById("primary")).as("not a quota problem").isEmpty();
    }

    @Test
    void shortRetryAfterIsWaitedOnce() {
        fake.enqueue(Step.rateLimited(Duration.ofSeconds(5)), Step.canned());

        assertThat(score(jobId)).isInstanceOf(LlmResult.class);
        assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(5));
        assertThat(states.findById("primary")).map(LlmModelState::getStatus).hasValue(ModelStatus.AVAILABLE);
    }

    @Test
    void longerRateLimitPausesThePurpose() {
        fake.enqueue(Step.rateLimited(Duration.ofSeconds(30)));

        assertThat(score(jobId)).isInstanceOf(LlmUnavailable.class);
        LlmModelState state = states.findById("primary").orElseThrow();
        assertThat(state.getStatus()).isEqualTo(ModelStatus.RATE_LIMITED);
        assertThat(state.getUntil()).isEqualTo(NOW.plusSeconds(30));
        assertThat(sleeper.sleeps()).isEmpty();
    }

    @Test
    void authErrorMarksTheModelError() {
        fake.enqueue(Step.httpError(401, "{\"error\":{\"message\":\"Invalid API Key\"}}", null));

        assertThat(score(jobId)).isInstanceOf(LlmUnavailable.class);
        assertThat(states.findById("primary").orElseThrow().getStatus()).isEqualTo(ModelStatus.ERROR);
    }

    @Test
    void badRequestFailsWithoutRetry() {
        fake.enqueue(Step.httpError(400, "max_tokens too large", null));

        assertThat(score(jobId)).isInstanceOfSatisfying(LlmFailed.class,
                f -> assertThat(f.kind()).isEqualTo(LlmErrorKind.BAD_REQUEST));
        assertThat(calls.count()).isEqualTo(1);
    }

    @Test
    void callModelUsesTheGivenModelWithoutAssignment() {
        LlmOutcome<Score> outcome = router.callModel("local", LlmPurpose.EVAL, "score-v1", VARIABLES, null, Score.class);

        assertThat(outcome).isInstanceOfSatisfying(LlmResult.class, r -> assertThat(r.modelId()).isEqualTo("local"));
        assertThat(onlyCall().getPurpose()).isEqualTo(LlmPurpose.EVAL);
        assertThat(onlyCall().getTier()).isEqualTo(ModelTier.LOCAL);
        assertThat(fake.requests().get(0).timeout()).as("LOCAL timeout").isEqualTo(Duration.ofSeconds(180));
    }

    @Test
    void everyHttpAttemptHasExactlyOneRow() {
        fake.enqueue(Step.httpError(500, "x", null), Step.text("nope"), Step.text(VALID), Step.dailyQuota(null));

        score(jobId);
        score(jobId);

        assertThat(calls.count()).isEqualTo(fake.requests().size()).isEqualTo(4);
        assertThat(budget.callsThisRun()).isEqualTo(4);
    }

    private LlmOutcome<Score> score(Long jobId) {
        return router.call(LlmPurpose.SCORE, "score-v1", VARIABLES, jobId, Score.class);
    }

    private LlmCall onlyCall() {
        List<LlmCall> all = calls.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private LlmAssignment assignment() {
        router.call(LlmPurpose.SCORE, "score-v1", VARIABLES, null, Score.class);
        fake.reset();
        calls.deleteAll();
        assignmentEvents.deleteAll();
        return assignments.findById(LlmPurpose.SCORE).orElseThrow();
    }

    private void enableAutoFallback(boolean allowPaid) {
        LlmAssignment row = assignment();
        row.setAutoFallback(true);
        row.setAllowPaid(allowPaid);
        assignments.saveAndFlush(row);
    }
}
