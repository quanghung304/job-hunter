package io.github.quanghung304.jobhunter.tracking;

import java.time.Instant;

/**
 * One entry of a job's tracking history; {@code note} may be null. For {@link TrackingEventKind#STATUS}
 * {@code from}/{@code to} are tracking statuses, for {@link TrackingEventKind#ELIGIBILITY} they are
 * {@code ELIGIBLE}, {@code INELIGIBLE} or {@code UNDECIDED}.
 */
public record StatusChange(TrackingEventKind kind, String from, String to, String note, Instant at) {

    public static final String UNDECIDED = "UNDECIDED";
}
