package io.github.quanghung304.jobhunter.llm;

/**
 * Result of {@link LlmRouter#call}: a parsed answer, "no model can serve this now" (not a job
 * failure: the job stays PENDING), or a failed attempt that counts against the job's attempts.
 */
public sealed interface LlmOutcome<T> permits LlmResult, LlmUnavailable, LlmFailed {
}
