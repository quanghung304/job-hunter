package io.github.quanghung304.jobhunter.source;

import java.util.List;

/**
 * Outcome of {@link JobCollector#collect}. Collectors never throw for source errors; they
 * report them here.
 *
 * @param postings     mapped postings, possibly fewer than fetched (filtered or malformed)
 * @param fetched      items received from the source, including skipped ones
 * @param warnings     per-item problems (malformed item, a later page failed, ...)
 * @param status       SUCCESS, PARTIAL (some warnings) or FAILED (nothing usable)
 * @param errorMessage why the run failed; {@code null} unless FAILED
 */
public record CollectResult(
        List<NormalizedPosting> postings,
        int fetched,
        List<String> warnings,
        CollectStatus status,
        String errorMessage) {

    public CollectResult {
        postings = List.copyOf(postings);
        warnings = List.copyOf(warnings);
    }

    /** SUCCESS without warnings, otherwise PARTIAL. */
    public static CollectResult completed(List<NormalizedPosting> postings, int fetched, List<String> warnings) {
        return new CollectResult(postings, fetched, warnings,
                warnings.isEmpty() ? CollectStatus.SUCCESS : CollectStatus.PARTIAL, null);
    }

    public static CollectResult failed(int fetched, String errorMessage) {
        return new CollectResult(List.of(), fetched, List.of(), CollectStatus.FAILED, errorMessage);
    }
}
