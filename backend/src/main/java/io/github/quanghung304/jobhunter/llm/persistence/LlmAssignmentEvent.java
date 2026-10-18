package io.github.quanghung304.jobhunter.llm.persistence;

import io.github.quanghung304.jobhunter.llm.AssignmentChangeReason;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** History of model switches per purpose. Append-only. */
@Entity
@Table(name = "llm_assignment_event")
public class LlmAssignmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false)
    private LlmPurpose purpose;

    @Column(name = "from_model_id", updatable = false)
    private String fromModelId;

    @Column(name = "to_model_id", nullable = false, updatable = false)
    private String toModelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false)
    private AssignmentChangeReason reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LlmAssignmentEvent() {
        // for JPA
    }

    public LlmAssignmentEvent(LlmPurpose purpose, String fromModelId, String toModelId, AssignmentChangeReason reason,
            Instant createdAt) {
        this.purpose = purpose;
        this.fromModelId = fromModelId;
        this.toModelId = toModelId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public LlmPurpose getPurpose() {
        return purpose;
    }

    public String getFromModelId() {
        return fromModelId;
    }

    public String getToModelId() {
        return toModelId;
    }

    public AssignmentChangeReason getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
