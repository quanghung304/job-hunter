package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.enrich.CheckResult;
import io.github.quanghung304.jobhunter.enrich.EligibilityRule;
import io.github.quanghung304.jobhunter.enrich.JobAttributes;
import io.github.quanghung304.jobhunter.enrich.JobAttributes.RemoteType;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import io.github.quanghung304.jobhunter.tracking.AnalysisStage;
import io.github.quanghung304.jobhunter.tracking.EligibilityDecision;
import io.github.quanghung304.jobhunter.tracking.ScoreBand;
import io.github.quanghung304.jobhunter.tracking.TrackingEventKind;
import io.github.quanghung304.jobhunter.tracking.TrackingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The job detail (api-spec {@code JobDetail}): the {@code JobSummary} fields plus description,
 * extraction, assessment details, postings, notes, history and the allowed status changes.
 */
public record JobDetailResponse(
        @Schema(requiredMode = REQUIRED) long id,
        @Schema(requiredMode = REQUIRED) String title,
        @Schema(requiredMode = REQUIRED) String company,
        @Schema(requiredMode = REQUIRED, nullable = true) String locationText,
        @Schema(requiredMode = REQUIRED, nullable = true, minimum = "0", maximum = "100") Integer score,
        @Schema(requiredMode = REQUIRED) ScoreBand scoreBand,
        @Schema(requiredMode = REQUIRED, nullable = true) String summary,
        @Schema(requiredMode = REQUIRED, nullable = true, description = "The owner's decision; null = undecided")
        EligibilityDecision eligibilityDecision,
        @Schema(requiredMode = REQUIRED, nullable = true, description = "Eligibility check results; null until analyzed")
        JobSummaryResponse.Checks checks,
        @Schema(requiredMode = REQUIRED, nullable = true) RemoteType remoteType,
        @Schema(requiredMode = REQUIRED) List<String> allowedRegions,
        @Schema(requiredMode = REQUIRED) List<EmploymentType> employmentTypes,
        @Schema(requiredMode = REQUIRED, nullable = true) Instant postedAt,
        @Schema(requiredMode = REQUIRED) Instant firstSeenAt,
        @Schema(requiredMode = REQUIRED) List<SourceSummaryResponse> sources,
        @Schema(requiredMode = REQUIRED) TrackingStatus status,
        @Schema(requiredMode = REQUIRED) JobStatus jobStatus,
        @Schema(requiredMode = REQUIRED) AnalysisStage analysisStage,
        @Schema(requiredMode = REQUIRED) String description,
        @Schema(requiredMode = REQUIRED, nullable = true) String applyUrl,
        @Schema(requiredMode = REQUIRED, nullable = true) Salary salary,
        @Schema(requiredMode = REQUIRED) List<String> tags,
        @Schema(requiredMode = REQUIRED, nullable = true,
                description = "LLM-extracted attributes (scoring-spec §3); null until analyzed") JobAttributes extraction,
        @Schema(requiredMode = REQUIRED, description = "One check per criterion (scoring-spec §4); empty until analyzed")
        List<EligibilityCheckResponse> eligibilityChecks,
        @Schema(requiredMode = REQUIRED, nullable = true) Instant eligibilityDecidedAt,
        @Schema(requiredMode = REQUIRED) List<String> prefilterReasons,
        @Schema(requiredMode = REQUIRED, nullable = true) ScoreBreakdown scoreBreakdown,
        @Schema(requiredMode = REQUIRED) List<String> strengths,
        @Schema(requiredMode = REQUIRED) List<String> gaps,
        @Schema(requiredMode = REQUIRED) List<String> redFlags,
        @Schema(requiredMode = REQUIRED, nullable = true) String applyAdvice,
        @Schema(requiredMode = REQUIRED) List<Posting> postings,
        @Schema(requiredMode = REQUIRED, nullable = true) String notes,
        @Schema(requiredMode = REQUIRED) List<HistoryEntry> history,
        @Schema(requiredMode = REQUIRED, description = "Statuses the UI may offer")
        List<TrackingStatus> allowedTransitions) {

    public JobDetailResponse {
        allowedRegions = List.copyOf(allowedRegions);
        employmentTypes = List.copyOf(employmentTypes);
        sources = List.copyOf(sources);
        tags = List.copyOf(tags);
        eligibilityChecks = List.copyOf(eligibilityChecks);
        prefilterReasons = List.copyOf(prefilterReasons);
        strengths = List.copyOf(strengths);
        gaps = List.copyOf(gaps);
        redFlags = List.copyOf(redFlags);
        postings = List.copyOf(postings);
        history = List.copyOf(history);
        allowedTransitions = List.copyOf(allowedTransitions);
    }

    /** Structured salary as given by the source; every field may be null. */
    @Schema(name = "JobSalary")
    public record Salary(
            @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal min,
            @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal max,
            @Schema(requiredMode = REQUIRED, nullable = true) String currency,
            @Schema(requiredMode = REQUIRED, nullable = true) SalaryPeriod period) {
    }

    /** Points per rubric key (scoring-spec §5); the weights sum to 100. */
    public record ScoreBreakdown(
            @Schema(requiredMode = REQUIRED, maximum = "40") int skills,
            @Schema(requiredMode = REQUIRED, maximum = "20") int experience,
            @Schema(requiredMode = REQUIRED, maximum = "20") int arrangement,
            @Schema(requiredMode = REQUIRED, maximum = "10") int domain,
            @Schema(requiredMode = REQUIRED, maximum = "10") int other) {
    }

    /** One source listing the job ("via sourceName" link). */
    @Schema(name = "JobPosting")
    public record Posting(
            @Schema(requiredMode = REQUIRED) String sourceCode,
            @Schema(requiredMode = REQUIRED) String sourceName,
            @Schema(requiredMode = REQUIRED) String url,
            @Schema(requiredMode = REQUIRED) Instant firstSeenAt,
            @Schema(requiredMode = REQUIRED) Instant lastSeenAt) {
    }

    /** One eligibility criterion; no overall verdict (ADR-0011). */
    @Schema(name = "EligibilityCheck")
    public record EligibilityCheckResponse(
            @Schema(requiredMode = REQUIRED) EligibilityRule code,
            @Schema(requiredMode = REQUIRED) boolean critical,
            @Schema(requiredMode = REQUIRED) CheckResult result,
            @Schema(requiredMode = REQUIRED, description = "Job-side value shown to the owner") String value,
            @Schema(requiredMode = REQUIRED, nullable = true, description = "Reason code, e.g. FULL_TIME_ONLY_SOFT")
            String reason) {
    }

    /**
     * One history entry: a status change ({@code from}/{@code to} are tracking statuses) or an
     * eligibility decision ({@code ELIGIBLE}, {@code INELIGIBLE} or {@code UNDECIDED}).
     */
    @Schema(name = "StatusHistoryEntry")
    public record HistoryEntry(
            @Schema(requiredMode = REQUIRED) TrackingEventKind kind,
            @Schema(requiredMode = REQUIRED) String from,
            @Schema(requiredMode = REQUIRED) String to,
            @Schema(requiredMode = REQUIRED, nullable = true) String note,
            @Schema(requiredMode = REQUIRED) Instant at) {
    }
}
