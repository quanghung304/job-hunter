package io.github.quanghung304.jobhunter.enrich;

import static io.github.quanghung304.jobhunter.enrich.CheckResult.FAIL;
import static io.github.quanghung304.jobhunter.enrich.CheckResult.OK;
import static io.github.quanghung304.jobhunter.enrich.CheckResult.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.common.model.LanguageLevel;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.Seniority;
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
 * scoring-spec.md §4: one row per cell of the check table, plus the cases T-011 and T-025 name.
 * There is no overall verdict (ADR-0011): each row asserts the result of one check. Profile:
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
                row("remote", a -> a, EligibilityRule.WORK_MODE, OK),
                row("remote restricted", a -> a.remote(RemoteType.REMOTE_RESTRICTED), EligibilityRule.WORK_MODE, OK),
                row("remote type unknown", a -> a.remote(RemoteType.UNKNOWN), EligibilityRule.WORK_MODE, UNKNOWN),
                row("on-site Munich", a -> a.remote(RemoteType.ONSITE).location("Munich, Germany"),
                        EligibilityRule.WORK_MODE, FAIL),
                row("hybrid Berlin", a -> a.remote(RemoteType.HYBRID).location("Berlin"), EligibilityRule.WORK_MODE, FAIL),
                row("on-site in my city (Torino)", a -> a.remote(RemoteType.ONSITE).location("Torino, Italy"),
                        EligibilityRule.WORK_MODE, OK),
                // REGION (critical)
                row("EU", a -> a.regions("EU"), EligibilityRule.REGION, OK),
                row("worldwide", a -> a.regions("WORLDWIDE"), EligibilityRule.REGION, OK),
                row("Italy", a -> a.regions("IT"), EligibilityRule.REGION, OK),
                row("US or EMEA", a -> a.regions("US", "EMEA"), EligibilityRule.REGION, OK),
                row("no region stated", a -> a.regions(), EligibilityRule.REGION, UNKNOWN),
                row("US only", a -> a.regions("US"), EligibilityRule.REGION, FAIL),
                row("APAC", a -> a.regions("APAC"), EligibilityRule.REGION, FAIL),
                // WORK_AUTH (critical; no UNKNOWN cell)
                row("no work authorization", a -> a, EligibilityRule.WORK_AUTH, OK),
                row("authorized in the EU", a -> a.workAuth("Must be authorized to work in the EU"),
                        EligibilityRule.WORK_AUTH, OK),
                row("authorized in the US", a -> a.workAuth("Must be authorized to work in the US"),
                        EligibilityRule.WORK_AUTH, FAIL),
                row("UK citizens", a -> a.workAuth("Applicants must be UK citizens"), EligibilityRule.WORK_AUTH, FAIL),
                row("United States citizens", a -> a.workAuth("United States citizens only, no sponsorship"),
                        EligibilityRule.WORK_AUTH, FAIL),
                row("'with us' is no country", a -> a.workAuth("Must be able to legally work with us"),
                        EligibilityRule.WORK_AUTH, OK),
                // TIMEZONE (not critical; no UNKNOWN cell)
                row("no timezone", a -> a, EligibilityRule.TIMEZONE, OK),
                row("CET±2, Rome summer", a -> a.timezone(-1, 3, null).at(SUMMER), EligibilityRule.TIMEZONE, OK),
                row("CET±2, Rome winter", a -> a.timezone(-1, 3, null).at(WINTER), EligibilityRule.TIMEZONE, OK),
                row("EST overlap 4h, d=6", a -> a.timezone(-5, -5, 4).at(WINTER), EligibilityRule.TIMEZONE, OK),
                row("PST only, d=6", a -> a.timezone(-8, -8, null).at(WINTER), EligibilityRule.TIMEZONE, FAIL),
                // EMPLOYMENT_TYPE (critical)
                row("contract", a -> a, EligibilityRule.EMPLOYMENT_TYPE, OK),
                row("full-time or contract", a -> a.types(EmploymentType.FULL_TIME, EmploymentType.CONTRACT),
                        EligibilityRule.EMPLOYMENT_TYPE, OK),
                row("types not stated", a -> a.types(), EligibilityRule.EMPLOYMENT_TYPE,
                        UNKNOWN),
                row("full-time only, no hours (soft)", a -> a.types(EmploymentType.FULL_TIME), EligibilityRule.EMPLOYMENT_TYPE, UNKNOWN),
                row("full-time only + permanent", a -> a.types(EmploymentType.FULL_TIME)
                        .description("This is a permanent position."), EligibilityRule.EMPLOYMENT_TYPE, FAIL),
                row("full-time only + 40 h", a -> a.types(EmploymentType.FULL_TIME).hours(40, 40),
                        EligibilityRule.EMPLOYMENT_TYPE, FAIL),
                row("internship", a -> a.types(EmploymentType.INTERNSHIP), EligibilityRule.EMPLOYMENT_TYPE, FAIL),
                // HOURS (not critical; no UNKNOWN cell)
                row("no hours stated", a -> a.profile(MAX_30_HOURS), EligibilityRule.HOURS, OK),
                row("20 h with max 30", a -> a.hours(20, 25).profile(MAX_30_HOURS), EligibilityRule.HOURS, OK),
                row("40 h with max 30", a -> a.hours(40, null).profile(MAX_30_HOURS), EligibilityRule.HOURS, FAIL),
                row("40 h without a max", a -> a.hours(40, null), EligibilityRule.HOURS, OK),
                // SENIORITY (not critical; no UNKNOWN cell)
                row("seniority not stated", a -> a.seniority(null), EligibilityRule.SENIORITY, OK),
                row("senior", a -> a.seniority(Seniority.SENIOR), EligibilityRule.SENIORITY, OK),
                row("lead", a -> a.seniority(Seniority.LEAD), EligibilityRule.SENIORITY, FAIL),
                // LANGUAGE (critical; no UNKNOWN cell)
                row("English C1", a -> a.languages(lang("en", LanguageLevel.C1)), EligibilityRule.LANGUAGE, OK),
                row("no language", a -> a.languages(), EligibilityRule.LANGUAGE, OK),
                row("Vietnamese C2 (native)", a -> a.languages(lang("vi", LanguageLevel.C2)), EligibilityRule.LANGUAGE, OK),
                row("German C1", a -> a.languages(lang("de", LanguageLevel.C1)), EligibilityRule.LANGUAGE, FAIL),
                row("native English", a -> a.languages(lang("en", LanguageLevel.NATIVE)), EligibilityRule.LANGUAGE, FAIL),
                // SCAM (critical; no UNKNOWN cell)
                row("no scam signal", a -> a, EligibilityRule.SCAM, OK),
                row("scam signal", a -> a.scam(true), EligibilityRule.SCAM, FAIL),
                // EXTRACTION_CONFIDENCE (critical; no FAIL cell)
                row("high confidence", a -> a, EligibilityRule.EXTRACTION_CONFIDENCE, OK),
                row("medium confidence", a -> a.confidence(Confidence.MEDIUM), EligibilityRule.EXTRACTION_CONFIDENCE,
                        OK),
                row("low confidence", a -> a.confidence(Confidence.LOW), EligibilityRule.EXTRACTION_CONFIDENCE,
                        UNKNOWN));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("rows")
    void evaluates(String name, UnaryOperator<Case> change, EligibilityRule rule, CheckResult result) {
        Case c = change.apply(new Case());

        List<EligibilityCheck> checks = c.evaluate();

        assertThat(check(checks, rule).result()).as("result of %s", rule).isEqualTo(result);
    }

    @Test
    void returnsOneCheckPerCriterionInTableOrderAndNoVerdict() {
        List<EligibilityCheck> checks = new Case().evaluate();

        assertThat(checks).extracting(EligibilityCheck::code).containsExactly(EligibilityRule.values());
        assertThat(checks).allSatisfy(c -> {
            assertThat(c.result()).isEqualTo(OK);
            assertThat(c.critical()).isEqualTo(c.code().critical());
            assertThat(c.value()).isNotBlank();
        });
        assertThat(EligibilityCheck.class.getRecordComponents()).extracting(c -> c.getName())
                .containsExactly("code", "critical", "result", "reason", "value");
    }

    @Test
    void fullTimeOnlyWithoutHoursIsUnknownWithTheSoftReason() {
        EligibilityCheck employment = check(new Case().types(EmploymentType.FULL_TIME).evaluate(),
                EligibilityRule.EMPLOYMENT_TYPE);

        assertThat(employment.result()).isEqualTo(UNKNOWN);
        assertThat(employment.reason()).isEqualTo(EligibilityCheck.FULL_TIME_ONLY_SOFT);
        assertThat(employment.value()).isEqualTo("Full-time");
        assertThat(check(new Case().evaluate(), EligibilityRule.EMPLOYMENT_TYPE).reason()).isNull();
    }

    @Test
    void valuesDescribeTheJobSide() {
        List<EligibilityCheck> stated = new Case().regions("EU", "IT").timezone(-1, 3, 4).hours(20, 30)
                .languages(lang("de", LanguageLevel.C1)).workAuth("Must be authorized to work in the US").evaluate();
        List<EligibilityCheck> unstated = new Case().regions().seniority(null).languages().evaluate();

        assertThat(check(stated, EligibilityRule.REGION).value()).isEqualTo("EU, IT");
        assertThat(check(stated, EligibilityRule.TIMEZONE).value()).isEqualTo("UTC-1 to UTC+3, 4h overlap");
        assertThat(check(stated, EligibilityRule.HOURS).value()).isEqualTo("20–30 h/week");
        assertThat(check(stated, EligibilityRule.LANGUAGE).value()).isEqualTo("DE C1");
        assertThat(check(stated, EligibilityRule.WORK_AUTH).value()).isEqualTo("Must be authorized to work in the US");
        assertThat(check(stated, EligibilityRule.WORK_MODE).value()).isEqualTo("Remote anywhere · Remote");
        assertThat(check(unstated, EligibilityRule.REGION).value()).isEqualTo("Not stated");
        assertThat(check(unstated, EligibilityRule.SENIORITY).value()).isEqualTo("Not stated");
        assertThat(check(unstated, EligibilityRule.LANGUAGE).value()).isEqualTo("None required");
    }

    @Test
    void promptLineHasCodeResultValueAndReason() {
        EligibilityCheck employment = check(new Case().types(EmploymentType.FULL_TIME).evaluate(),
                EligibilityRule.EMPLOYMENT_TYPE);

        assertThat(employment.promptLine()).isEqualTo("EMPLOYMENT_TYPE: UNKNOWN — Full-time (FULL_TIME_ONLY_SOFT)");
        assertThat(check(new Case().evaluate(), EligibilityRule.SCAM).promptLine()).isEqualTo("SCAM: OK — No signals");
    }

    @Test
    void countsResults() {
        List<EligibilityCheck> checks = new Case().regions().seniority(Seniority.LEAD).evaluate();

        assertThat(CheckCounts.of(checks)).isEqualTo(new CheckCounts(8, 1, 1));
        assertThat(CheckCounts.of(checks).toReview()).isEqualTo(2);
    }

    @Test
    void mentionedRegionsIgnoreLowerCaseAbbreviations() {
        assertThat(EligibilityEvaluator.mentionedRegions("Work with us from the U.S. or the EU"))
                .containsExactlyInAnyOrder("US", "EU");
        assertThat(EligibilityEvaluator.mentionedRegions("join us, we value you")).isEmpty();
    }

    static EligibilityCheck check(List<EligibilityCheck> checks, EligibilityRule rule) {
        return checks.stream().filter(c -> c.code() == rule).findFirst().orElseThrow();
    }

    static Arguments row(String name, UnaryOperator<Case> change, EligibilityRule rule, CheckResult result) {
        return Arguments.of(name, change, rule, result);
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

    /** A job whose checks are all OK for the example profile, changed per row. */
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

        List<EligibilityCheck> evaluate() {
            var attributes = new JobAttributes(remote, regions, null, timezone, types, hours, seniority, 3,
                    List.of("Java"), List.of(), languages, workAuth, null, "Backend work.", false, scam, confidence);
            var job = new JobView(1L, "Java Developer", "Acme", "acme", location, RemoteHint.REMOTE, null, description,
                    null, null, null, null, null, List.of(), now, now, now, JobStatus.ACTIVE);
            return new EligibilityEvaluator(Clock.fixed(now, ZoneOffset.UTC)).evaluate(attributes, job, profile);
        }
    }
}
