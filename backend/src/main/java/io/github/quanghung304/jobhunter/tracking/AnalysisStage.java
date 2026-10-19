package io.github.quanghung304.jobhunter.tracking;

/**
 * How far the owner's LLM actions got for a job and profile (api-spec {@code analysisStage},
 * ADR-0010). Derived from the stored extraction and assessment, never stored.
 */
public enum AnalysisStage {
    /** No extraction for the current prompt version yet. */
    NOT_ANALYZED,
    /** Extraction and eligibility done, not scored. */
    ANALYZED,
    /** Scored for the current score prompt version. */
    SCORED,
    /** The last extraction or scoring attempt failed and nothing newer succeeded; retry available. */
    FAILED
}
