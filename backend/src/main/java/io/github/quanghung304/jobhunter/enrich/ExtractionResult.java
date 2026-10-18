package io.github.quanghung304.jobhunter.enrich;

import io.github.quanghung304.jobhunter.llm.LlmUnavailable;
import java.util.Optional;

/**
 * Outcome of one extraction request.
 *
 * @param attributes   present when {@code status == OK}
 * @param extractionId stored {@code job_extraction} row, null for {@link ExtractionService#extractAttributes}
 * @param llmCalled    false when the result came from the cache (or no call was allowed)
 * @param unavailable  set when {@code status == UNAVAILABLE}: no model may serve EXTRACT now
 *                     (paused, budget or paid consent) — not a job failure
 * @param attempts     stored failed/succeeded attempts for this prompt version
 */
public record ExtractionResult(
        Status status,
        JobAttributes attributes,
        Long extractionId,
        String modelId,
        boolean llmCalled,
        LlmUnavailable.Reason unavailable,
        int attempts,
        String message) {

    public enum Status {
        OK,
        /** The attempt failed (invalid output after the corrective retry, server errors). */
        FAILED,
        /** {@link ExtractionService#MAX_ATTEMPTS} failed attempts: no further call for this prompt version. */
        FAILED_PERMANENTLY,
        UNAVAILABLE
    }

    public Optional<JobAttributes> attributesIfOk() {
        return status == Status.OK ? Optional.of(attributes) : Optional.empty();
    }
}
