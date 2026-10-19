package io.github.quanghung304.jobhunter.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.enrich.AssessmentView;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.scoring.ScoreResult;
import io.github.quanghung304.jobhunter.source.SourceCatalog;
import io.github.quanghung304.jobhunter.tracking.JobBoardRow;
import io.github.quanghung304.jobhunter.tracking.JobDetailView;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Maps {@link JobDetailView} to {@link JobDetailResponse}; the score details are read from their JSON. */
@Component
class JobDetailMapper {

    private static final Logger log = LoggerFactory.getLogger(JobDetailMapper.class);

    private final ObjectMapper json;

    JobDetailMapper(ObjectMapper json) {
        this.json = json;
    }

    JobDetailResponse toResponse(JobDetailView view) {
        JobBoardRow row = view.summary();
        JobView job = view.job();
        AssessmentView assessment = view.assessment();
        ScoreResult score = assessment == null ? null : scoreDetails(row.id(), assessment.scoreDetails());
        boolean hasSalary = job.salaryMin() != null || job.salaryMax() != null;
        return new JobDetailResponse(
                row.id(), row.title(), row.company(), row.locationText(), row.score(), row.scoreBand(), row.summary(),
                row.eligibility(), row.remoteType(), row.allowedRegions(), row.employmentTypes(), row.postedAt(),
                row.firstSeenAt(), row.sourceCodes().stream().map(SourceSummaryResponse::of).toList(), row.status(),
                row.jobStatus(), row.analysisStage(),
                job.descriptionText(),
                job.applyUrl(),
                hasSalary ? new JobDetailResponse.Salary(job.salaryMin(), job.salaryMax(), job.salaryCurrency(),
                        job.salaryPeriod()) : null,
                job.tags(),
                view.attributes(),
                assessment == null ? List.of() : assessment.eligibilityReasons(),
                assessment == null ? List.of() : assessment.prefilterReasons(),
                score == null ? null : breakdown(score.breakdown()),
                score == null ? List.of() : score.strengths(),
                score == null ? List.of() : score.gaps(),
                score == null ? List.of() : score.redFlags(),
                score == null ? null : score.applyAdvice(),
                view.postings().stream()
                        .map(p -> new JobDetailResponse.Posting(p.sourceCode(), SourceCatalog.name(p.sourceCode()),
                                p.url(), p.firstSeenAt(), p.lastSeenAt()))
                        .toList(),
                view.notes(),
                view.history().stream()
                        .map(h -> new JobDetailResponse.HistoryEntry(h.from(), h.to(), h.note(), h.at()))
                        .toList(),
                view.allowedTransitions().stream().sorted().toList());
    }

    /** The stored §5 object; null when unscored or unreadable (the summary still shows). */
    private ScoreResult scoreDetails(long jobId, JsonNode details) {
        if (details == null || details.isNull()) {
            return null;
        }
        try {
            return json.treeToValue(details, ScoreResult.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("job={}: stored score details are unreadable: {}", jobId, e.getMessage());
            return null;
        }
    }

    private static JobDetailResponse.ScoreBreakdown breakdown(ScoreResult.Breakdown b) {
        if (b == null || b.skills() == null || b.experience() == null || b.arrangement() == null
                || b.domain() == null || b.other() == null) {
            return null;
        }
        return new JobDetailResponse.ScoreBreakdown(b.skills(), b.experience(), b.arrangement(), b.domain(),
                b.other());
    }
}
