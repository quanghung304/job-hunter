package io.github.quanghung304.jobhunter.pipeline;

import io.github.quanghung304.jobhunter.source.CollectStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * One pipeline run (T-013). Not a table: the last 20 are kept in memory for the admin page.
 *
 * @param reassessed  assessment rows re-evaluated because the profile file changed
 * @param notAnalyzed ACTIVE, prefilter-PASS jobs still waiting for the owner's Analyze (ADR-0010)
 * @param llmCalls    {@code llm_call} rows written during the run: always 0 for the pipeline itself
 */
public record PipelineReport(
        long runNumber,
        Trigger trigger,
        Instant startedAt,
        Instant finishedAt,
        List<SourceReport> sources,
        int newJobs,
        int reassessed,
        int prefilterEvaluated,
        int prefilterPassed,
        int prefilterRejected,
        int notAnalyzed,
        int expired,
        long llmCalls,
        String error) {

    public PipelineReport {
        sources = List.copyOf(sources);
    }

    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }

    /** SUCCESS when every collector that ran succeeded, FAILED on a pipeline error or all collectors failing. */
    public CollectStatus status() {
        if (error != null) {
            return CollectStatus.FAILED;
        }
        List<SourceReport> ran = sources.stream().filter(s -> !s.skipped()).toList();
        if (!ran.isEmpty() && ran.stream().allMatch(s -> s.status() == CollectStatus.FAILED)) {
            return CollectStatus.FAILED;
        }
        return ran.stream().allMatch(s -> s.status() == CollectStatus.SUCCESS) ? CollectStatus.SUCCESS
                : CollectStatus.PARTIAL;
    }
}
