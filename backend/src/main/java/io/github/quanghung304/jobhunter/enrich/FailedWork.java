package io.github.quanghung304.jobhunter.enrich;

/** A job whose last LLM attempt (extraction or scoring) failed; listed on the admin page with Retry. */
public record FailedWork(long jobId, int attempts) {
}
