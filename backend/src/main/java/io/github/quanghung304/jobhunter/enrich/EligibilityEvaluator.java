package io.github.quanghung304.jobhunter.enrich;

import static io.github.quanghung304.jobhunter.enrich.CheckResult.FAIL;
import static io.github.quanghung304.jobhunter.enrich.CheckResult.OK;
import static io.github.quanghung304.jobhunter.enrich.CheckResult.UNKNOWN;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.RegionMatcher;
import io.github.quanghung304.jobhunter.profile.RegionVerdict;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The deterministic eligibility checks of scoring-spec.md §4: one check per criterion (OK,
 * UNKNOWN or FAIL, with the job-side value and an optional reason code) in table order. There is
 * no overall verdict: the owner decides (ADR-0011). No I/O; the clock only fixes the date for the
 * DST-aware residence offset.
 */
@Component
public class EligibilityEvaluator {

    static final String NOT_STATED = "Not stated";

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

    /** All checks in §4 table order (one per {@link EligibilityRule}). */
    public List<EligibilityCheck> evaluate(JobAttributes attributes, JobView job, Profile profile) {
        return List.of(
                workMode(attributes, job, profile),
                region(attributes, profile),
                workAuthorization(attributes, profile),
                timezone(attributes, profile),
                employmentType(attributes, job, profile),
                hours(attributes, profile),
                seniority(attributes, profile),
                language(attributes, profile),
                scam(attributes),
                confidence(attributes));
    }

    private static EligibilityCheck workMode(JobAttributes attributes, JobView job, Profile profile) {
        String value = remoteText(attributes.remoteType())
                + (job.locationText() == null || job.locationText().isBlank() ? "" : " · " + job.locationText());
        CheckResult result = switch (attributes.remoteType()) {
            case FULLY_REMOTE, REMOTE_RESTRICTED -> OK;
            case UNKNOWN -> UNKNOWN;
            case HYBRID, ONSITE -> CityNames.mentions(job.locationText(), profile.candidate().city()) ? OK : FAIL;
        };
        return EligibilityCheck.of(EligibilityRule.WORK_MODE, result, value);
    }

    private static String remoteText(JobAttributes.RemoteType type) {
        return switch (type) {
            case FULLY_REMOTE -> "Remote anywhere";
            case REMOTE_RESTRICTED -> "Remote (restricted)";
            case HYBRID -> "Hybrid";
            case ONSITE -> "On-site";
            case UNKNOWN -> NOT_STATED;
        };
    }

    private static EligibilityCheck region(JobAttributes attributes, Profile profile) {
        RegionVerdict verdict = RegionMatcher.of(profile).isAccepted(attributes.allowedRegions());
        CheckResult result = switch (verdict) {
            case OK -> OK;
            case UNKNOWN -> UNKNOWN;
            case FAIL -> FAIL;
        };
        String value = attributes.allowedRegions().isEmpty() ? NOT_STATED : String.join(", ", attributes.allowedRegions());
        return EligibilityCheck.of(EligibilityRule.REGION, result, value);
    }

    /** FAIL only when the sentence names countries/regions and none of them is acceptable. */
    static EligibilityCheck workAuthorization(JobAttributes attributes, Profile profile) {
        String text = attributes.workAuthorization();
        if (text == null || text.isBlank()) {
            return EligibilityCheck.of(EligibilityRule.WORK_AUTH, OK, NOT_STATED);
        }
        Set<String> regions = mentionedRegions(text);
        if (regions.isEmpty()) {
            return EligibilityCheck.of(EligibilityRule.WORK_AUTH, OK, text);
        }
        RegionMatcher matcher = RegionMatcher.of(profile);
        boolean anyAccepted = regions.stream().anyMatch(r -> matcher.isAccepted(List.of(r)) == RegionVerdict.OK);
        return EligibilityCheck.of(EligibilityRule.WORK_AUTH, anyAccepted ? OK : FAIL, text);
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
    private EligibilityCheck timezone(JobAttributes attributes, Profile profile) {
        JobAttributes.TimezoneRequirement requirement = attributes.timezoneRequirement();
        if (requirement == null) {
            return EligibilityCheck.of(EligibilityRule.TIMEZONE, OK, NOT_STATED);
        }
        ZoneOffset offset = profile.candidate().timezone().getRules().getOffset(clock.instant());
        double hours = offset.getTotalSeconds() / 3600.0;
        int d = profile.preferences().timezone().maxOffsetDiffHours();
        boolean ok = requirement.minUtcOffset() - d <= hours && hours <= requirement.maxUtcOffset() + d;
        return EligibilityCheck.of(EligibilityRule.TIMEZONE, ok ? OK : FAIL, timezoneText(requirement));
    }

    static String timezoneText(JobAttributes.TimezoneRequirement requirement) {
        String range = requirement.minUtcOffset().equals(requirement.maxUtcOffset())
                ? utc(requirement.minUtcOffset())
                : utc(requirement.minUtcOffset()) + " to " + utc(requirement.maxUtcOffset());
        return requirement.overlapHours() == null ? range : range + ", " + requirement.overlapHours() + "h overlap";
    }

    private static String utc(int offset) {
        return offset == 0 ? "UTC" : offset > 0 ? "UTC+" + offset : "UTC" + offset;
    }

    private static EligibilityCheck employmentType(JobAttributes attributes, JobView job, Profile profile) {
        List<EmploymentType> offered = attributes.employmentTypes();
        if (offered.isEmpty()) {
            return EligibilityCheck.of(EligibilityRule.EMPLOYMENT_TYPE, UNKNOWN, NOT_STATED);
        }
        String value = offered.stream().map(EligibilityEvaluator::employmentText).collect(Collectors.joining(", "));
        List<EmploymentType> accepted = profile.preferences().employmentTypes();
        if (offered.stream().anyMatch(accepted::contains)) {
            return EligibilityCheck.of(EligibilityRule.EMPLOYMENT_TYPE, OK, value);
        }
        boolean fullTimeOnly = offered.stream().allMatch(t -> t == EmploymentType.FULL_TIME);
        if (fullTimeOnly) {
            // Contract roles are often posted with a "full-time" template (scoring-spec §4 note).
            JobAttributes.HoursPerWeek hours = attributes.hoursPerWeek();
            boolean clearlyEmployment = (hours != null && hours.min() != null && hours.min() >= FULL_TIME_MIN_HOURS)
                    || (job.descriptionText() != null && EMPLOYMENT_WORDS.matcher(job.descriptionText()).find());
            return clearlyEmployment
                    ? EligibilityCheck.of(EligibilityRule.EMPLOYMENT_TYPE, FAIL, value)
                    : EligibilityCheck.of(EligibilityRule.EMPLOYMENT_TYPE, UNKNOWN, value)
                            .withReason(EligibilityCheck.FULL_TIME_ONLY_SOFT);
        }
        return EligibilityCheck.of(EligibilityRule.EMPLOYMENT_TYPE, FAIL, value);
    }

    private static String employmentText(EmploymentType type) {
        String name = type.name().replace('_', '-').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static EligibilityCheck hours(JobAttributes attributes, Profile profile) {
        Integer maxHours = profile.preferences().maxHoursPerWeek();
        JobAttributes.HoursPerWeek hours = attributes.hoursPerWeek();
        String value = hoursText(hours);
        if (maxHours == null || hours == null || hours.min() == null) {
            return EligibilityCheck.of(EligibilityRule.HOURS, OK, value);
        }
        return EligibilityCheck.of(EligibilityRule.HOURS, hours.min() <= maxHours ? OK : FAIL, value);
    }

    private static String hoursText(JobAttributes.HoursPerWeek hours) {
        if (hours == null || (hours.min() == null && hours.max() == null)) {
            return NOT_STATED;
        }
        if (hours.min() != null && hours.max() != null) {
            return hours.min().equals(hours.max()) ? hours.min() + " h/week" : hours.min() + "–" + hours.max() + " h/week";
        }
        return hours.min() != null ? "≥ " + hours.min() + " h/week" : "≤ " + hours.max() + " h/week";
    }

    private static EligibilityCheck seniority(JobAttributes attributes, Profile profile) {
        if (attributes.seniority() == null) {
            return EligibilityCheck.of(EligibilityRule.SENIORITY, OK, NOT_STATED);
        }
        boolean ok = profile.preferences().seniority().contains(attributes.seniority());
        String name = attributes.seniority().name().toLowerCase(Locale.ROOT);
        return EligibilityCheck.of(EligibilityRule.SENIORITY, ok ? OK : FAIL,
                Character.toUpperCase(name.charAt(0)) + name.substring(1));
    }

    private static EligibilityCheck language(JobAttributes attributes, Profile profile) {
        List<JobAttributes.RequiredLanguage> required = attributes.requiredLanguages();
        if (required.isEmpty()) {
            return EligibilityCheck.of(EligibilityRule.LANGUAGE, OK, "None required");
        }
        String value = required.stream()
                .map(l -> l.code().toUpperCase(Locale.ROOT) + " " + l.level())
                .collect(Collectors.joining(", "));
        for (JobAttributes.RequiredLanguage language : required) {
            boolean met = profile.candidate().languages().stream()
                    .anyMatch(spoken -> spoken.code().equalsIgnoreCase(language.code())
                            && spoken.level().isAtLeast(language.level()));
            if (!met) {
                return EligibilityCheck.of(EligibilityRule.LANGUAGE, FAIL, value);
            }
        }
        return EligibilityCheck.of(EligibilityRule.LANGUAGE, OK, value);
    }

    private static EligibilityCheck scam(JobAttributes attributes) {
        return Boolean.TRUE.equals(attributes.isScamSignal())
                ? EligibilityCheck.of(EligibilityRule.SCAM, FAIL, "Scam signals")
                : EligibilityCheck.of(EligibilityRule.SCAM, OK, "No signals");
    }

    private static EligibilityCheck confidence(JobAttributes attributes) {
        return switch (attributes.confidence()) {
            case HIGH -> EligibilityCheck.of(EligibilityRule.EXTRACTION_CONFIDENCE, OK, "High");
            case MEDIUM -> EligibilityCheck.of(EligibilityRule.EXTRACTION_CONFIDENCE, OK, "Medium");
            case LOW -> EligibilityCheck.of(EligibilityRule.EXTRACTION_CONFIDENCE, UNKNOWN, "Low");
        };
    }
}
