package io.github.quanghung304.jobhunter.enrich;

import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.text.TextNormalizer;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.profile.Profile;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The rule-based prefilter of scoring-spec.md §2: no I/O, no LLM. Every rule is checked so
 * that all reasons can be shown; any reason means REJECT.
 */
@Component
public class Prefilter {

    private final Clock clock;

    public Prefilter(Clock clock) {
        this.clock = clock;
    }

    public PrefilterDecision evaluate(JobView job, Profile profile) {
        Profile.Prefilter rules = profile.prefilter();
        List<PrefilterReason> reasons = new ArrayList<>();
        if (tooOld(job, rules.maxAgeDays())) {
            reasons.add(PrefilterReason.TOO_OLD);
        }
        if (excludedCompany(job, profile.preferences().excludeCompanies())) {
            reasons.add(PrefilterReason.EXCLUDED_COMPANY);
        }
        if (anyFinds(rules.titleExclude(), job.title())) {
            reasons.add(PrefilterReason.TITLE_EXCLUDED);
        }
        if (!anyFinds(rules.titleInclude(), job.title())
                && skillHits(job, profile) < rules.minSkillHits()) {
            reasons.add(PrefilterReason.NOT_RELEVANT);
        }
        if (job.remoteHint() == RemoteHint.ONSITE && !CityNames.mentions(job.locationText(), profile.candidate().city())) {
            reasons.add(PrefilterReason.ONSITE_SOURCE_HINT);
        }
        return PrefilterDecision.of(reasons);
    }

    /** Jobs without {@code posted_at} are never too old. */
    private boolean tooOld(JobView job, int maxAgeDays) {
        Instant cutoff = clock.instant().minus(Duration.ofDays(maxAgeDays));
        return job.postedAt() != null && job.postedAt().isBefore(cutoff);
    }

    private static boolean excludedCompany(JobView job, List<String> excludeCompanies) {
        return excludeCompanies.stream()
                .map(TextNormalizer::companyKey)
                .anyMatch(key -> !key.isEmpty() && key.equals(job.companyKey()));
    }

    private static boolean anyFinds(List<Pattern> patterns, String text) {
        return text != null && patterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    /** Distinct core skills in the description. */
    private static int skillHits(JobView job, Profile profile) {
        return SkillMatcher.of(profile.candidate().skills().core()).hits(job.descriptionText()).size();
    }
}
