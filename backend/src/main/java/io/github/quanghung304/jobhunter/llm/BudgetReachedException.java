package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/**
 * The budget guard refuses the call (monthly PAID budget or {@code max-calls-per-run}): api
 * {@code BUDGET_REACHED}, 409.
 */
public class BudgetReachedException extends JobHunterException {

    public static final String CODE = "BUDGET_REACHED";

    public BudgetReachedException(String detail) {
        super(CODE, "LLM budget reached" + (detail == null || detail.isBlank() ? "" : ": " + detail));
    }

    @Override
    public int httpStatus() {
        return 409;
    }
}
