package io.github.quanghung304.jobhunter.source;

import java.time.Instant;

/**
 * One {@code collector_run} for the admin page.
 *
 * @param status null while the run is in progress
 * @param error  null unless the run failed or was partial with a message
 */
public record CollectorRunView(long id, String sourceCode, CollectStatus status, Instant startedAt,
        Instant finishedAt, int fetched, int newRaw, int newJobs, String error) {
}
