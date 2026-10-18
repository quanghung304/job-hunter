package io.github.quanghung304.jobhunter.enrich;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/** A bulk action got more job ids than allowed (api-spec {@code TOO_MANY_JOBS}, 400). */
public class TooManyJobsException extends JobHunterException {

    public static final String CODE = "TOO_MANY_JOBS";

    public TooManyJobsException(int requested, int max) {
        super(CODE, requested + " jobs selected; at most " + max + " per bulk action");
    }
}
