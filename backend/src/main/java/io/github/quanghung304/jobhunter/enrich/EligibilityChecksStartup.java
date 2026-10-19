package io.github.quanghung304.jobhunter.enrich;

import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup (Java only, no LLM; ADR-0011): ACTIVE jobs get an assessment row for the current
 * score prompt version (after a prompt switch, e.g. score-v1 → score-v2, the board would otherwise
 * stay empty until the next pipeline run), and analyzed jobs without eligibility checks (rows from
 * before V8) get them from the stored extraction.
 */
@Component
class EligibilityChecksStartup {

    private static final Logger log = LoggerFactory.getLogger(EligibilityChecksStartup.class);
    private static final int BATCH = AssessmentService.JOB_BATCH_SIZE;

    private final AssessmentService assessments;
    private final JobQueryService jobs;
    private final ProfileProvider profiles;

    EligibilityChecksStartup(AssessmentService assessments, JobQueryService jobs, ProfileProvider profiles) {
        this.assessments = assessments;
        this.jobs = jobs;
        this.profiles = profiles;
    }

    @EventListener(ApplicationReadyEvent.class)
    void prepareAssessments() {
        Profile profile = profiles.current();
        String hash = profiles.profileHash();
        List<Long> ids = jobs.activeJobIds();
        List<JobView> active = new ArrayList<>(ids.size());
        for (int from = 0; from < ids.size(); from += BATCH) {
            active.addAll(jobs.findAll(ids.subList(from, Math.min(from + BATCH, ids.size()))));
        }
        PrefilterSummary prefilter = assessments.prefilterJobs(active, profile, hash);
        int checked = assessments.evaluateMissingChecks(profile, hash);
        if (prefilter.recorded() > 0 || checked > 0) {
            log.info("startup assessments profile={} prefilterRecorded={} checksEvaluated={} (no LLM)", profile.id(),
                    prefilter.recorded(), checked);
        }
    }
}
