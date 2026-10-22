package io.github.quanghung304.jobhunter.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.common.Sleeper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Telegram Bot API {@code sendMessage} with {@code parse_mode=HTML}. A text longer than 4096
 * characters is split at line breaks into several messages (each line must be valid HTML on its
 * own). A 429 is retried once after the {@code retry_after} the API asks for.
 */
public class TelegramClient {

    /** Telegram's limit per message. */
    static final int MAX_LENGTH = 4096;
    /** Upper bound for an honored {@code retry_after}. */
    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

    private final RestClient http;
    private final String token;
    private final String chatId;
    private final Sleeper sleeper;
    private final ObjectMapper json;

    public TelegramClient(NotifyProperties.Telegram properties, Sleeper sleeper, ObjectMapper json) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().baseUrl(properties.apiBaseUrl().toString()).requestFactory(factory).build();
        this.token = properties.botToken();
        this.chatId = properties.chatId();
        this.sleeper = sleeper;
        this.json = json;
    }

    /** Sends the text as one or more messages; the first failure stops and is thrown. */
    public void sendMessage(String html) {
        for (String part : split(html)) {
            send(part);
        }
    }

    /** Parts of at most {@link #MAX_LENGTH} characters, cut at line breaks (a longer single line is cut hard). */
    static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String rest = line;
            while (rest.length() > MAX_LENGTH) {
                flush(parts, current);
                parts.add(rest.substring(0, MAX_LENGTH));
                rest = rest.substring(MAX_LENGTH);
            }
            int needed = current.isEmpty() ? rest.length() : current.length() + 1 + rest.length();
            if (needed > MAX_LENGTH) {
                flush(parts, current);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(rest);
        }
        flush(parts, current);
        return parts;
    }

    private static void flush(List<String> parts, StringBuilder current) {
        String part = current.toString().strip();
        if (!part.isEmpty()) {
            parts.add(part);
        }
        current.setLength(0);
    }

    private void send(String text) {
        Map<String, Object> body = Map.of("chat_id", chatId, "text", text, "parse_mode", "HTML",
                "disable_web_page_preview", true);
        Response first = post(body);
        if (first.status() == 429) {
            Duration wait = retryAfter(first.body());
            log.warn("Telegram rate limit; retrying once in {}s", wait.toSeconds());
            try {
                sleeper.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TelegramException("interrupted while waiting for the Telegram rate limit", e);
            }
            Response second = post(body);
            check(second);
            return;
        }
        check(first);
    }

    private record Response(int status, String body) {
    }

    private Response post(Map<String, Object> body) {
        try {
            return http.post()
                    // Literal, not a URI variable: variables are strictly encoded, and tokens contain ':'.
                    .uri("/bot" + token + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> new Response(response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            // Never log the URL: it contains the bot token.
            throw new TelegramException("Telegram not reachable: " + e.getClass().getSimpleName(), e);
        }
    }

    private void check(Response response) {
        if (HttpStatusCode.valueOf(response.status()).is2xxSuccessful()) {
            return;
        }
        throw new TelegramException("Telegram answered " + response.status() + ": " + description(response.body()));
    }

    private String description(String body) {
        try {
            JsonNode node = json.readTree(body);
            return node.path("description").asText(body);
        } catch (IOException e) {
            return body;
        }
    }

    private Duration retryAfter(String body) {
        try {
            long seconds = json.readTree(body).path("parameters").path("retry_after").asLong(1);
            Duration wait = Duration.ofSeconds(Math.max(1, seconds));
            return wait.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : wait;
        } catch (IOException e) {
            return Duration.ofSeconds(1);
        }
    }

    /** Sending failed; the message never contains the bot token. */
    public static class TelegramException extends RuntimeException {

        public TelegramException(String message) {
            super(message);
        }

        public TelegramException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
