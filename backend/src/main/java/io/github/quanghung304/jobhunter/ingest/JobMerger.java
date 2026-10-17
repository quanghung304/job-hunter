package io.github.quanghung304.jobhunter.ingest;

import io.github.quanghung304.jobhunter.common.Hashing;
import io.github.quanghung304.jobhunter.common.text.TextNormalizer;
import io.github.quanghung304.jobhunter.ingest.persistence.Job;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import io.github.quanghung304.jobhunter.source.RemoteHint;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Creates jobs from postings and applies the merge rules of data-model.md §4. */
final class JobMerger {

    static final int MAX_DESCRIPTION_LENGTH = 30_000;

    /** Hosts of ATS job boards; their apply links beat aggregator board links. */
    private static final Set<String> ATS_HOSTS = Set.of("greenhouse.io", "lever.co", "ashbyhq.com");

    private JobMerger() {
    }

    static Job newJob(NormalizedPosting posting, String fingerprint, Instant now) {
        String description = truncate(posting.descriptionText());
        var job = new Job(fingerprint, posting.title().strip(), posting.companyName().strip(),
                TextNormalizer.companyKey(posting.companyName()),
                description, now, now);
        job.setDescriptionHash(Hashing.sha256Hex(description));
        job.setLocationText(posting.locationText());
        job.setRemoteHint(posting.remoteHint());
        job.setEmploymentHint(posting.employmentHint());
        job.setApplyUrl(posting.applyUrl() != null ? posting.applyUrl() : posting.url());
        copySalary(posting, job);
        job.setTags(posting.tags());
        job.setPostedAt(posting.postedAt() != null ? posting.postedAt() : now);
        return job;
    }

    /**
     * Merges a posting into an existing job: longest description, earliest posted_at,
     * structured salary if the job has none, ATS apply_url over board links, missing optional
     * fields filled, tags united. Title and company keep their first form.
     *
     * @return what changed
     */
    static Changes merge(Job job, NormalizedPosting posting) {
        boolean changed = false;
        boolean descriptionChanged = false;

        String description = truncate(posting.descriptionText());
        if (description.length() > job.getDescriptionText().length()) {
            job.setDescriptionText(description);
            String hash = Hashing.sha256Hex(description);
            descriptionChanged = !hash.equals(job.getDescriptionHash());
            job.setDescriptionHash(hash);
            changed = true;
        }
        if (posting.postedAt() != null && (job.getPostedAt() == null || posting.postedAt().isBefore(job.getPostedAt()))) {
            job.setPostedAt(posting.postedAt());
            changed = true;
        }
        if (!hasSalary(job) && hasSalary(posting)) {
            copySalary(posting, job);
            changed = true;
        }
        String applyUrl = posting.applyUrl() != null ? posting.applyUrl() : posting.url();
        if (job.getApplyUrl() == null || isAts(applyUrl) && !isAts(job.getApplyUrl())) {
            changed |= !Objects.equals(job.getApplyUrl(), applyUrl);
            job.setApplyUrl(applyUrl);
        }
        if (job.getLocationText() == null && posting.locationText() != null) {
            job.setLocationText(posting.locationText());
            changed = true;
        }
        if ((job.getRemoteHint() == null || job.getRemoteHint() == RemoteHint.UNKNOWN)
                && posting.remoteHint() != null && posting.remoteHint() != RemoteHint.UNKNOWN) {
            job.setRemoteHint(posting.remoteHint());
            changed = true;
        }
        if (job.getEmploymentHint() == null && posting.employmentHint() != null) {
            job.setEmploymentHint(posting.employmentHint());
            changed = true;
        }
        Set<String> tags = new LinkedHashSet<>(job.getTags());
        if (tags.addAll(posting.tags())) {
            job.setTags(tags.stream().toList());
            changed = true;
        }
        return new Changes(changed, descriptionChanged);
    }

    record Changes(boolean any, boolean description) {
    }

    static boolean isAts(String url) {
        if (url == null) {
            return false;
        }
        try {
            String host = URI.create(url.strip()).getHost();
            if (host == null) {
                return false;
            }
            String h = host.toLowerCase(Locale.ROOT);
            return ATS_HOSTS.stream().anyMatch(ats -> h.equals(ats) || h.endsWith("." + ats));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String truncate(String description) {
        String text = description.strip();
        return text.length() <= MAX_DESCRIPTION_LENGTH ? text : text.substring(0, MAX_DESCRIPTION_LENGTH);
    }

    private static boolean hasSalary(Job job) {
        return job.getSalaryMin() != null || job.getSalaryMax() != null;
    }

    private static boolean hasSalary(NormalizedPosting posting) {
        return posting.salaryMin() != null || posting.salaryMax() != null;
    }

    private static void copySalary(NormalizedPosting posting, Job job) {
        job.setSalaryMin(posting.salaryMin());
        job.setSalaryMax(posting.salaryMax());
        job.setSalaryCurrency(posting.salaryCurrency());
        job.setSalaryPeriod(posting.salaryPeriod());
    }
}
