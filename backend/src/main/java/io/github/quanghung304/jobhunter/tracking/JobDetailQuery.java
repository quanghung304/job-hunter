package io.github.quanghung304.jobhunter.tracking;

import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.ExtractionResult;
import io.github.quanghung304.jobhunter.enrich.ExtractionService;
import io.github.quanghung304.jobhunter.enrich.JobAttributes;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobView;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The job detail of one profile (api-spec {@code GET /api/jobs/{id}}); read-only, no LLM call. */
@Service
@Transactional(readOnly = true)
public class JobDetailQuery {

    private final JobBoardQuery board;
    private final JobQueryService jobs;
    private final AssessmentService assessments;
    private final ExtractionService extraction;
    private final TrackingService tracking;

    public JobDetailQuery(JobBoardQuery board, JobQueryService jobs, AssessmentService assessments,
            ExtractionService extraction, TrackingService tracking) {
        this.board = board;
        this.jobs = jobs;
        this.assessments = assessments;
        this.extraction = extraction;
        this.tracking = tracking;
    }

    public Optional<JobDetailView> find(long jobId, String profileId) {
        Optional<JobView> job = jobs.find(jobId);
        Optional<JobBoardRow> summary = board.find(jobId, profileId);
        if (job.isEmpty() || summary.isEmpty()) {
            return Optional.empty();
        }
        JobAttributes attributes = extraction.current(jobId).flatMap(ExtractionResult::attributesIfOk).orElse(null);
        return Optional.of(new JobDetailView(
                summary.get(),
                job.get(),
                attributes,
                assessments.find(jobId, profileId).orElse(null),
                jobs.postings(jobId),
                tracking.notes(jobId, profileId).orElse(null),
                tracking.history(jobId, profileId),
                TrackingStatusMachine.allowedTargets(summary.get().status())));
    }
}
