package io.github.quanghung304.jobhunter.ingest;

import io.github.quanghung304.jobhunter.ingest.persistence.Job;
import io.github.quanghung304.jobhunter.ingest.persistence.JobRepository;
import io.github.quanghung304.jobhunter.ingest.persistence.RawPostingRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to jobs for other modules; returns {@link JobView}s, never entities. */
@Service
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobRepository jobs;
    private final RawPostingRepository postings;

    public JobQueryService(JobRepository jobs, RawPostingRepository postings) {
        this.jobs = jobs;
        this.postings = postings;
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

    /** Ids of ACTIVE jobs, ascending. */
    public List<Long> activeJobIds() {
        return List.copyOf(jobs.findIdsByStatus(JobStatus.ACTIVE));
    }

    /** Ids of jobs first seen at or after {@code since} (new jobs of a run), ascending. */
    public List<Long> idsFirstSeenSince(Instant since) {
        return List.copyOf(jobs.findIdsFirstSeenSince(since));
    }

    /** The sources listing this job, oldest first. */
    public List<PostingView> postings(long jobId) {
        return postings.findByJobIdOrderByFirstSeenAtAscIdAsc(jobId).stream()
                .map(p -> new PostingView(p.getSourceCode(), p.getUrl(), p.getFirstSeenAt(), p.getLastSeenAt()))
                .toList();
    }

    public long countActive() {
        return jobs.countByStatus(JobStatus.ACTIVE);
    }

    /** Jobs first seen at or after {@code since}. */
    public long countFirstSeenSince(Instant since) {
        return jobs.countByFirstSeenAtGreaterThanEqual(since);
    }

    /** ACTIVE jobs per company key ({@code TextNormalizer.companyKey}); keys without jobs map to 0. */
    public Map<String, Long> countActiveByCompanyKey(Collection<String> companyKeys) {
        Map<String, Long> result = new HashMap<>();
        companyKeys.forEach(key -> result.put(key, 0L));
        if (!companyKeys.isEmpty()) {
            for (Object[] row : jobs.countByCompanyKeys(JobStatus.ACTIVE, companyKeys)) {
                result.put((String) row[0], ((Number) row[1]).longValue());
            }
        }
        return result;
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
