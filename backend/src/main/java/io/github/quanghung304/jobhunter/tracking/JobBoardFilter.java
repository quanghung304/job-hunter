package io.github.quanghung304.jobhunter.tracking;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.enrich.Eligibility;
import java.util.Set;

/**
 * Filters of {@code GET /api/jobs} (api-spec). Empty sets and nulls mean "no filter". When no
 * filter is set at all, {@link JobBoardQuery} applies the default view.
 *
 * @param q                text searched in title and company (case-insensitive substring)
 * @param includeExpired   EXPIRED jobs are left out unless true
 */
public record JobBoardFilter(
        Set<TrackingStatus> statuses,
        Set<Eligibility> eligibilities,
        Integer minScore,
        Integer maxScore,
        Set<String> sources,
        Set<EmploymentType> employmentTypes,
        Integer postedWithinDays,
        String q,
        boolean includeExpired,
        Set<AnalysisStage> analysisStages) {

    public static final JobBoardFilter NONE = new JobBoardFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(),
            null, null, false, Set.of());

    public JobBoardFilter {
        statuses = copy(statuses);
        eligibilities = copy(eligibilities);
        sources = copy(sources);
        employmentTypes = copy(employmentTypes);
        analysisStages = copy(analysisStages);
        q = q == null || q.isBlank() ? null : q.strip();
    }

    /** True when no filter is set: the board shows its default view. */
    public boolean isEmpty() {
        return statuses.isEmpty() && eligibilities.isEmpty() && minScore == null && maxScore == null
                && sources.isEmpty() && employmentTypes.isEmpty() && postedWithinDays == null && q == null
                && !includeExpired && analysisStages.isEmpty();
    }

    private static <T> Set<T> copy(Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
