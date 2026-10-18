package io.github.quanghung304.jobhunter.enrich;

import static io.github.quanghung304.jobhunter.enrich.EligibilityRule.Verdict.FAIL;
import static io.github.quanghung304.jobhunter.enrich.EligibilityRule.Verdict.OK;
import static io.github.quanghung304.jobhunter.enrich.EligibilityRule.Verdict.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.common.model.LanguageLevel;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.Seniority;
import io.github.quanghung304.jobhunter.enrich.EligibilityRule.Verdict;
import io.github.quanghung304.jobhunter.enrich.JobAttributes.Confidence;
import io.github.quanghung304.jobhunter.enrich.JobAttributes.RemoteType;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * scoring-spec.md §4: one row per cell of the rule table, plus the cases T-011 names. Profile:
 * the example (IT, Rome, Turin, vi NATIVE + en C1, PART_TIME/CONTRACT/FREELANCE, JUNIOR–SENIOR,
 * acceptRegions WORLDWIDE/EU/EEA/EUROPE/EMEA/IT/VN, d = 6); some rows use maxHoursPerWeek 30.
 */
class EligibilityEvaluatorTest {

    static final Instant SUMMER = Instant.parse("2026-07-15T10:00:00Z"); // Rome UTC+2
    static final Instant WINTER = Instant.parse("2026-01-15T10:00:00Z"); // Rome UTC+1
    static final Profile EXAMPLE = ProfileFixtures.example();
    static final Profile MAX_30_HOURS = withMaxHours(EXAMPLE, 30);

    static Stream<Arguments> rows() {
        return Stream.of(
                // WORK_MODE (critical)
                row("remote", a -> a, Eligibility.ELIGIBLE, EligibilityRule.WORK_MODE, OK),
                row("remote restricted", a -> a.remote(RemoteType.REMOTE_RESTRICTED), Eligibility.ELIGIBLE,
                        EligibilityRule.WORK_MODE, OK),
                row("remote type unknown", a -> a.remote(RemoteType.UNKNOWN), Eligibility.UNCERTAIN,
                        EligibilityRule.WORK_MODE, UNKNOWN),
                row("on-site Munich", a -> a.remote(RemoteType.ONSITE).location("Munich, Germany"),
                        Eligibility.INELIGIBLE, EligibilityRule.WORK_MODE, FAIL),
                row("hybrid Berlin", a -> a.remote(RemoteType.HYBRID).location("Berlin"), Eligibility.INELIGIBLE,
                        EligibilityRule.WORK_MODE, FAIL),
                row("on-site in my city (Torino)", a -> a.remote(RemoteType.ONSITE).location("Torino, Italy"),
                        Eligibility.ELIGIBLE, EligibilityRule.WORK_MODE, OK),
                // REGION (critical)
                row("EU", a -> a.regions("EU"), Eligibility.ELIGIBLE, EligibilityRule.REGION, OK),
                row("worldwide", a -> a.regions("WORLDWIDE"), Eligibility.ELIGIBLE, EligibilityRule.REGION, OK),
                row("Italy", a -> a.regions("IT"), Eligibility.ELIGIBLE, EligibilityRule.REGION, OK),
                row("US or EMEA", a -> a.regions("US", "EMEA"), Eligibility.ELIGIBLE, EligibilityRule.REGION, OK),
                row("no region stated", a -> a.regions(), Eligibility.UNCERTAIN, EligibilityRule.REGION, UNKNOWN),
                row("US only", a -> a.regions("US"), Eligibility.INELIGIBLE, EligibilityRule.REGION, FAIL),
                row("APAC", a -> a.regions("APAC"), Eligibility.INELIGIBLE, EligibilityRule.REGION, FAIL),
                // WORK_AUTH (critical; no UNKNOWN cell)
                row("no work authorization", a -> a, Eligibility.ELIGIBLE, EligibilityRule.WORK_AUTH, OK),
                row("authorized in the EU", a -> a.workAuth("Must be authorized to work in the EU"),
                        Eligibility.ELIGIBLE, EligibilityRule.WORK_AUTH, OK),
                row("authorized in the US", a -> a.workAuth("Must be authorized to work in the US"),
                        Eligibility.INELIGIBLE, EligibilityRule.WORK_AUTH, FAIL),
                row("UK citizens", a -> a.workAuth("Applicants must be UK citizens"), Eligibility.INELIGIBLE,
                        EligibilityRule.WORK_AUTH, FAIL),
                row("United States citizens", a -> a.workAuth("United States citizens only, no sponsorship"),
                        Eligibility.INELIGIBLE, EligibilityRule.WORK_AUTH, FAIL),
                row("'with us' is no country", a -> a.workAuth("Must be able to legally work with us"),
                        Eligibility.ELIGIBLE, EligibilityRule.WORK_AUTH, OK),
                // TIMEZONE (not critical; no UNKNOWN cell)
                row("no timezone", a -> a, Eligibility.ELIGIBLE, EligibilityRule.TIMEZONE, OK),
                row("CET±2, Rome summer", a -> a.timezone(-1, 3, null).at(SUMMER), Eligibility.ELIGIBLE,
                        EligibilityRule.TIMEZONE, OK),
                row("CET±2, Rome winter", a -> a.timezone(-1, 3, null).at(WINTER), Eligibility.ELIGIBLE,
                        EligibilityRule.TIMEZONE, OK),
                row("EST overlap 4h, d=6", a -> a.timezone(-5, -5, 4).at(WINTER), Eligibility.ELIGIBLE,
                        EligibilityRule.TIMEZONE, OK),
                row("PST only, d=6", a -> a.timezone(-8, -8, null).at(WINTER), Eligibility.INELIGIBLE,
                        EligibilityRule.TIMEZONE, FAIL),
                // EMPLOYMENT_TYPE (critical)
                row("contract", a -> a, Eligibility.ELIGIBLE, EligibilityRule.EMPLOYMENT_TYPE, OK),
                row("full-time or contract", a -> a.types(EmploymentType.FULL_TIME, EmploymentType.CONTRACT),
                        Eligibility.ELIGIBLE, EligibilityRule.EMPLOYMENT_TYPE, OK),
                row("types not stated", a -> a.types(), Eligibility.UNCERTAIN, EligibilityRule.EMPLOYMENT_TYPE,
                        UNKNOWN),
                row("full-time only, no hours (soft)", a -> a.types(EmploymentType.FULL_TIME), Eligibility.UNCERTAIN,
                        EligibilityRule.EMPLOYMENT_TYPE, UNKNOWN),
                row("full-time only + permanent", a -> a.types(EmploymentType.FULL_TIME)
                        .description("This is a permanent position."), Eligibility.INELIGIBLE,
                        EligibilityRule.EMPLOYMENT_TYPE, FAIL),
                row("full-time only + 40 h", a -> a.types(EmploymentType.FULL_TIME).hours(40, 40),
                        Eligibility.INELIGIBLE, EligibilityRule.EMPLOYMENT_TYPE, FAIL),
                row("internship", a -> a.types(EmploymentType.INTERNSHIP), Eligibility.INELIGIBLE,
                        EligibilityRule.EMPLOYMENT_TYPE, FAIL),
                // HOURS (not critical; no UNKNOWN cell)
                row("no hours stated", a -> a.profile(MAX_30_HOURS), Eligibility.ELIGIBLE, EligibilityRule.HOURS, OK),
                row("20 h with max 30", a -> a.hours(20, 25).profile(MAX_30_HOURS), Eligibility.ELIGIBLE,
                        EligibilityRule.HOURS, OK),
                row("40 h with max 30", a -> a.hours(40, null).profile(MAX_30_HOURS), Eligibility.INELIGIBLE,
                        EligibilityRule.HOURS, FAIL),
                row("40 h without a max", a -> a.hours(40, null), Eligibility.ELIGIBLE, EligibilityRule.HOURS, OK),
                // SENIORITY (not critical; no UNKNOWN cell)
                row("seniority not stated", a -> a.seniority(null), Eligibility.ELIGIBLE, EligibilityRule.SENIORITY, OK),
                row("senior", a -> a.seniority(Seniority.SENIOR), Eligibility.ELIGIBLE, EligibilityRule.SENIORITY, OK),
                row("lead", a -> a.seniority(Seniority.LEAD), Eligibility.INELIGIBLE, EligibilityRule.SENIORITY, FAIL),
                // LANGUAGE (critical; no UNKNOWN cell)
                row("English C1", a -> a.languages(lang("en", LanguageLevel.C1)), Eligibility.ELIGIBLE,
                        EligibilityRule.LANGUAGE, OK),
                row("no language", a -> a.languages(), Eligibility.ELIGIBLE, EligibilityRule.LANGUAGE, OK),
                row("Vietnamese C2 (native)", a -> a.languages(lang("vi", LanguageLevel.C2)), Eligibility.ELIGIBLE,
                        EligibilityRule.LANGUAGE, OK),
                row("German C1", a -> a.languages(lang("de", LanguageLevel.C1)), Eligibility.INELIGIBLE,
                        EligibilityRule.LANGUAGE, FAIL),
                row("native English", a -> a.languages(lang("en", LanguageLevel.NATIVE)), Eligibility.INELIGIBLE,
                        EligibilityRule.LANGUAGE, FAIL),
                // SCAM (critical; no UNKNOWN cell)
                row("no scam signal", a -> a, Eligibility.ELIGIBLE, EligibilityRule.SCAM, OK),
                row("scam signal", a -> a.scam(true), Eligibility.INELIGIBLE, EligibilityRule.SCAM, FAIL),
                // confidence cap
                row("low confidence caps at uncertain", a -> a.confidence(Confidence.LOW), Eligibility.UNCERTAIN,
                        EligibilityRule.SCAM, OK),
                row("low confidence keeps ineligible", a -> a.confidence(Confidence.LOW).regions("US"),
                        Eligibility.INELIGIBLE, EligibilityRule.REGION, FAIL));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("rows")
    void evaluates(String name, UnaryOperator<Case> change, Eligibility expected, EligibilityRule rule,
            Verdict verdict) {
        Case c = change.apply(new Case());

        EligibilityResult result = c.evaluate();

        assertThat(result.verdict(rule)).as("verdict of %s", rule).isEqualTo(verdict);
        assertThat(result.eligibility()).isEqualTo(expected);
    }

    @Test
    void reasonsListNonOkRulesAndSoftMarkers() {
        EligibilityResult soft = new Case().types(EmploymentType.FULL_TIME).regions().evaluate();
        EligibilityResult low = new Case().confidence(Confidence.LOW).seniority(Seniority.LEAD).evaluate();

        assertThat(soft.reasons()).containsExactly("REGION:UNKNOWN", "EMPLOYMENT_TYPE:UNKNOWN", "FULL_TIME_ONLY_SOFT");
        assertThat(low.reasons()).containsExactly("SENIORITY:FAIL", "CONFIDENCE:LOW");
        assertThat(new Case().evaluate().reasons()).isEmpty();
    }

    @Test
    void mentionedRegionsIgnoreLowerCaseAbbreviations() {
        assertThat(EligibilityEvaluator.mentionedRegions("Work with us from the U.S. or the EU"))
                .containsExactlyInAnyOrder("US", "EU");
        assertThat(EligibilityEvaluator.mentionedRegions("join us, we value you")).isEmpty();
    }

    static Arguments row(String name, UnaryOperator<Case> change, Eligibility expected, EligibilityRule rule,
            Verdict verdict) {
        return Arguments.of(name, change, expected, rule, verdict);
    }

    static JobAttributes.RequiredLanguage lang(String code, LanguageLevel level) {
        return new JobAttributes.RequiredLanguage(code, level);
    }

    static Profile withMaxHours(Profile profile, int maxHours) {
        Profile.Preferences p = profile.preferences();
        var preferences = new Profile.Preferences(p.employmentTypes(), maxHours, p.acceptRegions(), p.timezone(),
                p.minSalary(), p.seniority(), p.excludeCompanies());
        return new Profile(profile.id(), profile.summaryLanguage(), profile.candidate(), preferences,
                profile.prefilter(), profile.notifySettings(), profile.tracks());
    }

    /** A job that is ELIGIBLE for the example profile, changed per row. */
    static final class Case {
        RemoteType remote = RemoteType.FULLY_REMOTE;
        List<String> regions = List.of("EU");
        String workAuth;
        JobAttributes.TimezoneRequirement timezone;
        List<EmploymentType> types = List.of(EmploymentType.CONTRACT);
        JobAttributes.HoursPerWeek hours;
        Seniority seniority = Seniority.MID;
        List<JobAttributes.RequiredLanguage> languages = List.of(lang("en", LanguageLevel.B2));
        boolean scam;
        Confidence confidence = Confidence.HIGH;
        String location = "Remote";
        String description = "Build REST APIs with Java and Spring Boot.";
        Profile profile = EXAMPLE;
        Instant now = SUMMER;

        Case remote(RemoteType value) { remote = value; return this; }
        Case regions(String... value) { regions = List.of(value); return this; }
        Case workAuth(String value) { workAuth = value; return this; }
        Case timezone(int min, int max, Integer overlap) {
            timezone = new JobAttributes.TimezoneRequirement(min, max, overlap);
            return this;
        }
        Case types(EmploymentType... value) { types = List.of(value); return this; }
        Case hours(Integer min, Integer max) { hours = new JobAttributes.HoursPerWeek(min, max); return this; }
        Case seniority(Seniority value) { seniority = value; return this; }
        Case languages(JobAttributes.RequiredLanguage... value) { languages = List.of(value); return this; }
        Case scam(boolean value) { scam = value; return this; }
        Case confidence(Confidence value) { confidence = value; return this; }
        Case location(String value) { location = value; return this; }
        Case description(String value) { description = value; return this; }
        Case profile(Profile value) { profile = value; return this; }
        Case at(Instant value) { now = value; return this; }

        EligibilityResult evaluate() {
            var attributes = new JobAttributes(remote, regions, null, timezone, types, hours, seniority, 3,
                    List.of("Java"), List.of(), languages, workAuth, null, "Backend work.", false, scam, confidence);
            var job = new JobView(1L, "Java Developer", "Acme", "acme", location, RemoteHint.REMOTE, null, description,
                    null, null, null, null, null, List.of(), now, now, now, JobStatus.ACTIVE);
            return new EligibilityEvaluator(Clock.fixed(now, ZoneOffset.UTC)).evaluate(attributes, job, profile);
        }
    }
}
