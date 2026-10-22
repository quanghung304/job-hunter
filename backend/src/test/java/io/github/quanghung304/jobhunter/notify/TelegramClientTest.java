package io.github.quanghung304.jobhunter.notify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Bot API {@code sendMessage} against WireMock (response shapes from core.telegram.org/bots/api). */
class TelegramClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    static final String PATH = "/bot123:SECRET/sendMessage";
    static final ObjectMapper JSON = new ObjectMapper();
    static final String OK = "{\"ok\":true,\"result\":{\"message_id\":7}}";
    static final String TOO_MANY = """
            {"ok":false,"error_code":429,"description":"Too Many Requests: retry after 3",
             "parameters":{"retry_after":3}}""";

    final List<Duration> sleeps = new ArrayList<>();
    TelegramClient client;

    @BeforeEach
    void setUp() {
        var settings = new NotifyProperties.Telegram(true, "123:SECRET", "-1001", URI.create(wm.baseUrl()));
        client = new TelegramClient(settings, sleeps::add, new ObjectMapper());
    }

    @Test
    void sendsHtmlToTheChat() {
        wm.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(OK)));

        client.sendMessage("<b>Job Hunter</b> &amp; friends");

        wm.verify(1, postRequestedFor(urlEqualTo(PATH)).withRequestBody(equalToJson("""
                {"chat_id":"-1001","text":"<b>Job Hunter</b> &amp; friends","parse_mode":"HTML",
                 "disable_web_page_preview":true}""")));
        assertThat(sleeps).isEmpty();
    }

    @Test
    void aRateLimitIsRetriedOnceAfterRetryAfter() {
        wm.stubFor(post(urlEqualTo(PATH)).inScenario("rate").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Content-Type", "application/json")
                        .withBody(TOO_MANY))
                .willSetStateTo("retry"));
        wm.stubFor(post(urlEqualTo(PATH)).inScenario("rate").whenScenarioStateIs("retry").willReturn(okJson(OK)));

        client.sendMessage("hello");

        wm.verify(2, postRequestedFor(urlEqualTo(PATH)));
        assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void aSecondRateLimitFails() {
        wm.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(429)
                .withHeader("Content-Type", "application/json").withBody(TOO_MANY)));

        assertThatThrownBy(() -> client.sendMessage("hello"))
                .isInstanceOf(TelegramClient.TelegramException.class)
                .hasMessageContaining("429")
                .hasMessageNotContaining("SECRET");
        wm.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void anErrorCarriesTheApiDescriptionButNotTheToken() {
        wm.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: can't parse entities\"}")));

        assertThatThrownBy(() -> client.sendMessage("<b>broken"))
                .isInstanceOf(TelegramClient.TelegramException.class)
                .hasMessage("Telegram answered 400: Bad Request: can't parse entities")
                .hasMessageNotContaining("SECRET");
    }

    @Test
    void aLongTextIsSentAsSeveralMessagesCutAtLines() throws Exception {
        wm.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(OK)));
        String line = "<b>90</b> <a href=\"http://localhost:8080/jobs/1\">" + "x".repeat(80) + "</a>";
        String text = String.join("\n", java.util.Collections.nCopies(100, line));

        client.sendMessage(text);

        var requests = wm.findAll(postRequestedFor(urlEqualTo(PATH)));
        assertThat(requests).hasSizeGreaterThan(1);
        int lines = 0;
        for (var request : requests) {
            String sent = JSON.readTree(request.getBodyAsString()).get("text").asText();
            assertThat(sent.length()).isLessThanOrEqualTo(TelegramClient.MAX_LENGTH);
            assertThat(sent.split("\n")).as("whole lines only").containsOnly(line);
            lines += sent.split("\n").length;
        }
        assertThat(lines).isEqualTo(100);
    }

    @Test
    void splitKeepsWholeLinesAndTheLimit() {
        String line = "y".repeat(1000);
        String text = String.join("\n", line, line, line, line, line);

        List<String> parts = TelegramClient.split(text);

        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).isEqualTo(String.join("\n", line, line, line, line));
        assertThat(parts.get(1)).isEqualTo(line);
        assertThat(parts).allSatisfy(part -> assertThat(part.length()).isLessThanOrEqualTo(TelegramClient.MAX_LENGTH));
    }

    @Test
    void aSingleOverlongLineIsCutHard() {
        List<String> parts = TelegramClient.split("z".repeat(TelegramClient.MAX_LENGTH + 10));

        assertThat(parts).extracting(String::length).containsExactly(TelegramClient.MAX_LENGTH, 10);
    }

    @Test
    void shortTextIsOneMessage() {
        assertThat(TelegramClient.split("a\nb")).containsExactly("a\nb");
    }
}
