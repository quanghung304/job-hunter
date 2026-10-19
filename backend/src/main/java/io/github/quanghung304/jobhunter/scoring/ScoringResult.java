package io.github.quanghung304.jobhunter.scoring;

/**
 * Outcome of the owner's "Score" action for one job (ADR-0010).
 *
 * @param llmCalled whether a SCORE call was made (false for a stored score or a refusal)
 */
public record ScoringResult(long jobId, Outcome outcome, Integer score, String summary, boolean llmCalled,
        String message) {

    public enum Outcome {
        SCORED,
        /** A score exists for this job, profile and prompt version: no call. */
        ALREADY_SCORED,
        /** The job is EXPIRED: no LLM call (api {@code JOB_EXPIRED}). */
        JOB_EXPIRED,
        /** No OK extraction or eligibility yet (api {@code JOB_NOT_ANALYZED}). */
        NOT_ANALYZED,
        /** The owner marked the job Not eligible (api {@code JOB_MARKED_INELIGIBLE}, ADR-0011). */
        MARKED_INELIGIBLE,
        /** No model may serve SCORE now, incl. a PAID model without consent (api {@code LLM_PAUSED}). */
        LLM_PAUSED,
        /** Budget guard (api {@code BUDGET_REACHED}). */
        BUDGET_REACHED,
        /** This attempt failed; the owner may try again. */
        FAILED,
        /** Max attempts for this prompt version used up. */
        FAILED_PERMANENTLY
    }
}
