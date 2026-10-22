package io.github.quanghung304.jobhunter.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.profile.ProfileChanged;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.profile.ProfileService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;

/**
 * Saving the profile (ADR-0012) re-runs the prefilter and the eligibility checks through the
 * {@link ProfileChanged} listener: no LLM call, scores and the owner's decisions are kept.
 */
@PostgresJpaTest
@Import({EnrichTestConfig.class, ProfileChangeListener.class})
class ProfileChangedIT {

    static final String DESCRIPTION = "Contract role, fully remote in Europe. Java, Spring Boot, SQL.";
    static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @TempDir
    Path dir;

    @Autowired
    AnalysisService analysis;

    @Autowired
    AssessmentService assessments;

    @Autowired
    ProfileProvider startupProfile;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    MutableClock clock;

    @Autowired
    TestEntityManager em;

    ProfileService profiles;

    @BeforeEach
    void setUp() throws IOException {
        fake.reset();
        EnrichTestConfig.resetClock(clock);
        Path file = dir.resolve("profile.yaml");
        Files.copy(ProfileFixtures.EXAMPLE, file);
        profiles = ProfileFixtures.service(file, events);
    }

    @Test
    void savingReEvaluatesPrefilterAndChecksWithoutLlmCallsAndKeepsScoresAndDecisions() throws IOException {
        long scored = EnrichTestConfig.insertJob(em.getEntityManager(), "fp-java", "Java Developer", DESCRIPTION);
        long backend = EnrichTestConfig.insertJob(em.getEntityManager(), "fp-backend", "Backend Engineer",
                DESCRIPTION);
        analysis.analyze(scored);
        analysis.analyze(backend);
        assessments.recordScore(scored, "hugh", 81, YAML.createObjectNode(), "Gute Wahl", "fake");
        decide(scored, "ELIGIBLE");
        assertThat(profiles.current().hash()).as("same file as the startup profile")
                .isEqualTo(startupProfile.profileHash());
        assertThat(checkOf(scored, "EMPLOYMENT_TYPE")).isEqualTo("OK");
        fake.reset();

        // Full-time only (the fake extraction says CONTRACT) and no "backend" titles any more.
        ObjectNode tree = (ObjectNode) YAML.readTree(Files.readAllBytes(ProfileFixtures.EXAMPLE));
        ((ObjectNode) tree.get("preferences")).putArray("employmentTypes").add("FULL_TIME");
        ((ArrayNode) tree.get("prefilter").get("titleExclude")).add("\\bbackend\\b");
        tree.remove("tracks");
        ProfileService.SaveResult result = profiles.save(profiles.parse(tree), profiles.current().hash());

        assertThat(result.reassessed()).isEqualTo(new ProfileChanged.Reassessment(2, 1, 2));
        assertThat(fake.requests()).as("no LLM call").isEmpty();
        em.flush();
        em.clear();
        AssessmentView kept = assessments.find(scored, "hugh").orElseThrow();
        assertThat(kept.profileHash()).isEqualTo(result.snapshot().hash());
        assertThat(kept.score()).as("re-scoring is manual").isEqualTo(81);
        assertThat(checkOf(scored, "EMPLOYMENT_TYPE")).isEqualTo("FAIL");
        assertThat(decisionOf(scored)).as("the owner's decision stays").isEqualTo("ELIGIBLE");
        AssessmentView rejected = assessments.find(backend, "hugh").orElseThrow();
        assertThat(rejected.prefilterResult()).isEqualTo(PrefilterResult.REJECT);
        assertThat(rejected.prefilterReasons()).contains("TITLE_EXCLUDED");
        assertThat(rejected.eligibilityChecks()).isNull();
    }

    private String checkOf(long jobId, String code) {
        em.flush();
        em.clear();
        List<EligibilityCheck> checks = assessments.find(jobId, "hugh").orElseThrow().eligibilityChecks();
        return checks.stream().filter(c -> c.code().name().equals(code)).findFirst().orElseThrow().result().name();
    }

    private void decide(long jobId, String decision) {
        em.getEntityManager().createNativeQuery("""
                        insert into job_tracking (job_id, profile_id, status, eligibility_decision, eligibility_decided_at,
                                                  updated_at)
                        values (:job, 'hugh', 'NEW', :decision, now(), now())""")
                .setParameter("job", jobId)
                .setParameter("decision", decision)
                .executeUpdate();
    }

    private Object decisionOf(long jobId) {
        return em.getEntityManager()
                .createNativeQuery("select eligibility_decision from job_tracking where job_id = :job")
                .setParameter("job", jobId)
                .getSingleResult();
    }
}
