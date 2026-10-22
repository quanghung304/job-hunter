package io.github.quanghung304.jobhunter.notify;

/** Kind of a sent notification. Instant job alerts were removed (ADR-0010). */
public enum NotificationKind {
    DIGEST,
    /** A model of an assigned purpose is near its limit, exhausted, broken or back (T-022). */
    LLM_QUOTA
}
