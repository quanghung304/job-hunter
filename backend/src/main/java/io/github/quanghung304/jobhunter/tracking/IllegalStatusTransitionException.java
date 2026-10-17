package io.github.quanghung304.jobhunter.tracking;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/** A tracking status change not allowed by {@link TrackingStatusMachine}; HTTP 409. */
public class IllegalStatusTransitionException extends JobHunterException {

    public static final String CODE = "ILLEGAL_STATUS_TRANSITION";

    private final TrackingStatus from;
    private final TrackingStatus to;

    public IllegalStatusTransitionException(TrackingStatus from, TrackingStatus to) {
        super(CODE, "Cannot change status from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public TrackingStatus from() {
        return from;
    }

    public TrackingStatus to() {
        return to;
    }

    @Override
    public int httpStatus() {
        return 409;
    }
}
