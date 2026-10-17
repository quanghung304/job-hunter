package io.github.quanghung304.jobhunter.ingest;

/**
 * Published (Spring application event, inside the ingest transaction) when an existing job's
 * {@code description_hash} changes, so its extraction can be invalidated (enrich, T-011).
 */
public record JobDescriptionChanged(long jobId) {
}
