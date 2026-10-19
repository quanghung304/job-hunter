package io.github.quanghung304.jobhunter.scoring;

import io.github.quanghung304.jobhunter.llm.ModelChoice;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Dry run or result of the admin bulk "Score" (api-spec {@code POST /api/admin/jobs/score}).
 *
 * @param skipped          reason → count: {@code notAnalyzed}, {@code markedIneligible}, {@code alreadyScored},
 *                         {@code expired}, {@code failedPermanently}, {@code notFound}
 * @param model            the SCORE model that would be used; a PAID one must be named in the confirmation
 * @param estimatedCostUsd null when a PAID model has no cost history yet
 */
public record BulkScoreReport(
        boolean dryRun,
        int selected,
        int willScore,
        Map<String, Integer> skipped,
        int estimatedCalls,
        ModelChoice model,
        BigDecimal estimatedCostUsd,
        int scored,
        int failed,
        ScoringResult.Outcome stoppedBy,
        List<Long> notProcessed) {

    public BulkScoreReport {
        skipped = Map.copyOf(skipped);
        notProcessed = List.copyOf(notProcessed);
    }
}
