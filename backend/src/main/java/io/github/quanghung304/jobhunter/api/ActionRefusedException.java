package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/**
 * An owner action the services refused without an error of their own (an outcome such as
 * "job expired"), reported as 409 with the api-spec code, e.g. {@code JOB_MARKED_INELIGIBLE}.
 */
class ActionRefusedException extends JobHunterException {

    static final String JOB_EXPIRED = "JOB_EXPIRED";
    static final String JOB_PREFILTER_REJECTED = "JOB_PREFILTER_REJECTED";
    static final String JOB_NOT_ANALYZED = "JOB_NOT_ANALYZED";
    static final String JOB_MARKED_INELIGIBLE = "JOB_MARKED_INELIGIBLE";

    ActionRefusedException(String code, String message) {
        super(code, message);
    }

    @Override
    public int httpStatus() {
        return 409;
    }
}
