package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/**
 * An action would call a PAID model while the owner has not consented to paid calls
 * ({@code app.llm.allow-paid-calls=false}, ADR-0009): api {@code PAID_CALLS_DISABLED}, 409.
 */
public class PaidCallsDisabledException extends JobHunterException {

    public static final String CODE = "PAID_CALLS_DISABLED";

    public PaidCallsDisabledException(ModelChoice model) {
        super(CODE, "The " + model.label() + " model is PAID and paid calls are disabled"
                + " (set LLM_ALLOW_PAID_CALLS=true to allow them)");
    }

    @Override
    public int httpStatus() {
        return 409;
    }
}
