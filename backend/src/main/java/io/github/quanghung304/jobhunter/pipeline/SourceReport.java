package io.github.quanghung304.jobhunter.pipeline;

import io.github.quanghung304.jobhunter.source.CollectStatus;

/**
 * One collector in a run.
 *
 * @param status   null when the collector was skipped (min-interval)
 * @param message  skip reason or error
 */
public record SourceReport(String sourceCode, CollectStatus status, int fetched, int newRaw, int newJobs,
        int updatedJobs, String message) {

    public boolean skipped() {
        return status == null;
    }
}
