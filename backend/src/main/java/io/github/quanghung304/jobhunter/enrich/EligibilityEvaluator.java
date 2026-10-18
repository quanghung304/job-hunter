package io.github.quanghung304.jobhunter.enrich;

import static io.github.quanghung304.jobhunter.enrich.EligibilityRule.Verdict.FAIL;
import static io.github.quanghung304.jobhunter.enrich.EligibilityRule.Verdict.OK;
import static io.github.quanghung304.jobhunter.enrich.EligibilityRule.Verdict.UNKNOWN;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.enrich.EligibilityRule.Verdict;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.RegionMatcher;
import io.github.quanghung304.jobhunter.profile.RegionVerdict;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The deterministic eligibility check of scoring-spec.md §4: every rule yields OK, UNKNOWN or
 * FAIL; any FAIL → INELIGIBLE, else any UNKNOWN on a critical rule → UNCERTAIN, else ELIGIBLE;
 * extraction confidence LOW caps the result at UNCERTAIN. No I/O; the clock only fixes the
 * date for the DST-aware residence offset.
 */
@Component
public class EligibilityEvaluator {

    /** Text that makes a FULL_TIME-only posting a real employment (scoring-spec §4 note). */
    private static final Pattern EMPLOYMENT_WORDS =
            Pattern.compile("\\b(permanent|employee)\\b", Pattern.CASE_INSENSITIVE);
    static final int FULL_TIME_MIN_HOURS = 35;

    /**
     * Country names and adjectives found in work-authorization sentences → region token.
     * A simple match (§4 WORK_AUTH); text naming no listed country is ignored.
     */
    static final Map<String, String> COUNTRY_WORDS = Map.ofEntries(
            Map.entry("united states", "US"), Map.entry("u.s.", "US"), Map.entry("american", "US"),
            Map.entry("united kingdom", "GB"), Map.entry("british", "GB"),
            Map.entry("great britain", "GB"), Map.entry("england", "GB"),
            Map.entry("canada", "CA"), Map.entry("canadian", "CA"),
            Map.entry("australia", "AU"), Map.entry("australian", "AU"),
            Map.entry("new zealand", "NZ"), Map.entry("india", "IN"), Map.entry("indian", "IN"),
            Map.entry("germany", "DE"), Map.entry("german", "DE"), Map.entry("france", "FR"),
            Map.entry("french", "FR"), Map.entry("spain", "ES"), Map.entry("netherlands", "NL"),
            Map.entry("dutch", "NL"), Map.entry("italy", "IT"), Map.entry("italian", "IT"),
            Map.entry("ireland", "IE"), Map.entry("switzerland", "CH"), Map.entry("swiss", "CH"),
            Map.entry("poland", "PL"), Map.entry("portugal", "PT"), Map.entry("sweden", "SE"),
            Map.entry("vietnam", "VN"), Map.entry("brazil", "BR"), Map.entry("mexico", "MX"),
            Map.entry("singapore", "SG"), Map.entry("japan", "JP"), Map.entry("israel", "IL"),
            Map.entry("european union", "EU"), Map.entry("europe", "EUROPE"));

    /** Abbreviations matched only in upper case ("work with us" must not mean the US). */
    static final Map<String, String> UPPER_CASE_CODES = Map.of(
            "US", "US", "USA", "US", "UK", "GB", "EU", "EU", "EEA", "EEA");
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+");

    private final Clock clock;

    public EligibilityEvaluator(Clock clock) {
        this.clock = clock;
    }

    public EligibilityResult evaluate(JobAttributes attributes, JobView job, Profile profile) {
        Map<EligibilityRule, Verdict> verdicts = new EnumMap<>(EligibilityRule.class);
        verdicts.put(EligibilityRule.WORK_MODE, workMode(attributes, job, profile));
        verdicts.put(EligibilityRule.REGION, region(attributes, profile));
        verdicts.put(EligibilityRule.WORK_AUTH, workAuthorization(attributes, profile));
        verdicts.put(EligibilityRule.TIMEZONE, timezone(attributes, profile));
        EmploymentCheck employment = employmentType(attributes, job, profile);
        verdicts.put(EligibilityRule.EMPLOYMENT_TYPE, employment.verdict());
        verdicts.put(EligibilityRule.HOURS, hours(attributes, profile));
        verdicts.put(EligibilityRule.SENIORITY, seniority(attributes, profile));
        verdicts.put(EligibilityRule.LANGUAGE, language(attributes, profile));
        verdicts.put(EligibilityRule.SCAM, Boolean.TRUE.equals(attributes.isScamSignal()) ? FAIL : OK);

        Eligibility eligibility;
        if (verdicts.containsValue(FAIL)) {
            eligibility = Eligibility.INELIGIBLE;
        } else if (verdicts.entrySet().stream().anyMatch(e -> e.getKey().critical() && e.getValue() == UNKNOWN)) {
            eligibility = Eligibility.UNCERTAIN;
        } else {
            eligibility = Eligibility.ELIGIBLE;
        }
        boolean lowConfidence = attributes.confidence() == JobAttributes.Confidence.LOW;
        if (lowConfidence && eligibility == Eligibility.ELIGIBLE) {
            eligibility = Eligibility.UNCERTAIN;
        }
        return new EligibilityResult(eligibility, verdicts, employment.fullTimeOnlySoft(), lowConfidence);
    }

    private static Verdict workMode(JobAttributes attributes, JobView job, Profile profile) {
        return switch (attributes.remoteType()) {
            case FULLY_REMOTE, REMOTE_RESTRICTED -> OK;
            case UNKNOWN -> UNKNOWN;
            case HYBRID, ONSITE -> CityNames.mentions(job.locationText(), profile.candidate().city()) ? OK : FAIL;
        };
    }

    private static Verdict region(JobAttributes attributes, Profile profile) {
        RegionVerdict verdict = RegionMatcher.of(profile).isAccepted(attributes.allowedRegions());
        return switch (verdict) {
            case OK -> OK;
            case UNKNOWN -> UNKNOWN;
            case FAIL -> FAIL;
        };
    }

    /** FAIL only when the sentence names countries/regions and none of them is acceptable. */
    static Verdict workAuthorization(JobAttributes attributes, Profile profile) {
        String text = attributes.workAuthorization();
        if (text == null || text.isBlank()) {
            return OK;
        }
        Set<String> regions = mentionedRegions(text);
        if (regions.isEmpty()) {
            return OK;
        }
        RegionMatcher matcher = RegionMatcher.of(profile);
        boolean anyAccepted = regions.stream().anyMatch(r -> matcher.isAccepted(List.of(r)) == RegionVerdict.OK);
        return anyAccepted ? OK : FAIL;
    }

    static Set<String> mentionedRegions(String text) {
        String lower = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z.]+", " ") + " ";
        Set<String> regions = new LinkedHashSet<>();
        COUNTRY_WORDS.forEach((word, token) -> {
            if (lower.contains(" " + word + " ")) {
                regions.add(token);
            }
        });
        var words = WORD.matcher(text);
        while (words.find()) {
            String token = UPPER_CASE_CODES.get(words.group());
            if (token != null) {
                regions.add(token);
            }
        }
        return regions;
    }

    /**
     * OK if {@code minUtcOffset − d ≤ residence offset ≤ maxUtcOffset + d}, with the residence
     * offset of today in the candidate's time zone (DST-aware).
     */
    private Verdict timezone(JobAttributes attributes, Profile profile) {
        JobAttributes.TimezoneRequirement requirement = attributes.timezoneRequirement();
        if (requirement == null) {
            return OK;
        }
        ZoneOffset offset = profile.candidate().timezone().getRules().getOffset(clock.instant());
        double hours = offset.getTotalSeconds() / 3600.0;
        int d = profile.preferences().timezone().maxOffsetDiffHours();
        return requirement.minUtcOffset() - d <= hours && hours <= requirement.maxUtcOffset() + d ? OK : FAIL;
    }

    private static EmploymentCheck employmentType(JobAttributes attributes, JobView job, Profile profile) {
        List<EmploymentType> offered = attributes.employmentTypes();
        if (offered.isEmpty()) {
            return new EmploymentCheck(UNKNOWN, false);
        }
        List<EmploymentType> accepted = profile.preferences().employmentTypes();
        if (offered.stream().anyMatch(accepted::contains)) {
            return new EmploymentCheck(OK, false);
        }
        boolean fullTimeOnly = offered.stream().allMatch(t -> t == EmploymentType.FULL_TIME);
        if (fullTimeOnly) {
            // Contract roles are often posted with a "full-time" template (scoring-spec §4 note).
            JobAttributes.HoursPerWeek hours = attributes.hoursPerWeek();
            boolean clearlyEmployment = (hours != null && hours.min() != null && hours.min() >= FULL_TIME_MIN_HOURS)
                    || (job.descriptionText() != null && EMPLOYMENT_WORDS.matcher(job.descriptionText()).find());
            return clearlyEmployment ? new EmploymentCheck(FAIL, false) : new EmploymentCheck(UNKNOWN, true);
        }
        return new EmploymentCheck(FAIL, false);
    }

    private static Verdict hours(JobAttributes attributes, Profile profile) {
        Integer maxHours = profile.preferences().maxHoursPerWeek();
        JobAttributes.HoursPerWeek hours = attributes.hoursPerWeek();
        if (maxHours == null || hours == null || hours.min() == null) {
            return OK;
        }
        return hours.min() <= maxHours ? OK : FAIL;
    }

    private static Verdict seniority(JobAttributes attributes, Profile profile) {
        return attributes.seniority() == null || profile.preferences().seniority().contains(attributes.seniority())
                ? OK : FAIL;
    }

    private static Verdict language(JobAttributes attributes, Profile profile) {
        for (JobAttributes.RequiredLanguage required : attributes.requiredLanguages()) {
            boolean met = profile.candidate().languages().stream()
                    .anyMatch(spoken -> spoken.code().equalsIgnoreCase(required.code())
                            && spoken.level().isAtLeast(required.level()));
            if (!met) {
                return FAIL;
            }
        }
        return OK;
    }

    private record EmploymentCheck(Verdict verdict, boolean fullTimeOnlySoft) {
    }
}
