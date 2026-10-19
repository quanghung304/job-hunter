package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/** An endpoint of the contract whose feature comes in a later task: 501 {@code NOT_IMPLEMENTED}. */
class NotImplementedException extends JobHunterException {

    static final String CODE = "NOT_IMPLEMENTED";

    NotImplementedException(String message) {
        super(CODE, message);
    }

    @Override
    public int httpStatus() {
        return 501;
    }
}
