package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.llm.client.LlmCallException;
import io.github.quanghung304.jobhunter.llm.client.LlmRequest;
import io.github.quanghung304.jobhunter.llm.client.LlmResponse;
import io.github.quanghung304.jobhunter.llm.client.ProviderErrors;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCall;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes one {@code llm_call} row per HTTP attempt and one INFO line
 * ({@code purpose model tokens cost ms}); never logs prompts or answers above DEBUG.
 */
@Component
class LlmCallLogger {

    private static final Logger log = LoggerFactory.getLogger(LlmCallLogger.class);

    private final LlmCallRepository calls;
    private final Clock clock;
    private final Set<String> warnedNoPricing = ConcurrentHashMap.newKeySet();

    LlmCallLogger(LlmCallRepository calls, Clock clock) {
        this.calls = calls;
        this.clock = clock;
    }

    /** A 2xx answer; {@code invalidOutput} is the validation error when the JSON was rejected. */
    void answered(LlmRequest request, LlmResponse response, String invalidOutput) {
        RegisteredModel model = request.model();
        BigDecimal cost = cost(model, response.inputTokens(), response.outputTokens());
        LlmCall.Builder row = base(request)
                .tokens(response.inputTokens(), response.outputTokens())
                .costUsd(cost)
                .latencyMs((int) response.latencyMs())
                .httpStatus(response.httpStatus());
        if (invalidOutput == null) {
            row.success(true);
        } else {
            row.failure(response.httpStatus(), LlmErrorKind.INVALID_OUTPUT, ProviderErrors.shorten(invalidOutput));
        }
        calls.save(row.build());
        log.info("llm purpose={} model={} prompt={} job={} tokens={}/{} cost={} ms={} outcome={}", request.purpose(),
                model.id(), request.promptId(), request.jobId(), response.inputTokens(), response.outputTokens(),
                cost, response.latencyMs(), invalidOutput == null ? "OK" : "INVALID_OUTPUT");
    }

    void failed(LlmRequest request, LlmCallException error) {
        calls.save(base(request)
                .tokens(0, 0)
                .latencyMs((int) error.latencyMs())
                .failure(error.httpStatus(), error.kind(), ProviderErrors.shorten(error.getMessage()))
                .build());
        log.info("llm purpose={} model={} prompt={} job={} ms={} outcome={} status={}", request.purpose(),
                request.model().id(), request.promptId(), request.jobId(), error.latencyMs(), error.kind(),
                error.httpStatus());
    }

    private LlmCall.Builder base(LlmRequest request) {
        RegisteredModel model = request.model();
        return LlmCall.builder()
                .purpose(request.purpose())
                .jobId(request.jobId())
                .model(model.id(), model.modelId(), model.tier())
                .promptVersion(request.promptId())
                .createdAt(clock.instant());
    }

    private BigDecimal cost(RegisteredModel model, int inputTokens, int outputTokens) {
        if (model.tier() != ModelTier.PAID) {
            return null;
        }
        return model.cost(inputTokens, outputTokens).orElseGet(() -> {
            if (warnedNoPricing.add(model.id())) {
                log.warn("llm model={} is PAID but has no pricing in llm.yaml; cost unknown", model.id());
            }
            return null;
        });
    }
}
