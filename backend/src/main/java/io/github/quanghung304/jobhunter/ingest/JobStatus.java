package io.github.quanghung304.jobhunter.ingest;

/** Lifecycle of a deduplicated job; independent of the tracking status. */
public enum JobStatus {
    ACTIVE,
    EXPIRED
}
