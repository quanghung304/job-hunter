package io.github.quanghung304.jobhunter.common;

import java.time.Instant;
import java.util.List;

/**
 * Published by the pipeline after each run (T-013) for the digest (notify, T-015). It lives in
 * {@code common} because notify may not depend on the pipeline module.
 *
 * @param newJobIds jobs first seen during this run
 */
public record RunCompleted(Instant startedAt, Instant finishedAt, List<Long> newJobIds) {

    public RunCompleted {
        newJobIds = List.copyOf(newJobIds);
    }
}
