package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.common.JobHunterException;
import java.math.BigDecimal;

/**
 * An action that may start PAID calls was sent without {@code confirmPaid: true} (ADR-0009); 409
 * with the model and the estimated cost of one call (null when unknown), for the confirmation.
 */
public class PaidConfirmationRequiredException extends JobHunterException {

    public static final String CODE = "PAID_CONFIRMATION_REQUIRED";

    private final ModelChoice model;
    private final transient BigDecimal estimatedCostPerCallUsd;

    public PaidConfirmationRequiredException(ModelChoice model, BigDecimal estimatedCostPerCallUsd) {
        super(CODE, "The " + model.label() + " model is PAID: confirm with confirmPaid=true"
                + (estimatedCostPerCallUsd == null ? " (cost per call unknown)"
                        : " (about $" + estimatedCostPerCallUsd.toPlainString() + " per call)"));
        this.model = model;
        this.estimatedCostPerCallUsd = estimatedCostPerCallUsd;
    }

    public ModelChoice model() {
        return model;
    }

    /** Null when the model has no cost history. */
    public BigDecimal estimatedCostPerCallUsd() {
        return estimatedCostPerCallUsd;
    }

    @Override
    public int httpStatus() {
        return 409;
    }
}
