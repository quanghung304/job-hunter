package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.enrich.Eligibility;
import io.github.quanghung304.jobhunter.enrich.JobAttributes.RemoteType;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import io.github.quanghung304.jobhunter.tracking.AnalysisStage;
import io.github.quanghung304.jobhunter.tracking.JobBoardRow;
import io.github.quanghung304.jobhunter.tracking.ScoreBand;
import io.github.quanghung304.jobhunter.tracking.TrackingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** One job on the board (api-spec {@code JobSummary}). */
public record JobSummaryResponse(
        @Schema(requiredMode = REQUIRED) long id,
        @Schema(requiredMode = REQUIRED) String title,
        @Schema(requiredMode = REQUIRED) String company,
        @Schema(requiredMode = REQUIRED, nullable = true) String locationText,
        @Schema(requiredMode = REQUIRED, nullable = true, minimum = "0", maximum = "100") Integer score,
        @Schema(requiredMode = REQUIRED) ScoreBand scoreBand,
        @Schema(requiredMode = REQUIRED, nullable = true) String summary,
        @Schema(requiredMode = REQUIRED, nullable = true) Eligibility eligibility,
        @Schema(requiredMode = REQUIRED, nullable = true) RemoteType remoteType,
        @Schema(requiredMode = REQUIRED) List<String> allowedRegions,
        @Schema(requiredMode = REQUIRED) List<EmploymentType> employmentTypes,
        @Schema(requiredMode = REQUIRED, nullable = true) Instant postedAt,
        @Schema(requiredMode = REQUIRED) Instant firstSeenAt,
        @Schema(requiredMode = REQUIRED) List<SourceSummaryResponse> sources,
        @Schema(requiredMode = REQUIRED) TrackingStatus status,
        @Schema(requiredMode = REQUIRED) JobStatus jobStatus,
        @Schema(requiredMode = REQUIRED) AnalysisStage analysisStage) {

    public JobSummaryResponse {
        allowedRegions = List.copyOf(allowedRegions);
        employmentTypes = List.copyOf(employmentTypes);
        sources = List.copyOf(sources);
    }

    static JobSummaryResponse of(JobBoardRow row) {
        return new JobSummaryResponse(row.id(), row.title(), row.company(), row.locationText(), row.score(),
                row.scoreBand(), row.summary(), row.eligibility(), row.remoteType(), row.allowedRegions(),
                row.employmentTypes(), row.postedAt(), row.firstSeenAt(),
                row.sourceCodes().stream().map(SourceSummaryResponse::of).toList(), row.status(), row.jobStatus(),
                row.analysisStage());
    }
}
