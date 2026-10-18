package io.github.quanghung304.jobhunter.llm.persistence;

import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One LLM HTTP attempt, for cost tracking, quota counting and the budget guard.
 *
 * <p>Append-only log row: created complete, never updated. Build with {@link #builder()}.
 */
@Entity
@Table(name = "llm_call")
public class LlmCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false)
    private LlmPurpose purpose;

    @Column(name = "job_id", updatable = false)
    private Long jobId;

    @Column(name = "model_id", nullable = false, updatable = false)
    private String modelId;

    @Column(name = "provider_model", nullable = false, updatable = false)
    private String providerModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", updatable = false)
    private ModelTier tier;

    @Column(name = "prompt_version", nullable = false, updatable = false)
    private String promptVersion;

    @Column(name = "input_tokens", nullable = false, updatable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false, updatable = false)
    private int outputTokens;

    @Column(name = "cost_usd", updatable = false)
    private BigDecimal costUsd;

    @Column(name = "latency_ms", updatable = false)
    private Integer latencyMs;

    @Column(name = "success", nullable = false, updatable = false)
    private boolean success;

    @Column(name = "http_status", updatable = false)
    private Integer httpStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_kind", updatable = false)
    private LlmErrorKind errorKind;

    @Column(name = "error", updatable = false)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LlmCall() {
        // for JPA
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() {
        return id;
    }

    public LlmPurpose getPurpose() {
        return purpose;
    }

    public Long getJobId() {
        return jobId;
    }

    public String getModelId() {
        return modelId;
    }

    public String getProviderModel() {
        return providerModel;
    }

    public ModelTier getTier() {
        return tier;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public BigDecimal getCostUsd() {
        return costUsd;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public LlmErrorKind getErrorKind() {
        return errorKind;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static final class Builder {
        private final LlmCall call = new LlmCall();

        public Builder purpose(LlmPurpose purpose) {
            call.purpose = purpose;
            return this;
        }

        public Builder jobId(Long jobId) {
            call.jobId = jobId;
            return this;
        }

        public Builder model(String modelId, String providerModel, ModelTier tier) {
            call.modelId = modelId;
            call.providerModel = providerModel;
            call.tier = tier;
            return this;
        }

        public Builder promptVersion(String promptVersion) {
            call.promptVersion = promptVersion;
            return this;
        }

        public Builder tokens(int inputTokens, int outputTokens) {
            call.inputTokens = inputTokens;
            call.outputTokens = outputTokens;
            return this;
        }

        public Builder costUsd(BigDecimal costUsd) {
            call.costUsd = costUsd;
            return this;
        }

        public Builder latencyMs(Integer latencyMs) {
            call.latencyMs = latencyMs;
            return this;
        }

        public Builder success(boolean success) {
            call.success = success;
            return this;
        }

        public Builder failure(Integer httpStatus, LlmErrorKind errorKind, String error) {
            call.success = false;
            call.httpStatus = httpStatus;
            call.errorKind = errorKind;
            call.error = error;
            return this;
        }

        public Builder httpStatus(Integer httpStatus) {
            call.httpStatus = httpStatus;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            call.createdAt = createdAt;
            return this;
        }

        public LlmCall build() {
            return call;
        }
    }
}
