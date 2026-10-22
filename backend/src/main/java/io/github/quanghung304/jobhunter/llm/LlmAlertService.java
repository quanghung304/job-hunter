package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentEvent;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentEventRepository;
import io.github.quanghung304.jobhunter.llm.quota.ModelState;
import io.github.quanghung304.jobhunter.llm.quota.QuotaTracker;
import io.github.quanghung304.jobhunter.llm.registry.ModelRegistry;
import io.github.quanghung304.jobhunter.llm.registry.ProviderType;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The LLM alerts of the web banner and of {@code LLM_PAUSED} answers (ADR-0008): one per purpose
 * whose model is not AVAILABLE, plus one per purpose that auto-fallback moved away from a model
 * that is usable again (suggesting to switch back). Computed on each request from
 * {@code llm_model_state} and the assignments; nothing is stored.
 */
@Service
public class LlmAlertService {

    private final AssignmentService assignments;
    private final LlmAssignmentEventRepository events;
    private final ModelRegistry registry;
    private final QuotaTracker quota;
    private final LlmRouter router;
    private final LlmProperties properties;

    public LlmAlertService(AssignmentService assignments, LlmAssignmentEventRepository events, ModelRegistry registry,
            QuotaTracker quota, LlmRouter router, LlmProperties properties) {
        this.assignments = assignments;
        this.events = events;
        this.registry = registry;
        this.quota = quota;
        this.router = router;
        this.properties = properties;
    }

    /** Alerts of EXTRACT and SCORE, in that order. */
    public List<LlmAlert> alerts() {
        List<LlmAlert> result = new ArrayList<>();
        for (LlmPurpose purpose : ModelRegistry.ASSIGNABLE) {
            alert(purpose).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    /** The purposes this model serves now (assignment, or {@code app.llm.force-model}). */
    public List<LlmPurpose> purposesServedBy(String modelId) {
        return ModelRegistry.ASSIGNABLE.stream()
                .filter(purpose -> modelId.equals(
                        properties.forced() ? properties.forceModel() : assignments.current(purpose).modelId()))
                .toList();
    }

    /** The current status of a registry model; empty when unknown. */
    public Optional<ModelStatus> status(String modelId) {
        return registry.find(modelId).map(model -> quota.status(model).status());
    }

    /** The UI label of a registry model, or the id itself when unknown. */
    public String label(String modelId) {
        return registry.find(modelId).map(RegisteredModel::label).orElse(modelId);
    }

    public Optional<LlmAlert> alert(LlmPurpose purpose) {
        Assignment assignment = properties.forced() ? null : assignments.current(purpose);
        String modelId = assignment == null ? properties.forceModel() : assignment.modelId();
        RegisteredModel model = registry.get(modelId);
        ModelState state = quota.status(model);
        List<String> fallbacks = assignment == null ? List.of() : assignment.fallbacks();
        if (state.status() != ModelStatus.AVAILABLE) {
            boolean paused = router.availability(purpose).isPresent();
            return Optional.of(new LlmAlert(purpose, model.id(), model.label(), state.status(), state.detail(),
                    state.until(), paused, null, suggestions(model.id(), fallbacks)));
        }
        if (assignment != null && AssignmentService.BY_AUTO_FALLBACK.equals(assignment.updatedBy())) {
            return switchBack(purpose, model, state, fallbacks);
        }
        return Optional.empty();
    }

    /** After auto-fallback: suggest the model it left once that one is usable again. */
    private Optional<LlmAlert> switchBack(LlmPurpose purpose, RegisteredModel current, ModelState state,
            List<String> fallbacks) {
        Optional<RegisteredModel> previous = events.findFirstByPurposeOrderByCreatedAtDescIdDesc(purpose)
                .filter(event -> event.getReason() == AssignmentChangeReason.AUTO_FALLBACK)
                .map(LlmAssignmentEvent::getFromModelId)
                .flatMap(registry::find);
        if (previous.isEmpty()) {
            return Optional.empty();
        }
        ModelState previousState = quota.status(previous.get());
        if (previous.get().missingKey() || !previousState.status().usable()) {
            return Optional.empty();
        }
        var suggestion = new LlmAlert.Suggestion(previous.get().id(), previous.get().label(), previous.get().tier(),
                previousState.status());
        String detail = "Switched automatically from " + previous.get().label() + ", which is available again";
        return Optional.of(new LlmAlert(purpose, current.id(), current.label(), state.status(), detail, null, false,
                previous.get().id(), List.of(suggestion)));
    }

    /**
     * Models to switch to: fallbacks in their order, then the other registry models with FREE and
     * LOCAL before PAID. ERROR (incl. missing key) and EXHAUSTED models are left out, and so is the
     * test-only fake provider unless the current model is fake as well.
     */
    List<LlmAlert.Suggestion> suggestions(String currentModelId, List<String> fallbacks) {
        Set<RegisteredModel> ordered = new LinkedHashSet<>();
        fallbacks.forEach(id -> registry.find(id).ifPresent(ordered::add));
        registry.all().stream()
                .sorted(Comparator.comparing((RegisteredModel m) -> m.tier() == ModelTier.PAID))
                .forEach(ordered::add);
        // The fake model is offered only where the current model is fake too (tests, force-model=fake).
        boolean fakeWorld = registry.find(currentModelId).map(m -> m.providerType() == ProviderType.FAKE).orElse(false);
        List<LlmAlert.Suggestion> result = new ArrayList<>();
        for (RegisteredModel model : ordered) {
            if (model.id().equals(currentModelId) || model.missingKey()
                    || (model.providerType() == ProviderType.FAKE && !fakeWorld)) {
                continue;
            }
            ModelStatus status = quota.status(model).status();
            if (status == ModelStatus.ERROR || status == ModelStatus.EXHAUSTED) {
                continue;
            }
            result.add(new LlmAlert.Suggestion(model.id(), model.label(), model.tier(), status));
        }
        return result;
    }
}
