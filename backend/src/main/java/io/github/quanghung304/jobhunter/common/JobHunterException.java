package io.github.quanghung304.jobhunter.common;

/**
 * Base class of all module-specific runtime exceptions.
 *
 * <p>{@link #code()} is a stable machine string (e.g. {@code JOB_NOT_FOUND}) exposed as the
 * {@code code} field of Problem Details. Subclasses override {@link #httpStatus()} when the
 * error should not be reported as 400 Bad Request.
 */
public class JobHunterException extends RuntimeException {

    private final String code;

    public JobHunterException(String code, String message) {
        super(message);
        this.code = code;
    }

    public JobHunterException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** HTTP status used when this exception reaches the REST API. */
    public int httpStatus() {
        return 400;
    }
}
