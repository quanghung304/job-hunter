package io.github.quanghung304.jobhunter.ingest;

import io.github.quanghung304.jobhunter.ingest.persistence.JobRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Job expiry (data-model {@code job.status}): ACTIVE jobs not seen in any source for a while. */
@Service
public class JobExpiryService {

    private final JobRepository jobs;

    public JobExpiryService(JobRepository jobs) {
        this.jobs = jobs;
    }

    /** Marks ACTIVE jobs whose {@code last_seen_at} is before {@code cutoff} as EXPIRED. */
    @Transactional
    public int expireNotSeenSince(Instant cutoff) {
        return jobs.expireNotSeenSince(cutoff, JobStatus.ACTIVE, JobStatus.EXPIRED);
    }
}
