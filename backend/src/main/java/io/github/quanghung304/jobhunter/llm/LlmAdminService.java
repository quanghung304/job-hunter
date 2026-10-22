package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.common.JobHunterException;
import io.github.quanghung304.jobhunter.llm.LlmUsageService.ModelUsage;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentEventRepository;
import io.github.quanghung304.jobhunter.llm.quota.ModelState;
import io.github.quanghung304.jobhunter.llm.quota.QuotaTracker;
import io.github.quanghung304.jobhunter.llm.registry.ModelRegistry;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The Admin "LLM models" card (ADR-0008): assignments, models with status and today's usage,
 * switch history; the owner's switch of a purpose's model and the "Test" of one model. Actions
 * that may start PAID calls need the owner's consent and an explicit confirmation (ADR-0009).
 * A switch makes no LLM call (ADR-0010); it only un-pauses the purpose's owner actions.
 */
@Service
public class LlmAdminService {

    /** Prompt of the "Test" call (docs/ai/prompts/test-v1.md). */
    public static final String TEST_PROMPT = "test-v1";
    public static final String PAID_WARNING = "PAID model: counts against the monthly budget";

    private final ModelRegistry registry;
    private final AssignmentService assignments;
    private final LlmAssignmentEventRepository events;
    private final QuotaTracker quota;
    private final LlmUsageService usage;
    private final LlmRouter router;
    private final BudgetGuard budget;
    private final LlmProperties properties;
    private final Clock clock;

    public LlmAdminService(ModelRegistry registry, AssignmentService assignments, LlmAssignmentEventRepository events,
            QuotaTracker quota, LlmUsageService usage, LlmRouter router, BudgetGuard budget, LlmProperties properties,
            Clock clock) {
        this.registry = registry;
        this.assignments = assignments;
        this.events = events;
        this.quota = quota;
        this.usage = usage;
        this.router = router;
        this.budget = budget;
        this.properties = properties;
        this.clock = clock;
    }

    public record AssignmentView(Assignment assignment, Instant updatedAt) {
    }

    /** One registry model; {@code usageToday} counts calls since 00:00 UTC. */
    public record ModelView(RegisteredModel model, ModelState state, ModelUsage usageToday,
            List<LlmPurpose> assignedTo) {
    }

    public record HistoryEntry(LlmPurpose purpose, String fromModelId, String toModelId,
            AssignmentChangeReason reason, Instant at) {
    }

    public record Overview(List<AssignmentView> assignments, List<ModelView> models, List<HistoryEntry> history,
            boolean paidCallsAllowed) {
    }

    /** @param warning set for a PAID model */
    public record SwitchResult(AssignmentView assignment, String warning) {
    }

    /** @param status the model's status after the call; {@code detail} says why it failed */
    public record TestResult(boolean ok, long latencyMs, ModelStatus status, String detail) {
    }

    /** The answer of the test prompt. */
    record ProbeAnswer(Boolean ok) {
    }

    public Overview overview() {
        List<AssignmentView> views = new ArrayList<>();
        for (LlmPurpose purpose : ModelRegistry.ASSIGNABLE) {
            views.add(new AssignmentView(assignments.current(purpose), assignments.updatedAt(purpose)));
        }
        Instant dayStart = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        Map<String, ModelUsage> today = usage.usageByModel(dayStart);
        List<ModelView> models = new ArrayList<>();
        for (RegisteredModel model : registry.all()) {
            List<LlmPurpose> assignedTo = views.stream()
                    .filter(view -> view.assignment().modelId().equals(model.id()))
                    .map(view -> view.assignment().purpose())
                    .toList();
            models.add(new ModelView(model, quota.status(model), today.getOrDefault(model.id(), ModelUsage.NONE),
                    assignedTo));
        }
        List<HistoryEntry> history = events.findTop10ByOrderByCreatedAtDescIdDesc().stream()
                .map(e -> new HistoryEntry(e.getPurpose(), e.getFromModelId(), e.getToModelId(), e.getReason(),
                        e.getCreatedAt()))
                .toList();
        return new Overview(List.copyOf(views), List.copyOf(models), history, properties.allowPaidCalls());
    }

    /**
     * The owner's switch of a purpose (null values keep the current setting).
     *
     * @throws UnknownModelException              a model id not in the registry
     * @throws ModelUnusableException             the model is in ERROR (bad or missing key, removed model)
     * @throws PaidCallsDisabledException         it would allow PAID calls while paid calls are off
     * @throws PaidConfirmationRequiredException  it would allow PAID calls and {@code confirmPaid} is not true
     */
    public SwitchResult switchModel(LlmPurpose purpose, String modelId, List<String> fallbacks, Boolean autoFallback,
            Boolean allowPaid, boolean confirmPaid) {
        if (!ModelRegistry.ASSIGNABLE.contains(purpose)) {
            throw new JobHunterException("BAD_REQUEST", "Only EXTRACT and SCORE have a model assignment");
        }
        RegisteredModel model = registry.find(modelId).orElseThrow(() -> new UnknownModelException(modelId, false));
        Assignment before = assignments.current(purpose);
        List<String> newFallbacks = fallbacks == null ? before.fallbacks() : List.copyOf(fallbacks);
        newFallbacks.forEach(id -> registry.find(id).orElseThrow(() -> new UnknownModelException(id, false)));
        boolean newAllowPaid = allowPaid == null ? before.allowPaid() : allowPaid;
        boolean newAutoFallback = autoFallback == null ? before.autoFallback() : autoFallback;

        ModelState state = quota.status(model);
        if (model.missingKey() || state.status() == ModelStatus.ERROR) {
            throw new ModelUnusableException(model.label(),
                    model.missingKey() ? "the provider key is missing in .env" : state.detail());
        }
        boolean newPaidModel = model.tier() == ModelTier.PAID && !model.id().equals(before.modelId());
        boolean paidFallbackTurnedOn = newAllowPaid && !before.allowPaid();
        if (newPaidModel || paidFallbackTurnedOn) {
            RegisteredModel paid = newPaidModel ? model : firstPaid(newFallbacks).orElse(model);
            requirePaidConsent(paid, purpose, confirmPaid);
        }
        Assignment updated = assignments.update(purpose, model.id(), newFallbacks, newAutoFallback, newAllowPaid);
        return new SwitchResult(new AssignmentView(updated, assignments.updatedAt(purpose)),
                model.tier() == ModelTier.PAID ? PAID_WARNING : null);
    }

    /**
     * Admin "Test": one tiny call to the model ({@link #TEST_PROMPT}), also when its status is
     * ERROR or EXHAUSTED; a success makes it AVAILABLE again.
     */
    public TestResult test(String modelId, boolean confirmPaid) {
        RegisteredModel model = registry.find(modelId).orElseThrow(() -> new UnknownModelException(modelId, true));
        if (model.tier() == ModelTier.PAID) {
            requirePaidConsent(model, LlmPurpose.TEST, confirmPaid);
        }
        budget.beginRun();
        long started = System.nanoTime();
        LlmOutcome<ProbeAnswer> outcome = router.probe(model.id(), TEST_PROMPT, ProbeAnswer.class);
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        ModelStatus status = quota.status(model).status();
        return switch (outcome) {
            case LlmResult<ProbeAnswer> result -> new TestResult(Boolean.TRUE.equals(result.value().ok()),
                    result.latencyMs() > 0 ? result.latencyMs() : elapsed, status,
                    Boolean.TRUE.equals(result.value().ok()) ? null : "unexpected answer: " + result.rawText());
            case LlmFailed<ProbeAnswer> failed -> new TestResult(false, elapsed, status,
                    failed.kind() + ": " + failed.message());
            case LlmUnavailable<ProbeAnswer> unavailable -> new TestResult(false, elapsed, status,
                    unavailable.reason() + ": " + unavailable.detail());
        };
    }

    private Optional<RegisteredModel> firstPaid(List<String> modelIds) {
        return modelIds.stream()
                .map(registry::find)
                .flatMap(Optional::stream)
                .filter(m -> m.tier() == ModelTier.PAID)
                .findFirst();
    }

    private void requirePaidConsent(RegisteredModel model, LlmPurpose purpose, boolean confirmPaid) {
        var choice = new ModelChoice(model.id(), model.label(), model.tier(), properties.allowPaidCalls());
        if (choice.blockedByConsent()) {
            throw new PaidCallsDisabledException(choice);
        }
        if (!confirmPaid) {
            BigDecimal perCall = budget.estimateCostUsd(choice, purpose, 1).orElse(null);
            throw new PaidConfirmationRequiredException(choice, perCall);
        }
    }
}
