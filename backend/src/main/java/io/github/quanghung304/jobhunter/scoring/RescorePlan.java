package io.github.quanghung304.jobhunter.scoring;

import io.github.quanghung304.jobhunter.llm.ModelChoice;
import java.math.BigDecimal;
import java.util.List;

/**
 * "Re-score active jobs" (admin): the dry run counts scored ACTIVE eligible jobs and names the
 * SCORE model and estimated cost (ADR-0009); the confirmed run scores them again.
 */
public record RescorePlan(
        boolean dryRun,
        int jobCount,
        int estimatedCalls,
        ModelChoice model,
        BigDecimal estimatedCostUsd,
        int scored,
        int failed,
        ScoringResult.Outcome stoppedBy,
        List<Long> notProcessed) {

    public RescorePlan {
        notProcessed = List.copyOf(notProcessed);
    }
}
