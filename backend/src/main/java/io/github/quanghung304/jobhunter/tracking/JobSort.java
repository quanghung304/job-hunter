package io.github.quanghung304.jobhunter.tracking;

/** Board order (api-spec {@code sort}). */
public enum JobSort {
    /** Scored jobs first by score (highest first), then the others by posting date (newest first). */
    SCORE,
    /** By posting date (or first seen when unknown), newest first. */
    POSTED
}
