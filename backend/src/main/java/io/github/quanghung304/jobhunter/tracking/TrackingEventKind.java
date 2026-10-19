package io.github.quanghung304.jobhunter.tracking;

/** What a {@code job_tracking_event} records. */
public enum TrackingEventKind {
    /** A tracking status change; from/to are {@link TrackingStatus} names. */
    STATUS,
    /** An eligibility decision change; from/to are {@code ELIGIBLE}, {@code INELIGIBLE} or {@code UNDECIDED}. */
    ELIGIBILITY
}
