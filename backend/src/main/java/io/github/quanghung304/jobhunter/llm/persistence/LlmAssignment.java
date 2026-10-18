package io.github.quanghung304.jobhunter.llm.persistence;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Which model serves a purpose (ADR-0008); one row per EXTRACT/SCORE. */
@Entity
@Table(name = "llm_assignment")
public class LlmAssignment {

    public static final String BY_CONFIG = "config";
    public static final String BY_USER = "user";

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose")
    private LlmPurpose purpose;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "fallbacks", nullable = false)
    private List<String> fallbacks = new ArrayList<>();

    @Column(name = "auto_fallback", nullable = false)
    private boolean autoFallback;

    @Column(name = "allow_paid", nullable = false)
    private boolean allowPaid;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected LlmAssignment() {
        // for JPA
    }

    public LlmAssignment(LlmPurpose purpose, String modelId, List<String> fallbacks, boolean autoFallback,
            boolean allowPaid, Instant updatedAt, String updatedBy) {
        this.purpose = purpose;
        this.modelId = modelId;
        this.fallbacks = new ArrayList<>(fallbacks);
        this.autoFallback = autoFallback;
        this.allowPaid = allowPaid;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public LlmPurpose getPurpose() {
        return purpose;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    /** Unmodifiable. */
    public List<String> getFallbacks() {
        return List.copyOf(fallbacks);
    }

    public void setFallbacks(List<String> fallbacks) {
        this.fallbacks = new ArrayList<>(fallbacks);
    }

    public boolean isAutoFallback() {
        return autoFallback;
    }

    public void setAutoFallback(boolean autoFallback) {
        this.autoFallback = autoFallback;
    }

    public boolean isAllowPaid() {
        return allowPaid;
    }

    public void setAllowPaid(boolean allowPaid) {
        this.allowPaid = allowPaid;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
