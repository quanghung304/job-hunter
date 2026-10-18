package io.github.quanghung304.jobhunter.llm.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpResponseFor;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import io.github.quanghung304.jobhunter.llm.registry.ProviderType;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.io.InterruptedIOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Claude models through the official Anthropic Java SDK (Messages API).
 *
 * <p>Current Claude models reject a non-default {@code temperature} and think by default; thinking
 * tokens count against {@code max_tokens}. So this client sends no temperature, asks for
 * {@code effort: low} and allows {@link #maxTokens} instead of the prompt's output budget.
 * SDK retries are off ({@code maxRetries(0)}): the router retries and logs every attempt.
 * The provider {@code baseUrl}, if set, overrides the API URL (used by WireMock tests).
 */
@Component
public class AnthropicLlmClient implements LlmClient {

    /** Minimum {@code max_tokens}: room for adaptive thinking before the JSON answer. */
    static final int MIN_MAX_TOKENS = 4_096;

    private final Clock clock;
    private final Map<String, AnthropicClient> clients = new ConcurrentHashMap<>();

    public AnthropicLlmClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ProviderType type() {
        return ProviderType.ANTHROPIC;
    }

    @Override
    public LlmResponse send(LlmRequest request) {
        RegisteredModel model = request.model();
        AnthropicClient client = clients.computeIfAbsent(
                model.providerId() + "|" + model.apiKey().hashCode() + "|" + request.timeout(),
                key -> client(model, request.timeout()));
        long start = System.nanoTime();
        try {
            HttpResponseFor<Message> response = client.messages().withRawResponse().create(params(request));
            Message message = response.parse();
            long latency = elapsedMs(start);
            String text = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(textBlock -> textBlock.text())
                    .collect(Collectors.joining());
            return new LlmResponse(text, (int) message.usage().inputTokens(), (int) message.usage().outputTokens(),
                    latency, response.statusCode(), rateLimit(response.headers()));
        } catch (AnthropicServiceException e) {
            long latency = elapsedMs(start);
            String body = String.valueOf(e.body());
            Duration retryAfter = ProviderErrors.retryAfter(first(e.headers(), "retry-after"), clock);
            LlmErrorKind kind = ProviderErrors.classify(e.statusCode(), body, retryAfter);
            throw new LlmCallException(kind, e.statusCode(), "HTTP " + e.statusCode() + ": " + ProviderErrors.shorten(body),
                    retryAfter, rateLimit(e.headers()), latency, e);
        } catch (AnthropicIoException | AnthropicRetryableException e) {
            boolean timeout = hasTimeoutCause(e);
            throw new LlmCallException(timeout ? LlmErrorKind.TIMEOUT : LlmErrorKind.SERVER, null,
                    (timeout ? "timeout: " : "network error: ") + e.getMessage(), null, null, elapsedMs(start), e);
        }
    }

    static MessageCreateParams params(LlmRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(request.model().modelId())
                .maxTokens(maxTokens(request.maxOutputTokens()))
                .system(request.system())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
        for (ChatMessage message : request.messages()) {
            if (message.role() == ChatMessage.Role.USER) {
                builder.addUserMessage(message.content());
            } else {
                builder.addAssistantMessage(message.content());
            }
        }
        return builder.build();
    }

    static long maxTokens(int promptMaxOutputTokens) {
        return Math.max(MIN_MAX_TOKENS, 4L * promptMaxOutputTokens);
    }

    private RateLimitInfo rateLimit(Headers headers) {
        return ProviderErrors.anthropicStyle(name -> first(headers, name), clock);
    }

    private static String first(Headers headers, String name) {
        List<String> values = headers.values(name);
        return values.isEmpty() ? null : values.get(0);
    }

    private static boolean hasTimeoutCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    private static AnthropicClient client(RegisteredModel model, Duration timeout) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(model.apiKey())
                .maxRetries(0)
                .timeout(timeout);
        if (model.baseUrl() != null && !model.baseUrl().isBlank()) {
            builder.baseUrl(model.baseUrl());
        }
        return builder.build();
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
