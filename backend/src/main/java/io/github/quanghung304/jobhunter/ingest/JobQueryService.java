package io.github.quanghung304.jobhunter.ingest;

import io.github.quanghung304.jobhunter.ingest.persistence.Job;
import io.github.quanghung304.jobhunter.ingest.persistence.JobRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to jobs for other modules; returns {@link JobView}s, never entities. */
@Service
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobRepository jobs;

    public JobQueryService(JobRepository jobs) {
        this.jobs = jobs;
    }

    public Optional<JobView> find(long jobId) {
        return jobs.findById(jobId).map(JobQueryService::toView);
    }

    /** The existing jobs among {@code jobIds}, ordered by id; unknown ids are skipped. */
    public List<JobView> findAll(Collection<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        return jobs.findAllByIdInOrderById(jobIds).stream().map(JobQueryService::toView).toList();
    }

    static JobView toView(Job job) {
        return new JobView(
                job.getId(),
                job.getTitle(),
                job.getCompanyName(),
                job.getCompanyKey(),
                job.getLocationText(),
                job.getRemoteHint(),
                job.getEmploymentHint(),
                job.getDescriptionText(),
                job.getApplyUrl(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.getSalaryPeriod(),
                job.getTags(),
                job.getPostedAt(),
                job.getFirstSeenAt(),
                job.getLastSeenAt(),
                job.getStatus());
    }
}
