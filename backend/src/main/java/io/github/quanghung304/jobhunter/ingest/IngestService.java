package io.github.quanghung304.jobhunter.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.quanghung304.jobhunter.common.Hashing;
import io.github.quanghung304.jobhunter.ingest.persistence.Job;
import io.github.quanghung304.jobhunter.ingest.persistence.JobRepository;
import io.github.quanghung304.jobhunter.ingest.persistence.RawPosting;
import io.github.quanghung304.jobhunter.ingest.persistence.RawPostingRepository;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stores collector output (data-model.md §4): upserts {@code raw_posting}, finds or creates the
 * deduplicated {@code job} by fingerprint and merges each posting into it. One transaction per
 * batch of {@value #BATCH_SIZE} postings.
 */
@Service
public class IngestService {

    static final int BATCH_SIZE = 50;
    /** A job not seen for longer than this is a new job when it reappears (re-posting). */
    static final Duration REPOST_AFTER = Duration.ofDays(60);

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final RawPostingRepository rawPostings;
    private final JobRepository jobs;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public IngestService(RawPostingRepository rawPostings, JobRepository jobs, ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.rawPostings = rawPostings;
        this.jobs = jobs;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public IngestResult ingest(List<NormalizedPosting> postings) {
        var counts = new Counts();
        for (int from = 0; from < postings.size(); from += BATCH_SIZE) {
            List<NormalizedPosting> batch = postings.subList(from, Math.min(from + BATCH_SIZE, postings.size()));
            transaction.executeWithoutResult(status -> ingestBatch(batch, counts));
        }
        IngestResult total = counts.toResult();
        if (!postings.isEmpty()) {
            log.info("ingest postings={} newRaw={} newJobs={} updatedJobs={} skippedInvalid={}", postings.size(),
                    total.newRaw(), total.newJobs(), total.updatedJobs(), total.skippedInvalid());
        }
        return total;
    }

    private void ingestBatch(List<NormalizedPosting> batch, Counts counts) {
        Instant now = clock.instant();
        for (NormalizedPosting posting : batch) {
            Optional<String> invalid = validate(posting);
            if (invalid.isPresent()) {
                counts.skippedInvalid++;
                log.debug("ingest skipped source={} id={}: {}", posting.sourceCode(), posting.externalId(), invalid.get());
                continue;
            }
            ingestOne(posting, now, counts);
        }
    }

    private void ingestOne(NormalizedPosting posting, Instant now, Counts counts) {
        String externalId = externalId(posting);
        JsonNode payload = posting.rawPayload() != null ? posting.rawPayload() : JsonNodeFactory.instance.objectNode();
        String payloadHash = Hashing.sha256Hex(payload.toString());

        RawPosting raw = rawPostings.findBySourceCodeAndExternalId(posting.sourceCode(), externalId).orElse(null);
        if (raw == null) {
            raw = new RawPosting(posting.sourceCode(), externalId, posting.url().strip(), payload, payloadHash, now, now);
            counts.newRaw++;
        } else {
            raw.setLastSeenAt(now);
            if (!raw.getPayloadHash().equals(payloadHash)) {
                raw.setPayload(payload);
                raw.setPayloadHash(payloadHash);
                raw.setUrl(posting.url().strip());
            }
        }

        Job job = raw.getJobId() != null ? jobs.findById(raw.getJobId()).orElse(null) : null;
        if (job == null) {
            job = resolveJob(posting, now);
        }
        if (job.getId() == null) {
            job = jobs.save(job);
            counts.createdJobIds.add(job.getId());
        } else {
            JobMerger.Changes changes = JobMerger.merge(job, posting);
            if (changes.any() && !counts.createdJobIds.contains(job.getId())) {
                counts.updatedJobIds.add(job.getId());
            }
            if (changes.description()) {
                events.publishEvent(new JobDescriptionChanged(job.getId()));
            }
            job.setLastSeenAt(now);
            job.setStatus(JobStatus.ACTIVE);
        }
        raw.setJobId(job.getId());
        rawPostings.save(raw);
    }

    /** The newest job of the fingerprint family if seen within 60 days, else a new (unsaved) job. */
    private Job resolveJob(NormalizedPosting posting, Instant now) {
        String fingerprint = Fingerprint.ofPosting(posting.companyName(), posting.title());
        List<Job> family = jobs.findFingerprintFamily(fingerprint);
        if (family.isEmpty()) {
            return JobMerger.newJob(posting, fingerprint, now);
        }
        Job latest = family.get(0);
        if (latest.getLastSeenAt().isBefore(now.minus(REPOST_AFTER))) {
            return JobMerger.newJob(posting, Fingerprint.repost(fingerprint, now), now);
        }
        return latest;
    }

    static Optional<String> validate(NormalizedPosting posting) {
        if (isBlank(posting.sourceCode())) {
            return Optional.of("blank sourceCode");
        }
        if (isBlank(posting.title())) {
            return Optional.of("blank title");
        }
        if (isBlank(posting.companyName())) {
            return Optional.of("blank companyName");
        }
        if (isBlank(posting.url())) {
            return Optional.of("blank url");
        }
        if (isBlank(posting.descriptionText())) {
            return Optional.of("blank descriptionText");
        }
        return Optional.empty();
    }

    /** The source's id, or sha256 of the URL when the source has none (data-model.md §1). */
    static String externalId(NormalizedPosting posting) {
        return isBlank(posting.externalId()) ? Hashing.sha256Hex(posting.url().strip()) : posting.externalId().strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Counts of one ingest call; jobs created in this call are never also counted as updated. */
    private static final class Counts {
        int newRaw;
        int skippedInvalid;
        final Set<Long> createdJobIds = new HashSet<>();
        final Set<Long> updatedJobIds = new HashSet<>();

        IngestResult toResult() {
            return new IngestResult(newRaw, createdJobIds.size(), updatedJobIds.size(), skippedInvalid);
        }
    }
}
