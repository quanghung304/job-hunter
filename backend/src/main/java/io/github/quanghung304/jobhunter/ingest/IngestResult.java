package io.github.quanghung304.jobhunter.ingest;

/**
 * Counts of one {@link IngestService#ingest} call.
 *
 * @param newRaw         raw_posting rows created
 * @param newJobs        job rows created (including re-postings)
 * @param updatedJobs    existing jobs whose content changed by a merge (not just last_seen_at)
 * @param skippedInvalid postings rejected by validation (blank title, company, url or description)
 */
public record IngestResult(int newRaw, int newJobs, int updatedJobs, int skippedInvalid) {

    public static final IngestResult EMPTY = new IngestResult(0, 0, 0, 0);

    public IngestResult plus(IngestResult other) {
        return new IngestResult(newRaw + other.newRaw, newJobs + other.newJobs,
                updatedJobs + other.updatedJobs, skippedInvalid + other.skippedInvalid);
    }
}
