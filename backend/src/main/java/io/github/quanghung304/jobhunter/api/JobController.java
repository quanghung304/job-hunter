package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.enrich.AnalysisResult;
import io.github.quanghung304.jobhunter.enrich.AnalysisService;
import io.github.quanghung304.jobhunter.enrich.Eligibility;
import io.github.quanghung304.jobhunter.ingest.JobNotFoundException;
import io.github.quanghung304.jobhunter.llm.BudgetReachedException;
import io.github.quanghung304.jobhunter.llm.LlmPausedException;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.scoring.ScoringResult;
import io.github.quanghung304.jobhunter.scoring.ScoringService;
import io.github.quanghung304.jobhunter.tracking.AnalysisStage;
import io.github.quanghung304.jobhunter.tracking.JobBoardFilter;
import io.github.quanghung304.jobhunter.tracking.JobBoardQuery;
import io.github.quanghung304.jobhunter.tracking.JobDetailQuery;
import io.github.quanghung304.jobhunter.tracking.JobSort;
import io.github.quanghung304.jobhunter.tracking.TrackingService;
import io.github.quanghung304.jobhunter.tracking.TrackingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The board, job detail and the owner's actions on one job (api-spec.md, "Jobs"). */
@RestController
@RequestMapping(path = "/api/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
public class JobController {

    static final int DEFAULT_SIZE = 25;
    static final int MAX_SIZE = 100;

    private final JobBoardQuery board;
    private final JobDetailQuery details;
    private final JobDetailMapper detailMapper;
    private final TrackingService tracking;
    private final AnalysisService analysis;
    private final ScoringService scoring;
    private final ProfileProvider profiles;

    public JobController(JobBoardQuery board, JobDetailQuery details, JobDetailMapper detailMapper,
            TrackingService tracking, AnalysisService analysis, ScoringService scoring, ProfileProvider profiles) {
        this.board = board;
        this.details = details;
        this.detailMapper = detailMapper;
        this.tracking = tracking;
        this.analysis = analysis;
        this.scoring = scoring;
        this.profiles = profiles;
    }

    @Operation(summary = "The board of the current profile",
            description = "With no filter the default view applies: status NEW or SHORTLISTED, prefilter PASS, "
                    + "eligibility not INELIGIBLE, job ACTIVE. Any filter replaces the default view; EXPIRED jobs "
                    + "stay hidden unless includeExpired=true.")
    @GetMapping
    public JobPageResponse list(
            @RequestParam(required = false) List<TrackingStatus> status,
            @RequestParam(required = false) List<Eligibility> eligibility,
            @RequestParam(required = false) @Min(0) @Max(100) Integer minScore,
            @RequestParam(required = false) @Min(0) @Max(100) Integer maxScore,
            @RequestParam(required = false) List<String> source,
            @RequestParam(required = false) List<EmploymentType> employmentType,
            @RequestParam(required = false) @Positive Integer postedWithinDays,
            @Parameter(description = "Text in title or company (case-insensitive)")
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "false") boolean includeExpired,
            @RequestParam(required = false) List<AnalysisStage> analysisStage,
            @Parameter(schema = @Schema(allowableValues = {"score", "posted"}, defaultValue = "score"))
            @RequestParam(defaultValue = "score") @Pattern(regexp = "score|posted") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) @Min(1) @Max(MAX_SIZE) int size) {
        JobBoardFilter filter = new JobBoardFilter(set(status), set(eligibility), minScore, maxScore, set(source),
                set(employmentType), postedWithinDays, q, includeExpired, set(analysisStage));
        JobSort order = JobSort.valueOf(sort.toUpperCase(Locale.ROOT));
        return JobPageResponse.of(board.page(profileId(), filter, order, page, size));
    }

    @GetMapping("/{id}")
    public JobDetailResponse detail(@PathVariable long id) {
        return detailResponse(id);
    }

    @Operation(summary = "Change the tracking status",
            description = "409 ILLEGAL_STATUS_TRANSITION when the status machine does not allow it.")
    @PostMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JobSummaryResponse changeStatus(@PathVariable long id, @Valid @RequestBody StatusChangeRequest request) {
        String profileId = profileId();
        tracking.changeStatus(id, profileId, request.to(), request.note());
        return board.find(id, profileId).map(JobSummaryResponse::of).orElseThrow(() -> new JobNotFoundException(id));
    }

    @Operation(summary = "Owner action Analyze (ADR-0010)",
            description = "Extraction (at most one EXTRACT call, none when stored) and eligibility. A failed attempt "
                    + "answers 200 with analysisStage FAILED.")
    @PostMapping("/{id}/analyze")
    public JobDetailResponse analyze(@PathVariable long id) {
        AnalysisResult result = analysis.analyze(id);
        switch (result.outcome()) {
            case JOB_EXPIRED -> throw new ActionRefusedException(ActionRefusedException.JOB_EXPIRED,
                    "Job " + id + " has expired");
            case PREFILTER_REJECTED -> throw new ActionRefusedException(ActionRefusedException.JOB_PREFILTER_REJECTED,
                    "The prefilter rejects job " + id + " for this profile");
            case LLM_PAUSED -> throw new LlmPausedException(LlmPurpose.EXTRACT, result.message());
            case BUDGET_REACHED -> throw new BudgetReachedException(result.message());
            case ANALYZED, FAILED, FAILED_PERMANENTLY -> {
                // answered with the job detail
            }
        }
        return detailResponse(id);
    }

    @Operation(summary = "Owner action Score (ADR-0010)",
            description = "One SCORE call for an analyzed ELIGIBLE or UNCERTAIN job; none when already scored.")
    @PostMapping("/{id}/score")
    public JobDetailResponse score(@PathVariable long id) {
        Profile profile = profiles.current();
        ScoringResult result = scoring.score(id, profile);
        switch (result.outcome()) {
            case JOB_EXPIRED -> throw new ActionRefusedException(ActionRefusedException.JOB_EXPIRED,
                    "Job " + id + " has expired");
            case NOT_ANALYZED -> throw new ActionRefusedException(ActionRefusedException.JOB_NOT_ANALYZED,
                    "Job " + id + " is not analyzed yet; press Analyze first");
            case NOT_ELIGIBLE -> throw new ActionRefusedException(ActionRefusedException.JOB_NOT_ELIGIBLE,
                    "Job " + id + " is not eligible" + (result.message() == null ? "" : ": " + result.message()));
            case LLM_PAUSED -> throw new LlmPausedException(LlmPurpose.SCORE, result.message());
            case BUDGET_REACHED -> throw new BudgetReachedException(result.message());
            case SCORED, ALREADY_SCORED, FAILED, FAILED_PERMANENTLY -> {
                // answered with the job detail
            }
        }
        return detailResponse(id);
    }

    @PutMapping(path = "/{id}/notes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateNotes(@PathVariable long id, @Valid @RequestBody NotesRequest request) {
        tracking.updateNotes(id, profileId(), request.notes());
    }

    private JobDetailResponse detailResponse(long id) {
        return details.find(id, profileId()).map(detailMapper::toResponse)
                .orElseThrow(() -> new JobNotFoundException(id));
    }

    private String profileId() {
        return profiles.current().id();
    }

    private static <T> Set<T> set(List<T> values) {
        return values == null ? Set.of() : new HashSet<>(values);
    }
}
