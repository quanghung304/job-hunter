package io.github.quanghung304.jobhunter.tracking;

import java.time.Instant;

/** One entry of a job's tracking history; {@code note} may be null. */
public record StatusChange(TrackingStatus from, TrackingStatus to, String note, Instant at) {
}
