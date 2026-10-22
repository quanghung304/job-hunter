package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/** A model id that is not in {@code config/llm.yaml}: 400, or 404 when it names the resource of the URL. */
public class UnknownModelException extends JobHunterException {

    public static final String CODE = "UNKNOWN_MODEL";

    private final boolean inPath;

    public UnknownModelException(String modelId, boolean inPath) {
        super(CODE, "Model '" + modelId + "' is not in config/llm.yaml");
        this.inPath = inPath;
    }

    @Override
    public int httpStatus() {
        return inPath ? 404 : 400;
    }
}
