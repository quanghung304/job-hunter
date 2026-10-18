package io.github.quanghung304.jobhunter.llm.persistence;

import io.github.quanghung304.jobhunter.llm.ModelStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Last known quota/health status of one registry model (data-model {@code llm_model_state}). */
@Entity
@Table(name = "llm_model_state")
public class LlmModelState {

    @Id
    @Column(name = "model_id")
    private String modelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ModelStatus status;

    @Column(name = "until")
    private Instant until;

    @Column(name = "detail")
    private String detail;

    @Column(name = "remaining_requests")
    private Integer remainingRequests;

    @Column(name = "remaining_tokens")
    private Integer remainingTokens;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LlmModelState() {
        // for JPA
    }

    public LlmModelState(String modelId, ModelStatus status, Instant updatedAt) {
        this.modelId = modelId;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public String getModelId() {
        return modelId;
    }

    public ModelStatus getStatus() {
        return status;
    }

    public void setStatus(ModelStatus status) {
        this.status = status;
    }

    public Instant getUntil() {
        return until;
    }

    public void setUntil(Instant until) {
        this.until = until;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Integer getRemainingRequests() {
        return remainingRequests;
    }

    public void setRemainingRequests(Integer remainingRequests) {
        this.remainingRequests = remainingRequests;
    }

    public Integer getRemainingTokens() {
        return remainingTokens;
    }

    public void setRemainingTokens(Integer remainingTokens) {
        this.remainingTokens = remainingTokens;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
