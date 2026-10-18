package io.github.quanghung304.jobhunter.llm;

/**
 * The call failed after the retries of scoring-spec §6 (invalid output after the corrective
 * retry, 5xx/timeouts after backoff, a bad request). Counts as one attempt for the job.
 */
public record LlmFailed<T>(LlmPurpose purpose, String modelId, LlmErrorKind kind, String message)
        implements LlmOutcome<T> {
}
