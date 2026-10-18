package io.github.quanghung304.jobhunter.pipeline;

/** What started a pipeline run. */
public enum Trigger {
    SCHEDULED,
    /** On startup, when the last successful run is older than {@code catch-up-after}. */
    CATCH_UP,
    /** Admin "Run pipeline" ({@code POST /api/admin/pipeline/run}). */
    MANUAL
}
