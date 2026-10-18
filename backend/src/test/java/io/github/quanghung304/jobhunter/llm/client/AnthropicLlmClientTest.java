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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import io.github.quanghung304.jobhunter.llm.LlmTestModels;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** The real SDK against WireMock through the provider {@code baseUrl} override. */
class AnthropicLlmClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    static final String MESSAGE = """
            {"id":"msg_01","type":"message","role":"assistant","model":"claude-haiku-5-5",
             "content":[{"type":"text","text":"{\\"score\\": "},{"type":"text","text":"70}"}],
             "stop_reason":"end_turn","stop_sequence":null,
             "usage":{"input_tokens":2100,"output_tokens":95}}""";

    final AnthropicLlmClient client = new AnthropicLlmClient(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void sendsMessagesWithoutTemperatureAndParsesTheAnswer() {
        wm.stubFor(post(urlEqualTo("/v1/messages")).willReturn(okJson(MESSAGE)
                .withHeader("anthropic-ratelimit-requests-remaining", "49")
                .withHeader("anthropic-ratelimit-tokens-remaining", "39000")
                .withHeader("anthropic-ratelimit-requests-reset", "2026-10-17T12:00:30Z")));

        LlmResponse response = client.send(OpenAiCompatibleLlmClientTest.request(
                LlmTestModels.anthropic(wm.baseUrl()), Duration.ofSeconds(5)));

        assertThat(response.text()).isEqualTo("{\"score\": 70}");
        assertThat(response.inputTokens()).isEqualTo(2100);
        assertThat(response.outputTokens()).isEqualTo(95);
        assertThat(response.rateLimit().remainingRequests()).isEqualTo(49);
        assertThat(response.rateLimit().remainingTokens()).isEqualTo(39000);
        assertThat(response.rateLimit().resetRequests()).isEqualTo(Duration.ofSeconds(30));
        wm.verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withHeader("x-api-key", equalTo("sk-test"))
                .withRequestBody(equalToJson("""
                        {"model":"claude-haiku-5-5","max_tokens":4096,"system":"sys",
                         "messages":[{"role":"user","content":"user text"},{"role":"assistant","content":"bad"},
                                     {"role":"user","content":"fix it"}],
                         "output_config":{"effort":"low"}}""")));
    }

    @Test
    void maxTokensLeavesRoomForThinking() {
        assertThat(AnthropicLlmClient.maxTokens(800)).isEqualTo(4096);
        assertThat(AnthropicLlmClient.maxTokens(1200)).isEqualTo(4800);
    }

    @Test
    void rateLimitAndAuthErrorsAreClassified() {
        wm.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(429)
                .withHeader("retry-after", "20")
                .withHeader("content-type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"Number of requests has exceeded your per-minute rate limit\"}}")));

        assertThatThrownBy(() -> client.send(OpenAiCompatibleLlmClientTest.request(
                LlmTestModels.anthropic(wm.baseUrl()), Duration.ofSeconds(5))))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.kind()).isEqualTo(LlmErrorKind.RATE_LIMIT);
                    assertThat(e.httpStatus()).isEqualTo(429);
                    assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(20));
                });

        wm.resetAll();
        wm.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(401)
                .withHeader("content-type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}")));

        assertThatThrownBy(() -> client.send(OpenAiCompatibleLlmClientTest.request(
                LlmTestModels.anthropic(wm.baseUrl()), Duration.ofSeconds(5))))
                .isInstanceOfSatisfying(LlmCallException.class, e -> assertThat(e.kind()).isEqualTo(LlmErrorKind.AUTH));
    }

    @Test
    void serverErrorIsNotRetriedBySdk() {
        wm.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(529)
                .withHeader("content-type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}")));

        assertThatThrownBy(() -> client.send(OpenAiCompatibleLlmClientTest.request(
                LlmTestModels.anthropic(wm.baseUrl()), Duration.ofSeconds(5))))
                .isInstanceOfSatisfying(LlmCallException.class, e -> assertThat(e.kind()).isEqualTo(LlmErrorKind.SERVER));
        assertThat(wm.getAllServeEvents()).as("maxRetries(0): the router retries").hasSize(1);
    }

    @Test
    void timeout() {
        wm.stubFor(post(urlEqualTo("/v1/messages")).willReturn(okJson(MESSAGE).withFixedDelay(1_500)));

        assertThatThrownBy(() -> client.send(OpenAiCompatibleLlmClientTest.request(
                LlmTestModels.anthropic(wm.baseUrl()), Duration.ofMillis(300))))
                .isInstanceOfSatisfying(LlmCallException.class, e -> assertThat(e.kind()).isEqualTo(LlmErrorKind.TIMEOUT));
    }
}
