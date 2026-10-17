package io.github.quanghung304.jobhunter.llm.persistence;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
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
 * One LLM call, for cost tracking and the budget guard.
 *
 * <p>Append-only log row: created complete, never updated.
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

    @Column(name = "model", nullable = false, updatable = false)
    private String model;

    @Column(name = "prompt_version", nullable = false, updatable = false)
    private String promptVersion;

    @Column(name = "input_tokens", nullable = false, updatable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false, updatable = false)
    private int outputTokens;

    @Column(name = "cost_usd", nullable = false, updatable = false)
    private BigDecimal costUsd;

    @Column(name = "latency_ms", updatable = false)
    private Integer latencyMs;

    @Column(name = "success", nullable = false, updatable = false)
    private boolean success;

    @Column(name = "error", updatable = false)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LlmCall() {
        // for JPA
    }

    public LlmCall(
            LlmPurpose purpose,
            Long jobId,
            String model,
            String promptVersion,
            int inputTokens,
            int outputTokens,
            BigDecimal costUsd,
            Integer latencyMs,
            boolean success,
            String error,
            Instant createdAt) {
        this.purpose = purpose;
        this.jobId = jobId;
        this.model = model;
        this.promptVersion = promptVersion;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.costUsd = costUsd;
        this.latencyMs = latencyMs;
        this.success = success;
        this.error = error;
        this.createdAt = createdAt;
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

    public String getModel() {
        return model;
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

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
