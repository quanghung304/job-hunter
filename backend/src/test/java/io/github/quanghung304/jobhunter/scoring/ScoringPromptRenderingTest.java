package io.github.quanghung304.jobhunter.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.common.model.LanguageLevel;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.Seniority;
import io.github.quanghung304.jobhunter.enrich.EligibilityEvaluator;
import io.github.quanghung304.jobhunter.enrich.EligibilityCheck;
import io.github.quanghung304.jobhunter.enrich.JobAttributes;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig;
import io.github.quanghung304.jobhunter.llm.prompt.PromptRepository;
import io.github.quanghung304.jobhunter.llm.prompt.RenderedPrompt;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** score-v2 rendered with the variables ScoringService builds (eligibility checks, ADR-0011). */
class ScoringPromptRenderingTest {

    static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    final ScoringService scoring = new ScoringService(null, null, null, null, null, null, new ObjectMapper(), null,
            LlmRouterTestConfig.properties(null, false));

    @Test
    void everyPlaceholderIsFilledInTheProfileLanguage() {
        Profile profile = ProfileFixtures.example();

        RenderedPrompt prompt = render(profile, "Build payment APIs with Java.");

        assertThat(prompt.system() + prompt.user()).doesNotContain("{{", "}}");
        assertThat(prompt.system()).contains("one sentence in language 'vi'");
        assertThat(prompt.user())
                .contains("Headline: Backend Software Engineer")
                .contains("<title>Java Developer</title>", "<company>Acme</company>")
                .contains("\"remoteType\" : \"REMOTE_RESTRICTED\"")
                .contains("<eligibility_checks>", "EMPLOYMENT_TYPE: UNKNOWN — Full-time (FULL_TIME_ONLY_SOFT)",
                        "REGION: OK — EU")
                .doesNotContain("UNCERTAIN", "INELIGIBLE")
                .contains("Build payment APIs with Java.");
    }

    @Test
    void contactDataIsNotSentEvenIfItSlipsIntoTheProfile() {
        Profile profile = withSummary(ProfileFixtures.example(),
                "- Backend developer. Contact: hugh.cao@example.com, +39 333 123 4567.");

        RenderedPrompt prompt = render(profile, "Java role.");

        assertThat(prompt.user()).doesNotContain("hugh.cao@example.com", "333 123 4567").contains("[redacted]");
    }

    @Test
    void longDescriptionsAreCutAt8000Characters() {
        RenderedPrompt prompt = render(ProfileFixtures.example(), "d".repeat(9_000));

        assertThat(prompt.user()).contains("d".repeat(8_000) + "\n[…]").doesNotContain("d".repeat(8_001));
    }

    @Test
    void everyCheckIsOneLine() {
        Profile profile = ProfileFixtures.example();
        JobAttributes attributes = attributes(List.of(EmploymentType.CONTRACT));
        List<EligibilityCheck> checks = new EligibilityEvaluator(Clock.fixed(NOW, ZoneOffset.UTC))
                .evaluate(attributes, job("x"), profile);

        assertThat(scoring.variables(job("x"), attributes, checks, profile).get("eligibilityChecks").lines())
                .hasSize(checks.size()).allSatisfy(line -> assertThat(line).contains(": OK — "));
    }

    private RenderedPrompt render(Profile profile, String description) {
        JobAttributes attributes = attributes(List.of(EmploymentType.FULL_TIME));
        List<EligibilityCheck> checks = new EligibilityEvaluator(Clock.fixed(NOW, ZoneOffset.UTC))
                .evaluate(attributes, job(description), profile);
        return new PromptRepository().get("score-v2").render(scoring.variables(job(description), attributes,
                checks, profile));
    }

    private static JobAttributes attributes(List<EmploymentType> types) {
        return new JobAttributes(JobAttributes.RemoteType.REMOTE_RESTRICTED, List.of("EU"), null, null, types, null,
                Seniority.MID, 3, List.of("Java"), List.of(),
                List.of(new JobAttributes.RequiredLanguage("en", LanguageLevel.B2)), null, null, "Backend work.",
                false, false, JobAttributes.Confidence.HIGH);
    }

    private static JobView job(String description) {
        return new JobView(1L, "Java Developer", "Acme", "acme", "Remote (EU)", RemoteHint.REMOTE, "Contract",
                description, null, null, null, null, null, List.of(), NOW, NOW, NOW, JobStatus.ACTIVE);
    }

    private static Profile withSummary(Profile profile, String summary) {
        Profile.Candidate c = profile.candidate();
        var candidate = new Profile.Candidate(c.headline(), c.yearsExperience(), c.residenceCountry(), c.city(),
                c.timezone(), c.languages(), summary, c.skills(), c.domains());
        return new Profile(profile.id(), profile.summaryLanguage(), candidate, profile.preferences(),
                profile.prefilter(), profile.notifySettings(), profile.tracks());
    }
}
