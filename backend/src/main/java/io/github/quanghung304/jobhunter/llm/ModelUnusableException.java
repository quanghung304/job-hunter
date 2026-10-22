package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/** The model cannot be assigned: status ERROR (e.g. missing key, bad key, model removed); 409. */
public class ModelUnusableException extends JobHunterException {

    public static final String CODE = "MODEL_UNUSABLE";

    public ModelUnusableException(String label, String detail) {
        super(CODE, "The " + label + " model cannot be used: " + (detail == null ? "status ERROR" : detail)
                + ". Fix it (key in .env, model id in config/llm.yaml), restart or press Test, then switch.");
    }

    @Override
    public int httpStatus() {
        return 409;
    }
}
