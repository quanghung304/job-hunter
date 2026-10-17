package io.github.quanghung304.jobhunter.source;

/**
 * Result of {@link CollectorRunner#run}.
 *
 * @param runId      id of the {@code collector_run} row; {@code null} when skipped
 * @param result     what the collector returned; {@code null} when skipped
 * @param skipReason why the run was skipped (min-interval); {@code null} when it ran
 */
public record CollectorRunOutcome(Long runId, CollectResult result, String skipReason) {

    static CollectorRunOutcome ran(long runId, CollectResult result) {
        return new CollectorRunOutcome(runId, result, null);
    }

    static CollectorRunOutcome skipped(String reason) {
        return new CollectorRunOutcome(null, null, reason);
    }

    public boolean skipped() {
        return skipReason != null;
    }
}
