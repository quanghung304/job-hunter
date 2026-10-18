package io.github.quanghung304.jobhunter.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import io.github.quanghung304.jobhunter.llm.registry.ProviderType;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * {@code POST {baseUrl}/chat/completions} for Groq, OpenRouter and Ollama (ADR-0008).
 * Body: {@code {model, messages, max_tokens, temperature?, response_format?}}; the answer is
 * {@code choices[0].message.content} with {@code usage.prompt_tokens/completion_tokens}.
 * Rate-limit headers: {@code x-ratelimit-*} and {@code retry-after}.
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper json;
    private final Clock clock;
    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    public OpenAiCompatibleLlmClient(ObjectMapper json, Clock clock) {
        this.json = json;
        this.clock = clock;
    }

    @Override
    public ProviderType type() {
        return ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public LlmResponse send(LlmRequest request) {
        RegisteredModel model = request.model();
        RestClient client = clients.computeIfAbsent(model.baseUrl() + "|" + request.timeout(),
                key -> restClient(model.baseUrl(), request.timeout()));
        String body = body(request).toString();
        long start = System.nanoTime();
        try {
            return client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (!model.apiKey().isEmpty()) {
                            headers.setBearerAuth(model.apiKey());
                        }
                    })
                    .body(body)
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        String text = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        long latency = elapsedMs(start);
                        RateLimitInfo rateLimit = ProviderErrors.openAiStyle(res.getHeaders()::getFirst, clock);
                        if (status >= 200 && status < 300) {
                            return parse(text, status, latency, rateLimit);
                        }
                        throw error(status, text, res.getHeaders(), rateLimit, latency);
                    });
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof HttpTimeoutException
                    || e.getCause() instanceof InterruptedIOException;
            throw new LlmCallException(timeout ? LlmErrorKind.TIMEOUT : LlmErrorKind.SERVER, null,
                    (timeout ? "timeout: " : "network error: ") + e.getMessage(), null, null, elapsedMs(start), e);
        }
    }

    ObjectNode body(LlmRequest request) {
        ObjectNode body = json.createObjectNode();
        body.put("model", request.model().modelId());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.system());
        for (ChatMessage message : request.messages()) {
            messages.addObject()
                    .put("role", message.role() == ChatMessage.Role.USER ? "user" : "assistant")
                    .put("content", message.content());
        }
        body.put("max_tokens", request.maxOutputTokens());
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        if (request.model().jsonMode()) {
            body.putObject("response_format").put("type", "json_object");
        }
        return body;
    }

    private LlmResponse parse(String text, int status, long latency, RateLimitInfo rateLimit) {
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (IOException e) {
            throw new LlmCallException(LlmErrorKind.INVALID_OUTPUT, status,
                    "response body is not JSON: " + ProviderErrors.shorten(text), null, rateLimit, latency, e);
        }
        JsonNode choice = root.path("choices").path(0);
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            // reasoning models that run out of max_tokens while thinking answer with no content
            throw new LlmCallException(LlmErrorKind.INVALID_OUTPUT, status,
                    "no answer text in the response (finish_reason=" + choice.path("finish_reason").asText("?")
                            + "; reasoning models may need a higher minOutputTokens)", null, rateLimit, latency, null);
        }
        JsonNode usage = root.path("usage");
        return new LlmResponse(content.asText(), usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0), latency, status, rateLimit);
    }

    private LlmCallException error(int status, String body, HttpHeaders headers, RateLimitInfo rateLimit,
            long latency) {
        Duration retryAfter = ProviderErrors.retryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER), clock);
        LlmErrorKind kind = ProviderErrors.classify(status, body, retryAfter);
        return new LlmCallException(kind, status, "HTTP " + status + ": " + ProviderErrors.shorten(body),
                retryAfter, rateLimit, latency, null);
    }

    private static RestClient restClient(String baseUrl, Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
