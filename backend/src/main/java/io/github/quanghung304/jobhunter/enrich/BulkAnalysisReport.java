package io.github.quanghung304.jobhunter.enrich;

import io.github.quanghung304.jobhunter.llm.ModelChoice;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Dry run or result of the admin bulk "Analyze" (api-spec {@code POST /api/admin/jobs/analyze}).
 *
 * @param skipped        reason → count; reasons: {@code alreadyAnalyzed}, {@code expired},
 *                       {@code prefilterRejected}, {@code failedPermanently}, {@code notFound}
 * @param estimatedCalls EXTRACT calls the run would make (one per job to analyze)
 * @param model          the EXTRACT model that would be used; a PAID one must be named in the confirmation
 * @param estimatedCostUsd null when a PAID model has no cost history yet
 * @param stoppedBy      why the run stopped early, null if it went through
 * @param notProcessed   jobs to analyze that were left because the run stopped
 */
public record BulkAnalysisReport(
        boolean dryRun,
        int selected,
        int willAnalyze,
        Map<String, Integer> skipped,
        int estimatedCalls,
        ModelChoice model,
        BigDecimal estimatedCostUsd,
        int analyzed,
        int failed,
        AnalysisResult.Outcome stoppedBy,
        List<Long> notProcessed) {

    public BulkAnalysisReport {
        skipped = Map.copyOf(skipped);
        notProcessed = List.copyOf(notProcessed);
    }
}
