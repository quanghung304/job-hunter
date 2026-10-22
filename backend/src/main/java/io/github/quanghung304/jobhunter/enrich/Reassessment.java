package io.github.quanghung304.jobhunter.enrich;

/**
 * Result of {@link AssessmentService#reassessPrefilter}: rows re-evaluated for a new profile hash,
 * rows whose prefilter result changed, rows whose eligibility checks changed (no LLM).
 */
public record Reassessment(int jobs, int prefilterChanged, int checksUpdated) {
}
