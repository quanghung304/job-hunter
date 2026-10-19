package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/**
 * No model may serve the purpose now (quota, provider error, missing key; ADR-0008): the owner
 * action is refused with api {@code LLM_PAUSED}, 409, until the model recovers or is switched.
 */
public class LlmPausedException extends JobHunterException {

    public static final String CODE = "LLM_PAUSED";

    private final LlmPurpose purpose;

    public LlmPausedException(LlmPurpose purpose, String detail) {
        super(CODE, purpose + " is paused: no usable model"
                + (detail == null || detail.isBlank() ? "" : " (" + detail + ")"));
        this.purpose = purpose;
    }

    public LlmPurpose purpose() {
        return purpose;
    }

    @Override
    public int httpStatus() {
        return 409;
    }
}
