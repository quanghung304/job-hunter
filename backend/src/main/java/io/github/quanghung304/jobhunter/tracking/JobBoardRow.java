package io.github.quanghung304.jobhunter.tracking;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.enrich.CheckCounts;
import io.github.quanghung304.jobhunter.enrich.JobAttributes.RemoteType;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import java.time.Instant;
import java.util.List;

/**
 * One job on the board for one profile (api-spec {@code JobSummary} without display names).
 * Nullable: {@code locationText}, {@code score}, {@code summary}, {@code eligibilityDecision}
 * (undecided), {@code checks} (not analyzed), {@code remoteType}, {@code postedAt}.
 *
 * @param sourceCodes codes of the sources that list the job, sorted
 */
public record JobBoardRow(
        long id,
        String title,
        String company,
        String locationText,
        Integer score,
        String summary,
        EligibilityDecision eligibilityDecision,
        CheckCounts checks,
        RemoteType remoteType,
        List<String> allowedRegions,
        List<EmploymentType> employmentTypes,
        Instant postedAt,
        Instant firstSeenAt,
        List<String> sourceCodes,
        TrackingStatus status,
        JobStatus jobStatus,
        AnalysisStage analysisStage) {

    public JobBoardRow {
        allowedRegions = List.copyOf(allowedRegions);
        employmentTypes = List.copyOf(employmentTypes);
        sourceCodes = List.copyOf(sourceCodes);
    }

    public ScoreBand scoreBand() {
        return ScoreBand.of(score);
    }
}
