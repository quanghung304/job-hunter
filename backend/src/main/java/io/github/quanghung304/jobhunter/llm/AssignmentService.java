package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignment;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentEvent;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentEventRepository;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentRepository;
import io.github.quanghung304.jobhunter.llm.registry.LlmRegistryFile;
import io.github.quanghung304.jobhunter.llm.registry.ModelRegistry;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the assignment of a purpose from {@code llm_assignment} on every call, so a change in
 * the DB applies to the next call without restart. A missing row is seeded from
 * {@code llm.yaml} {@code defaults} ({@code updated_by = config}).
 *
 * <p>When the router falls back automatically, the assignment is switched to the fallback
 * ({@code updated_by = auto-fallback}) and an {@code AUTO_FALLBACK} event is written, so the UI
 * shows what really serves the purpose. It is not switched back automatically; the alert suggests
 * it (T-022). Every model change publishes {@link LlmAssignmentSwitched}.
 */
@Service
public class AssignmentService {

    public static final String BY_AUTO_FALLBACK = "auto-fallback";
    public static final String BY_USER = "user";

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private final LlmAssignmentRepository assignments;
    private final LlmAssignmentEventRepository events;
    private final ModelRegistry registry;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public AssignmentService(LlmAssignmentRepository assignments, LlmAssignmentEventRepository events,
            ModelRegistry registry, ApplicationEventPublisher publisher, Clock clock) {
        this.assignments = assignments;
        this.events = events;
        this.registry = registry;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** The assignment of EXTRACT or SCORE, seeded from the registry defaults when missing. */
    public Assignment current(LlmPurpose purpose) {
        if (!ModelRegistry.ASSIGNABLE.contains(purpose)) {
            throw new IllegalArgumentException("purpose " + purpose + " has no assignment");
        }
        LlmAssignment row = assignments.findById(purpose).orElseGet(() -> seed(purpose));
        return new Assignment(purpose, row.getModelId(), row.getFallbacks(), row.isAutoFallback(), row.isAllowPaid(),
                row.getUpdatedBy());
    }

    /** The router served the purpose with a fallback: make it the assignment and record why. */
    public void switchToFallback(Assignment assignment, String fallbackModelId) {
        LlmAssignment row = assignments.findById(assignment.purpose()).orElseThrow();
        if (row.getModelId().equals(fallbackModelId)) {
            return;
        }
        String from = row.getModelId();
        row.setModelId(fallbackModelId);
        row.setUpdatedBy(BY_AUTO_FALLBACK);
        row.setUpdatedAt(clock.instant());
        assignments.save(row);
        events.save(new LlmAssignmentEvent(assignment.purpose(), from, fallbackModelId,
                AssignmentChangeReason.AUTO_FALLBACK, clock.instant()));
        log.info("llm purpose={} switched {} -> {} (auto-fallback)", assignment.purpose(), from, fallbackModelId);
        publisher.publishEvent(new LlmAssignmentSwitched(assignment.purpose(), from, fallbackModelId,
                AssignmentChangeReason.AUTO_FALLBACK));
    }

    /**
     * The owner's change from the Admin page ({@code updated_by = user}); a new model writes a
     * {@code USER} event. Validation (known model, usable, paid consent) is the caller's job.
     */
    @Transactional
    public Assignment update(LlmPurpose purpose, String modelId, List<String> fallbacks, boolean autoFallback,
            boolean allowPaid) {
        Assignment before = current(purpose);
        LlmAssignment row = assignments.findById(purpose).orElseThrow();
        row.setModelId(modelId);
        row.setFallbacks(fallbacks);
        row.setAutoFallback(autoFallback);
        row.setAllowPaid(allowPaid);
        row.setUpdatedBy(BY_USER);
        row.setUpdatedAt(clock.instant());
        assignments.save(row);
        if (!before.modelId().equals(modelId)) {
            events.save(new LlmAssignmentEvent(purpose, before.modelId(), modelId, AssignmentChangeReason.USER,
                    clock.instant()));
            log.info("llm purpose={} switched {} -> {} (user)", purpose, before.modelId(), modelId);
            publisher.publishEvent(new LlmAssignmentSwitched(purpose, before.modelId(), modelId,
                    AssignmentChangeReason.USER));
        }
        return current(purpose);
    }

    /** When the assignment last changed. */
    public java.time.Instant updatedAt(LlmPurpose purpose) {
        current(purpose);
        return assignments.findById(purpose).orElseThrow().getUpdatedAt();
    }

    private LlmAssignment seed(LlmPurpose purpose) {
        LlmRegistryFile.PurposeDefaults defaults = registry.defaults(purpose);
        log.info("llm purpose={} has no assignment; seeding model={} from llm.yaml defaults", purpose, defaults.model());
        return assignments.save(new LlmAssignment(purpose, defaults.model(), defaults.fallbacks(),
                defaults.autoFallback(), defaults.allowPaid(), clock.instant(), LlmAssignment.BY_CONFIG));
    }
}
