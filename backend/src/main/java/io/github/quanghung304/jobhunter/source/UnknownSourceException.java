package io.github.quanghung304.jobhunter.source;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/** No collector is registered for the requested source code; HTTP 404. */
public class UnknownSourceException extends JobHunterException {

    public static final String CODE = "SOURCE_NOT_FOUND";

    public UnknownSourceException(String sourceCode) {
        super(CODE, "No collector for source '" + sourceCode + "'");
    }

    @Override
    public int httpStatus() {
        return 404;
    }
}
