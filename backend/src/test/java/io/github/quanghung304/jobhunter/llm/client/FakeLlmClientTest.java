package io.github.quanghung304.jobhunter.llm.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import io.github.quanghung304.jobhunter.llm.LlmTestModels;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeLlmClientTest {

    @Test
    void defaultAnswersAreTheCannedJsonPerPrompt() {
        FakeLlmClient fake = FakeLlmClient.simulating(null);

        assertThat(fake.send(request(LlmPurpose.EXTRACT, null)).text()).contains("\"remoteType\": \"FULLY_REMOTE\"");
        assertThat(fake.send(request(LlmPurpose.SCORE, 5L)).text()).contains("\"score\": 73");
        assertThat(fake.requests()).hasSize(2);
    }

    @Test
    void perJobFileWinsOverDefault() {
        FakeLlmClient fake = FakeLlmClient.simulating(null);

        assertThat(fake.send(request(LlmPurpose.SCORE, 42L)).text()).contains("\"score\": 91");
    }

    @Test
    void scriptedStepsComeFirst() {
        FakeLlmClient fake = FakeLlmClient.simulating(null).enqueue(
                FakeLlmClient.Step.text("not json"),
                FakeLlmClient.Step.httpError(500, "boom", null),
                FakeLlmClient.Step.rateLimited(Duration.ofSeconds(3)),
                FakeLlmClient.Step.dailyQuota(null),
                FakeLlmClient.Step.httpError(401, "bad key", null),
                FakeLlmClient.Step.timeout());

        assertThat(fake.send(request(LlmPurpose.SCORE, 1L)).text()).isEqualTo("not json");
        assertThat(kindOf(fake)).isEqualTo(LlmErrorKind.SERVER);
        assertThat(kindOf(fake)).isEqualTo(LlmErrorKind.RATE_LIMIT);
        assertThat(kindOf(fake)).isEqualTo(LlmErrorKind.QUOTA_EXHAUSTED);
        assertThat(kindOf(fake)).isEqualTo(LlmErrorKind.AUTH);
        assertThat(kindOf(fake)).isEqualTo(LlmErrorKind.TIMEOUT);
        assertThat(fake.send(request(LlmPurpose.SCORE, 1L)).text()).contains("\"score\": 73");
    }

    @Test
    void quotaAfterSimulatesAnExhaustedDailyQuota() {
        FakeLlmClient fake = FakeLlmClient.simulating("quota-after:2");

        fake.send(request(LlmPurpose.EXTRACT, 1L));
        fake.send(request(LlmPurpose.EXTRACT, 2L));

        assertThatThrownBy(() -> fake.send(request(LlmPurpose.EXTRACT, 3L)))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.kind()).isEqualTo(LlmErrorKind.QUOTA_EXHAUSTED);
                    assertThat(e.httpStatus()).isEqualTo(429);
                    assertThat(e.getMessage()).contains("requests per day (RPD)");
                });
        fake.reset();
        assertThat(fake.send(request(LlmPurpose.EXTRACT, 4L)).text()).isNotBlank();
    }

    @Test
    void badSimulateValueFails() {
        assertThatThrownBy(() -> FakeLlmClient.simulating("explode")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usageIsEstimatedFromLength() {
        LlmResponse response = FakeLlmClient.simulating(null).enqueue(FakeLlmClient.Step.text("x".repeat(40)))
                .send(request(LlmPurpose.SCORE, 1L));

        assertThat(response.outputTokens()).isEqualTo(10);
        assertThat(response.inputTokens()).isPositive();
    }

    private static LlmErrorKind kindOf(FakeLlmClient fake) {
        try {
            fake.send(request(LlmPurpose.SCORE, 1L));
            return null;
        } catch (LlmCallException e) {
            return e.kind();
        }
    }

    private static LlmRequest request(LlmPurpose purpose, Long jobId) {
        String promptId = purpose == LlmPurpose.EXTRACT ? "extract-v1" : "score-v1";
        return new LlmRequest(LlmTestModels.withLimits("fake", ModelTier.LOCAL, LlmTestModels.NO_LIMITS), purpose,
                jobId, promptId, "system prompt", List.of(ChatMessage.user("user prompt")), 100, null,
                Duration.ofSeconds(1));
    }
}
