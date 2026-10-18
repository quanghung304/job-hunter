package io.github.quanghung304.jobhunter.llm;

/** Why an LLM HTTP attempt failed ({@code llm_call.error_kind}). */
public enum LlmErrorKind {
    /** 429 with a short reset (per-minute limit). */
    RATE_LIMIT,
    /** 429 for a daily/monthly quota, or a reset longer than 1 h. */
    QUOTA_EXHAUSTED,
    /** 401/403 or a missing key. */
    AUTH,
    /** 404: unknown model id. */
    MODEL_NOT_FOUND,
    TIMEOUT,
    /** 5xx or a network error. */
    SERVER,
    /** Other 4xx: the request itself is wrong (not retried). */
    BAD_REQUEST,
    /** The answer is not a valid JSON object for the expected schema, or was refused/truncated. */
    INVALID_OUTPUT;

    /** Quota and provider-health errors are not job failures (scoring-spec §6). */
    public boolean affectsModelStatus() {
        return this == RATE_LIMIT || this == QUOTA_EXHAUSTED || this == AUTH || this == MODEL_NOT_FOUND;
    }
}
