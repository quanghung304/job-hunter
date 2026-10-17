package io.github.quanghung304.jobhunter.enrich;

import static io.github.quanghung304.jobhunter.enrich.PrefilterReason.EXCLUDED_COMPANY;
import static io.github.quanghung304.jobhunter.enrich.PrefilterReason.NOT_RELEVANT;
import static io.github.quanghung304.jobhunter.enrich.PrefilterReason.ONSITE_SOURCE_HINT;
import static io.github.quanghung304.jobhunter.enrich.PrefilterReason.TITLE_EXCLUDED;
import static io.github.quanghung304.jobhunter.enrich.PrefilterReason.TOO_OLD;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.text.TextNormalizer;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** scoring-spec.md §2 against the example profile (city Turin, core skills Java, Spring Boot, …). */
class PrefilterTest {

    static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    static final String NO_SKILLS = "Great team, flexible hours.";
    static final Profile EXAMPLE = ProfileFixtures.example();

    final Prefilter prefilter = new Prefilter(Clock.fixed(NOW, ZoneOffset.UTC));

    static Stream<Arguments> exampleProfileRows() {
        return Stream.of(
                // title include
                row("Senior Java Developer", RemoteHint.REMOTE, null, NO_SKILLS, 1),
                row("Backend Engineer", RemoteHint.REMOTE, null, NO_SKILLS, 1),
                row("BACKEND ENGINEER (m/f/d)", RemoteHint.REMOTE, null, NO_SKILLS, 1),
                row("Full-Stack Developer", RemoteHint.REMOTE, null, NO_SKILLS, 1),
                row("Platform Engineer", RemoteHint.REMOTE, null, NO_SKILLS, 1),
                row("Software Engineer", RemoteHint.REMOTE, null, "You will use Java and Spring Boot daily.", 1),
                // title exclude
                row("iOS Engineer", RemoteHint.REMOTE, null, "Swift and UIKit.", 1, TITLE_EXCLUDED, NOT_RELEVANT),
                row("Staff Backend Engineer", RemoteHint.REMOTE, null, NO_SKILLS, 1, TITLE_EXCLUDED),
                row("Engineering Manager, Backend", RemoteHint.REMOTE, null, NO_SKILLS, 1, TITLE_EXCLUDED),
                row("Salesforce Developer", RemoteHint.REMOTE, null, "Apex.", 1, TITLE_EXCLUDED, NOT_RELEVANT),
                // relevance via description skills (minSkillHits = 2)
                row("Data Analyst", RemoteHint.REMOTE, null, "Excel, Tableau and Python.", 1, NOT_RELEVANT),
                row("Data Analyst", RemoteHint.REMOTE, null, "Some Java is a plus.", 1, NOT_RELEVANT),
                row("Data Engineer", RemoteHint.REMOTE, null, "Java, SpringBoot, Kafka.", 1),
                row("Data Engineer", RemoteHint.REMOTE, null, "JavaScript and TypeScript.", 1, NOT_RELEVANT),
                row("Engineer", RemoteHint.REMOTE, null, "OIDC and OAuth2 for our login.", 1),
                row("Engineer", RemoteHint.REMOTE, null, "Hibernate and JPA.", 1, NOT_RELEVANT),
                row("Engineer", RemoteHint.REMOTE, null, "RESTful services on Oracle.", 1),
                // on-site hint
                row("Backend Developer", RemoteHint.ONSITE, "Munich, Germany", NO_SKILLS, 1, ONSITE_SOURCE_HINT),
                row("Backend Developer", RemoteHint.ONSITE, "Turin, Italy", NO_SKILLS, 1),
                row("Backend Developer", RemoteHint.ONSITE, "Torino, Piemonte", NO_SKILLS, 1),
                row("Backend Developer", RemoteHint.ONSITE, null, NO_SKILLS, 1, ONSITE_SOURCE_HINT),
                row("Backend Developer", RemoteHint.HYBRID, "Munich, Germany", NO_SKILLS, 1),
                row("Backend Developer", RemoteHint.UNKNOWN, null, NO_SKILLS, 1),
                row("Backend Developer", null, null, NO_SKILLS, 1),
                // age (maxAgeDays = 21)
                row("Backend Developer", RemoteHint.REMOTE, null, NO_SKILLS, 22, TOO_OLD),
                row("Backend Developer", RemoteHint.REMOTE, null, NO_SKILLS, 20),
                row("Backend Developer", RemoteHint.REMOTE, null, NO_SKILLS, null),
                // all reasons are collected, in rule order
                row("Android Developer", RemoteHint.ONSITE, "Munich", "Kotlin.", 30,
                        TOO_OLD, TITLE_EXCLUDED, NOT_RELEVANT, ONSITE_SOURCE_HINT));
    }

    @ParameterizedTest(name = "[{index}] {0} / {1} / {2} / {4}d → {5}")
    @MethodSource("exampleProfileRows")
    void exampleProfile(String title, RemoteHint hint, String location, String description, Integer ageDays,
            List<PrefilterReason> expected) {
        JobView job = job(title, "Acme", hint, location, description, ageDays);

        PrefilterDecision decision = prefilter.evaluate(job, EXAMPLE);

        assertThat(decision.reasons()).containsExactlyElementsOf(expected);
        assertThat(decision.result()).isEqualTo(expected.isEmpty() ? PrefilterResult.PASS : PrefilterResult.REJECT);
    }

    @Test
    void excludedCompanyIsMatchedOnTheNormalizedCompanyKey() {
        Profile profile = withExcludeCompanies(EXAMPLE, List.of("Acme, Inc.", "Évil Corp GmbH"));

        assertThat(prefilter.evaluate(job("Java Developer", "ACME Inc", RemoteHint.REMOTE, null, NO_SKILLS, 1), profile)
                .reasons()).containsExactly(EXCLUDED_COMPANY);
        assertThat(prefilter.evaluate(job("Java Developer", "Evil Corp", RemoteHint.REMOTE, null, NO_SKILLS, 1), profile)
                .reasons()).containsExactly(EXCLUDED_COMPANY);
        assertThat(prefilter.evaluate(job("Java Developer", "Acme Labs", RemoteHint.REMOTE, null, NO_SKILLS, 1), profile)
                .passed()).isTrue();
    }

    @Test
    void withoutACityEveryOnsiteJobIsRejected() {
        Profile profile = withCity(EXAMPLE, null);

        assertThat(prefilter.evaluate(job("Java Developer", "Acme", RemoteHint.ONSITE, "Turin", NO_SKILLS, 1), profile)
                .reasons()).containsExactly(ONSITE_SOURCE_HINT);
    }

    @Test
    void reasonCodesAreTheEnumNames() {
        PrefilterDecision decision = prefilter.evaluate(
                job("iOS Engineer", "Acme", RemoteHint.REMOTE, null, NO_SKILLS, 30), EXAMPLE);

        assertThat(decision.reasonCodes()).containsExactly("TOO_OLD", "TITLE_EXCLUDED", "NOT_RELEVANT");
    }

    private static Arguments row(String title, RemoteHint hint, String location, String description, Integer ageDays,
            PrefilterReason... expected) {
        return Arguments.of(title, hint, location, description, ageDays, List.of(expected));
    }

    static JobView job(String title, String company, RemoteHint hint, String location, String description,
            Integer ageDays) {
        Instant postedAt = ageDays == null ? null : NOW.minus(Duration.ofDays(ageDays));
        return new JobView(1L, title, company, TextNormalizer.companyKey(company), location, hint, null, description,
                null, null, null, null, null, List.of(), postedAt, NOW, NOW, JobStatus.ACTIVE);
    }

    static Profile withExcludeCompanies(Profile profile, List<String> excludeCompanies) {
        Profile.Preferences p = profile.preferences();
        var preferences = new Profile.Preferences(p.employmentTypes(), p.maxHoursPerWeek(), p.acceptRegions(),
                p.timezone(), p.minSalary(), p.seniority(), excludeCompanies);
        return new Profile(profile.id(), profile.summaryLanguage(), profile.candidate(), preferences,
                profile.prefilter(), profile.notifySettings(), profile.tracks());
    }

    static Profile withCity(Profile profile, String city) {
        Profile.Candidate c = profile.candidate();
        var candidate = new Profile.Candidate(c.headline(), c.yearsExperience(), c.residenceCountry(), city,
                c.timezone(), c.languages(), c.summary(), c.skills(), c.domains());
        return new Profile(profile.id(), profile.summaryLanguage(), candidate, profile.preferences(),
                profile.prefilter(), profile.notifySettings(), profile.tracks());
    }
}
