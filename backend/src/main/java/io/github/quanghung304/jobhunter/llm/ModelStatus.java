package io.github.quanghung304.jobhunter.llm;

/** Quota/health status of a model (data-model {@code llm_model_state}). */
public enum ModelStatus {
    AVAILABLE,
    NEAR_LIMIT,
    RATE_LIMITED,
    EXHAUSTED,
    ERROR;

    /** Whether a call may be sent to a model in this status (before {@code until} passes). */
    public boolean usable() {
        return this == AVAILABLE || this == NEAR_LIMIT;
    }
}
