package io.github.quanghung304.jobhunter.pipeline;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/** A run is in progress (this process or another one holding the DB lock): 409. */
public class PipelineAlreadyRunningException extends JobHunterException {

    public static final String CODE = "PIPELINE_ALREADY_RUNNING";

    public PipelineAlreadyRunningException() {
        super(CODE, "A pipeline run is already in progress");
    }

    @Override
    public int httpStatus() {
        return 409;
    }
}
