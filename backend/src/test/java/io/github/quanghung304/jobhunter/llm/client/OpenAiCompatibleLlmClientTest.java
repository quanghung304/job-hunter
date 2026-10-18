package io.github.quanghung304.jobhunter.llm.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmTestModels;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Response shapes follow the providers' docs (OpenAI-style chat completions, Groq 429 wording). */
class OpenAiCompatibleLlmClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    static final String SUCCESS = """
            {"id":"chatcmpl-1","object":"chat.completion","model":"llama-test",
             "choices":[{"index":0,"message":{"role":"assistant","content":"{\\"score\\": 70}"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1234,"completion_tokens":56,"total_tokens":1290}}""";
    static final String GROQ_DAILY_429 = """
            {"error":{"message":"Rate limit reached for model `openai/gpt-oss-120b` in organization `org_1` \
            service tier `on_demand` on requests per day (RPD): Limit 1000, Used 1000, Requested 1. \
            Please try again in 1m26.4s.","type":"requests","code":"rate_limit_exceeded"}}""";
    static final String GROQ_MINUTE_429 = """
            {"error":{"message":"Rate limit reached for model `openai/gpt-oss-120b` in organization `org_1` \
            service tier `on_demand` on tokens per minute (TPM): Limit 12000, Used 11900, Requested 900. \
            Please try again in 4.5s.","type":"tokens","code":"rate_limit_exceeded"}}""";

    final OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void successParsesContentUsageAndRateLimitHeaders() {
        stub(okJson(SUCCESS)
                .withHeader("x-ratelimit-remaining-requests", "998")
                .withHeader("x-ratelimit-remaining-tokens", "11000")
                .withHeader("x-ratelimit-reset-requests", "2m59.56s")
                .withHeader("x-ratelimit-reset-tokens", "7.66s"));

        LlmResponse response = client.send(request(model(true), Duration.ofSeconds(5)));

        assertThat(response.text()).isEqualTo("{\"score\": 70}");
        assertThat(response.inputTokens()).isEqualTo(1234);
        assertThat(response.outputTokens()).isEqualTo(56);
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.rateLimit()).isEqualTo(new RateLimitInfo(998, 11000, Duration.ofMillis(179_560),
                Duration.ofMillis(7_660)));
        wm.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer secret-key"))
                .withRequestBody(equalToJson("""
                        {"model":"llama-test",
                         "messages":[{"role":"system","content":"sys"},{"role":"user","content":"user text"},
                                     {"role":"assistant","content":"bad"},{"role":"user","content":"fix it"}],
                         "max_tokens":800,"temperature":0,"response_format":{"type":"json_object"}}""")));
    }

    @Test
    void withoutJsonModeOrKeyNeitherIsSent() {
        stub(okJson(SUCCESS));
        RegisteredModel keyless = new RegisteredModel("ollama", "Ollama", "ollama", model(false).providerType(),
                wm.baseUrl() + "/v1", "qwen", model(false).tier(), LlmTestModels.NO_LIMITS, null, false, "", false, null, 0);

        client.send(request(keyless, Duration.ofSeconds(5)));

        wm.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withoutHeader("Authorization")
                .withRequestBody(equalToJson("""
                        {"model":"qwen","messages":"${json-unit.ignore}","max_tokens":800,"temperature":0}""")));
    }

    @Test
    void rateLimitWithShortRetryAfter() {
        stub(aResponse().withStatus(429).withHeader("retry-after", "5").withBody(GROQ_MINUTE_429));

        assertThatThrownBy(() -> client.send(request(model(true), Duration.ofSeconds(5))))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.kind()).isEqualTo(LlmErrorKind.RATE_LIMIT);
                    assertThat(e.httpStatus()).isEqualTo(429);
                    assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(e.getMessage()).contains("tokens per minute (TPM)");
                });
    }

    @Test
    void dailyQuotaBodyIsExhausted() {
        stub(aResponse().withStatus(429).withHeader("retry-after", "87").withBody(GROQ_DAILY_429));

        assertThatThrownBy(() -> client.send(request(model(true), Duration.ofSeconds(5))))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.kind()).isEqualTo(LlmErrorKind.QUOTA_EXHAUSTED);
                    assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(87));
                });
    }

    @Test
    void httpErrorsAreClassified() {
        record Case(int status, String body, LlmErrorKind kind) {
        }
        List<Case> cases = List.of(
                new Case(401, "{\"error\":{\"message\":\"Invalid API Key\",\"type\":\"invalid_request_error\"}}",
                        LlmErrorKind.AUTH),
                new Case(403, "{\"error\":{\"message\":\"forbidden\"}}", LlmErrorKind.AUTH),
                new Case(404, "{\"error\":{\"message\":\"The model `x` does not exist\",\"code\":\"model_not_found\"}}",
                        LlmErrorKind.MODEL_NOT_FOUND),
                new Case(500, "{\"error\":{\"message\":\"internal\"}}", LlmErrorKind.SERVER),
                new Case(503, "upstream unavailable", LlmErrorKind.SERVER),
                new Case(400, "{\"error\":{\"message\":\"Failed to generate JSON\",\"code\":\"json_validate_failed\"}}",
                        LlmErrorKind.INVALID_OUTPUT),
                new Case(400, "{\"error\":{\"message\":\"max_tokens too large\"}}", LlmErrorKind.BAD_REQUEST));
        for (Case c : cases) {
            wm.resetAll();
            stub(aResponse().withStatus(c.status()).withBody(c.body()));

            assertThatThrownBy(() -> client.send(request(model(true), Duration.ofSeconds(5))))
                    .as("HTTP %d", c.status())
                    .isInstanceOfSatisfying(LlmCallException.class, e -> {
                        assertThat(e.kind()).isEqualTo(c.kind());
                        assertThat(e.httpStatus()).isEqualTo(c.status());
                    });
        }
    }

    @Test
    void readTimeout() {
        stub(okJson(SUCCESS).withFixedDelay(1_500));

        assertThatThrownBy(() -> client.send(request(model(true), Duration.ofMillis(300))))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.kind()).isEqualTo(LlmErrorKind.TIMEOUT);
                    assertThat(e.httpStatus()).isNull();
                });
    }

    @Test
    void nonJsonOrEmptyChoicesAreInvalidOutput() {
        stub(aResponse().withStatus(200).withBody("<html>oops</html>"));
        assertThatThrownBy(() -> client.send(request(model(true), Duration.ofSeconds(5))))
                .isInstanceOfSatisfying(LlmCallException.class,
                        e -> assertThat(e.kind()).isEqualTo(LlmErrorKind.INVALID_OUTPUT));

        wm.resetAll();
        stub(okJson("{\"choices\":[]}"));
        assertThatThrownBy(() -> client.send(request(model(true), Duration.ofSeconds(5))))
                .isInstanceOfSatisfying(LlmCallException.class,
                        e -> assertThat(e.kind()).isEqualTo(LlmErrorKind.INVALID_OUTPUT));
    }

    @Test
    void connectionRefusedIsAServerError() {
        RegisteredModel down = LlmTestModels.openAi("http://localhost:1/v1", true);

        assertThatThrownBy(() -> client.send(request(down, Duration.ofSeconds(2))))
                .isInstanceOfSatisfying(LlmCallException.class, e -> assertThat(e.kind()).isEqualTo(LlmErrorKind.SERVER));
    }

    private static void stub(ResponseDefinitionBuilder response) {
        wm.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(response));
    }

    private static RegisteredModel model(boolean jsonMode) {
        return LlmTestModels.openAi(wm.baseUrl() + "/v1", jsonMode);
    }

    static LlmRequest request(RegisteredModel model, Duration timeout) {
        return new LlmRequest(model, LlmPurpose.SCORE, 7L, "score-v1", "sys",
                List.of(ChatMessage.user("user text"), ChatMessage.assistant("bad"), ChatMessage.user("fix it")),
                800, BigDecimal.ZERO, timeout);
    }
}
