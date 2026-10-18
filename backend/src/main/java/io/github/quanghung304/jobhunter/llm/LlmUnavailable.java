package io.github.quanghung304.jobhunter.llm;

/**
 * No model may serve the purpose now (quota, provider error, missing key or budget guard). The
 * caller pauses this purpose for the run and leaves its jobs PENDING; no job attempt is used.
 */
public record LlmUnavailable<T>(LlmPurpose purpose, Reason reason, String detail) implements LlmOutcome<T> {

    public enum Reason {
        /** The assigned model (and every allowed fallback) is RATE_LIMITED, EXHAUSTED or ERROR. */
        NO_USABLE_MODEL,
        /** {@code app.llm.max-calls-per-run} reached. */
        RUN_CALL_LIMIT,
        /** PAID model and the monthly budget is used up. */
        MONTHLY_BUDGET,
        /** PAID model and the owner has not consented to paid calls ({@code app.llm.allow-paid-calls}). */
        PAID_NOT_ALLOWED
    }
}
