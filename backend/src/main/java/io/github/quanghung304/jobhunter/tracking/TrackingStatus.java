package io.github.quanghung304.jobhunter.tracking;

/** Owner's tracking status of a job (data-model.md §2). */
public enum TrackingStatus {
    NEW,
    SHORTLISTED,
    DISMISSED,
    APPLIED,
    INTERVIEWING,
    OFFER,
    REJECTED,
    WITHDRAWN
}
