package io.github.quanghung304.jobhunter.enrich;

import java.util.List;

/**
 * Outcome of the owner's "Analyze" action for one job (ADR-0010).
 *
 * @param eligibility set when {@code outcome == ANALYZED}
 * @param llmCalled   whether an EXTRACT call was made (false when the extraction was cached)
 */
public record AnalysisResult(
        long jobId,
        Outcome outcome,
        Eligibility eligibility,
        List<String> eligibilityReasons,
        boolean llmCalled,
        String message) {

    public AnalysisResult {
        eligibilityReasons = eligibilityReasons == null ? List.of() : List.copyOf(eligibilityReasons);
    }

    public enum Outcome {
        /** Extraction available and eligibility stored. */
        ANALYZED,
        /** The job is EXPIRED: no LLM call (api {@code JOB_EXPIRED}). */
        JOB_EXPIRED,
        /** The prefilter rejects the job for this profile: no LLM call. */
        PREFILTER_REJECTED,
        /** No model may serve EXTRACT now (quota, error, paid consent): api {@code LLM_PAUSED}. */
        LLM_PAUSED,
        /** Budget guard (per action or monthly): api {@code BUDGET_REACHED}. */
        BUDGET_REACHED,
        /** This attempt failed; the owner may try again. */
        FAILED,
        /** Max attempts for this prompt version used up; no further call. */
        FAILED_PERMANENTLY
    }
}
