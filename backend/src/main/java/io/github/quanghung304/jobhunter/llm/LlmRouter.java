package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.common.Sleeper;
import io.github.quanghung304.jobhunter.llm.client.ChatMessage;
import io.github.quanghung304.jobhunter.llm.client.LlmCallException;
import io.github.quanghung304.jobhunter.llm.client.LlmClient;
import io.github.quanghung304.jobhunter.llm.client.LlmRequest;
import io.github.quanghung304.jobhunter.llm.client.LlmResponse;
import io.github.quanghung304.jobhunter.llm.json.InvalidLlmOutputException;
import io.github.quanghung304.jobhunter.llm.json.JsonExtractor;
import io.github.quanghung304.jobhunter.llm.prompt.PromptRepository;
import io.github.quanghung304.jobhunter.llm.prompt.RenderedPrompt;
import io.github.quanghung304.jobhunter.llm.quota.CallPacer;
import io.github.quanghung304.jobhunter.llm.quota.ModelState;
import io.github.quanghung304.jobhunter.llm.quota.QuotaTracker;
import io.github.quanghung304.jobhunter.llm.registry.ModelRegistry;
import io.github.quanghung304.jobhunter.llm.registry.ProviderType;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The only entry point for LLM calls (AGENTS.md hard rule). For a purpose it resolves the
 * assigned model (or {@code app.llm.force-model}), skips models that are not usable (status,
 * missing key, monthly budget, PAID without the owner's consent: ADR-0009), uses fallbacks
 * only when {@code autoFallback} is on (PAID only with {@code allowPaid}), and runs the
 * attempt loop of scoring-spec §6:
 * <ul>
 *   <li>network/5xx/timeout → retry after 2 s and 8 s, then {@link LlmFailed};
 *   <li>a 429 with {@code retry-after} ≤ 10 s → wait once and retry;
 *   <li>other 429, 401/403/404 → model status updated by {@link QuotaTracker}, next usable
 *       fallback or {@link LlmUnavailable} (never a job failure);
 *   <li>invalid JSON/schema → one corrective retry with the validation error, then {@link LlmFailed}.
 * </ul>
 * Every HTTP attempt is paced ({@code rpm}), counted by {@link BudgetGuard} and logged in {@code llm_call}.
 */
@Service
public class LlmRouter {

    static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(2), Duration.ofSeconds(8));
    static final Duration MAX_INLINE_RETRY_AFTER = Duration.ofSeconds(10);
    private static final String BUDGET_MARK = "monthly budget used up";
    private static final String PAID_MARK = "paid calls not allowed (app.llm.allow-paid-calls=false)";

    private final ModelRegistry registry;
    private final AssignmentService assignments;
    private final PromptRepository prompts;
    private final JsonExtractor extractor;
    private final QuotaTracker quota;
    private final CallPacer pacer;
    private final BudgetGuard budget;
    private final LlmCallLogger callLogger;
    private final LlmProperties properties;
    private final Sleeper sleeper;
    private final Map<ProviderType, LlmClient> clients = new EnumMap<>(ProviderType.class);

    public LlmRouter(ModelRegistry registry, AssignmentService assignments, PromptRepository prompts,
            JsonExtractor extractor, QuotaTracker quota, CallPacer pacer, BudgetGuard budget, LlmCallLogger callLogger,
            LlmProperties properties, Sleeper sleeper, List<LlmClient> clients) {
        this.registry = registry;
        this.assignments = assignments;
        this.prompts = prompts;
        this.extractor = extractor;
        this.quota = quota;
        this.pacer = pacer;
        this.budget = budget;
        this.callLogger = callLogger;
        this.properties = properties;
        this.sleeper = sleeper;
        clients.forEach(client -> this.clients.put(client.type(), client));
    }

    /**
     * Calls the model assigned to {@code purpose} (EXTRACT or SCORE) with a rendered prompt and
     * parses the answer into {@code responseType}.
     */
    public <T> LlmOutcome<T> call(LlmPurpose purpose, String promptId, Map<String, String> variables, Long jobId,
            Class<T> responseType) {
        RenderedPrompt prompt = prompts.get(promptId).render(variables);
        if (properties.forced()) {
            return callModels(purpose, List.of(registry.get(properties.forceModel())), null, prompt, jobId, responseType);
        }
        Assignment assignment = assignments.current(purpose);
        return callModels(purpose, candidates(assignment), assignment, prompt, jobId, responseType);
    }

    /** Calls one given model without fallback (eval runs, admin "Test" button). */
    public <T> LlmOutcome<T> callModel(String modelId, LlmPurpose purpose, String promptId,
            Map<String, String> variables, Long jobId, Class<T> responseType) {
        RenderedPrompt prompt = prompts.get(promptId).render(variables);
        return callModels(purpose, List.of(registry.get(modelId)), null, prompt, jobId, responseType);
    }

    /**
     * Admin "Test" (T-022): one call to the model even when its status is ERROR, EXHAUSTED or
     * RATE_LIMITED, so that a success clears it. A missing key, the budget guard and the paid
     * consent (ADR-0009) still apply; the call is logged with purpose TEST.
     */
    public <T> LlmOutcome<T> probe(String modelId, String promptId, Class<T> responseType) {
        RenderedPrompt prompt = prompts.get(promptId).render(Map.of());
        RegisteredModel model = registry.get(modelId);
        if (model.missingKey()) {
            quota.markMissingKey(model);
            return new LlmUnavailable<>(LlmPurpose.TEST, LlmUnavailable.Reason.NO_USABLE_MODEL, "missing key");
        }
        Optional<LlmUnavailable.Reason> stop = budget.check(model.tier());
        if (stop.isPresent()) {
            return new LlmUnavailable<>(LlmPurpose.TEST, stop.get(), "budget guard");
        }
        return this.<T>attempt(LlmPurpose.TEST, model, prompt, null, responseType)
                .orElseGet(() -> new LlmUnavailable<>(LlmPurpose.TEST, LlmUnavailable.Reason.NO_USABLE_MODEL,
                        model.id() + ": " + quota.status(model).status()));
    }

    /** The model that would serve {@code purpose} now (EXTRACT or SCORE), without calling it. */
    public ModelChoice resolve(LlmPurpose purpose) {
        String modelId = properties.forced() ? properties.forceModel() : assignments.current(purpose).modelId();
        RegisteredModel model = registry.get(modelId);
        return new ModelChoice(model.id(), model.label(), model.tier(), properties.allowPaidCalls());
    }

    /**
     * Why no model could serve {@code purpose} now, checked without a call (pre-check of the bulk
     * actions): empty when the assigned model, or a fallback the router may use, is usable.
     */
    public Optional<LlmUnavailable.Reason> availability(LlmPurpose purpose) {
        List<RegisteredModel> candidates = properties.forced()
                ? List.of(registry.get(properties.forceModel()))
                : candidates(assignments.current(purpose));
        List<String> skipped = new ArrayList<>();
        for (RegisteredModel model : candidates) {
            Optional<String> unusable = unusable(model);
            if (unusable.isEmpty()) {
                return Optional.empty();
            }
            skipped.add(model.id() + ": " + unusable.get());
        }
        return Optional.of(unavailableReason(skipped));
    }

    /** Assigned model first, then allowed fallbacks when auto-fallback is on. */
    List<RegisteredModel> candidates(Assignment assignment) {
        List<RegisteredModel> result = new ArrayList<>();
        registry.find(assignment.modelId()).ifPresent(result::add);
        if (assignment.autoFallback()) {
            for (String id : assignment.fallbacks()) {
                registry.find(id)
                        .filter(model -> model.tier() != ModelTier.PAID || assignment.allowPaid())
                        .filter(model -> !result.contains(model))
                        .ifPresent(result::add);
            }
        }
        return result;
    }

    private <T> LlmOutcome<T> callModels(LlmPurpose purpose, List<RegisteredModel> candidates, Assignment assignment,
            RenderedPrompt prompt, Long jobId, Class<T> responseType) {
        List<String> skipped = new ArrayList<>();
        for (RegisteredModel model : candidates) {
            Optional<LlmUnavailable.Reason> runStop = budget.checkRun();
            if (runStop.isPresent()) {
                return new LlmUnavailable<>(purpose, runStop.get(), "max-calls-per-run " + properties.maxCallsPerRun());
            }
            Optional<String> unusable = unusable(model);
            if (unusable.isPresent()) {
                skipped.add(model.id() + ": " + unusable.get());
                continue;
            }
            Optional<LlmOutcome<T>> outcome = attempt(purpose, model, prompt, jobId, responseType);
            if (outcome.isPresent()) {
                if (assignment != null && outcome.get() instanceof LlmResult<T>
                        && !model.id().equals(assignment.modelId())) {
                    assignments.switchToFallback(assignment, model.id());
                }
                return outcome.get();
            }
            skipped.add(model.id() + ": " + quota.status(model).status());
        }
        return new LlmUnavailable<>(purpose, unavailableReason(skipped),
                skipped.isEmpty() ? "no model configured" : String.join("; ", skipped));
    }

    private static LlmUnavailable.Reason unavailableReason(List<String> skipped) {
        if (!skipped.isEmpty() && skipped.stream().allMatch(s -> s.endsWith(PAID_MARK))) {
            return LlmUnavailable.Reason.PAID_NOT_ALLOWED;
        }
        if (!skipped.isEmpty() && skipped.stream().allMatch(s -> s.endsWith(BUDGET_MARK))) {
            return LlmUnavailable.Reason.MONTHLY_BUDGET;
        }
        return LlmUnavailable.Reason.NO_USABLE_MODEL;
    }

    private Optional<String> unusable(RegisteredModel model) {
        if (model.missingKey()) {
            quota.markMissingKey(model);
            return Optional.of("missing key");
        }
        ModelState state = quota.status(model);
        if (!state.status().usable()) {
            return Optional.of(state.status() + (state.until() != null ? " until " + state.until() : ""));
        }
        Optional<LlmUnavailable.Reason> stop = budget.check(model.tier());
        if (stop.filter(r -> r == LlmUnavailable.Reason.PAID_NOT_ALLOWED).isPresent()) {
            return Optional.of(PAID_MARK);
        }
        if (stop.filter(r -> r == LlmUnavailable.Reason.MONTHLY_BUDGET).isPresent()) {
            return Optional.of(BUDGET_MARK);
        }
        return Optional.empty();
    }

    /**
     * The attempt loop for one model. Empty = the model became unusable (quota/auth), so the
     * caller tries the next candidate.
     */
    private <T> Optional<LlmOutcome<T>> attempt(LlmPurpose purpose, RegisteredModel model, RenderedPrompt prompt,
            Long jobId, Class<T> responseType) {
        LlmClient client = clients.get(model.providerType());
        if (client == null) {
            throw new IllegalStateException("no LlmClient for provider type " + model.providerType());
        }
        List<ChatMessage> messages = new ArrayList<>(List.of(ChatMessage.user(prompt.user())));
        boolean corrected = false;
        boolean waitedForRateLimit = false;
        int transientRetries = 0;
        int httpCalls = 0;
        while (true) {
            Optional<LlmUnavailable.Reason> stop = budget.check(model.tier());
            if (stop.isPresent()) {
                return Optional.of(new LlmUnavailable<>(purpose, stop.get(), "budget guard"));
            }
            LlmRequest request = new LlmRequest(model, purpose, jobId, prompt.promptId(), prompt.system(), messages,
                    model.maxOutputTokens(prompt.maxOutputTokens()), prompt.temperature(), timeout(model));
            pace(model);
            budget.recordCall();
            httpCalls++;
            LlmResponse response;
            try {
                response = client.send(request);
            } catch (LlmCallException e) {
                callLogger.failed(request, e);
                switch (e.kind()) {
                    case SERVER, TIMEOUT -> {
                        if (transientRetries < BACKOFF.size()) {
                            sleep(BACKOFF.get(transientRetries++));
                            continue;
                        }
                        return Optional.of(new LlmFailed<>(purpose, model.id(), e.kind(), e.getMessage()));
                    }
                    case RATE_LIMIT -> {
                        if (!waitedForRateLimit && e.retryAfter() != null
                                && e.retryAfter().compareTo(MAX_INLINE_RETRY_AFTER) <= 0) {
                            waitedForRateLimit = true;
                            sleep(e.retryAfter());
                            continue;
                        }
                        quota.onFailure(model, e);
                        return Optional.empty();
                    }
                    case QUOTA_EXHAUSTED, AUTH, MODEL_NOT_FOUND -> {
                        quota.onFailure(model, e);
                        return Optional.empty();
                    }
                    case INVALID_OUTPUT -> {
                        if (!corrected) {
                            corrected = true;
                            continue;
                        }
                        return Optional.of(new LlmFailed<>(purpose, model.id(), e.kind(), e.getMessage()));
                    }
                    default -> {
                        return Optional.of(new LlmFailed<>(purpose, model.id(), e.kind(), e.getMessage()));
                    }
                }
            }
            try {
                T value = extractor.extract(response.text(), responseType);
                callLogger.answered(request, response, null);
                quota.onSuccess(model, response.rateLimit());
                return Optional.of(new LlmResult<>(value, response.text(), model.id(), model.modelId(),
                        prompt.promptId(), response.inputTokens(), response.outputTokens(), response.latencyMs(),
                        httpCalls));
            } catch (InvalidLlmOutputException e) {
                callLogger.answered(request, response, e.getMessage());
                quota.onSuccess(model, response.rateLimit());
                if (corrected) {
                    return Optional.of(new LlmFailed<>(purpose, model.id(), LlmErrorKind.INVALID_OUTPUT, e.getMessage()));
                }
                corrected = true;
                messages.add(ChatMessage.assistant(response.text()));
                messages.add(ChatMessage.user(correction(e.getMessage())));
            }
        }
    }

    static String correction(String validationError) {
        return "Your previous answer was rejected: " + validationError
                + "\nAnswer again with only the corrected JSON object, nothing else.";
    }

    private Duration timeout(RegisteredModel model) {
        return model.tier() == ModelTier.LOCAL ? properties.timeoutLocal() : properties.timeout();
    }

    private void pace(RegisteredModel model) {
        try {
            pacer.acquire(model);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while pacing LLM calls", e);
        }
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to retry an LLM call", e);
        }
    }
}
