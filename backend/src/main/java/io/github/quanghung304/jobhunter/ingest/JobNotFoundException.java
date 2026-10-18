package io.github.quanghung304.jobhunter.ingest;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/** No job with this id (api-spec {@code JOB_NOT_FOUND}, 404). */
public class JobNotFoundException extends JobHunterException {

    public static final String CODE = "JOB_NOT_FOUND";

    public JobNotFoundException(long jobId) {
        super(CODE, "Job " + jobId + " not found");
    }

    @Override
    public int httpStatus() {
        return 404;
    }
}
