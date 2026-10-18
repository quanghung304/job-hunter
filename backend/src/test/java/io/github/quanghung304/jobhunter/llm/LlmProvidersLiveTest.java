package io.github.quanghung304.jobhunter.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.quanghung304.jobhunter.TestcontainersConfiguration;
import io.github.quanghung304.jobhunter.llm.registry.ModelRegistry;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Opt-in, one real extraction call per FREE model through the router:
 * {@code ./mvnw test -Plive -Dtest=LlmProvidersLiveTest}. Keys come from {@code ../.env}; a model
 * without a key is skipped. Paid calls are forced off (ADR-0009) and every model is checked to
 * be FREE before the call. Only extract-v1 is used: it contains the job posting, no CV data.
 */
@Tag("live")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "app.profile.path=../config/profile.example.yaml",
        "spring.config.import=optional:file:../.env[.properties]",
        "app.llm.allow-paid-calls=false"})
@Import(TestcontainersConfiguration.class)
class LlmProvidersLiveTest {

    /** The extraction fields this check reads; the rest is ignored. */
    record Extraction(
            @NotNull String remoteType,
            @NotNull List<String> allowedRegions,
            @NotNull List<String> employmentTypes,
            @NotNull List<String> requiredSkills,
            @NotBlank String roleSummary,
            @NotNull String confidence) {
    }

    static final Map<String, String> JOB = Map.of(
            "title", "Senior Java Developer (Contract, Remote EU)",
            "company", "Acme Payments GmbH",
            "locationText", "Remote (EU)",
            "remoteHint", "REMOTE",
            "employmentHint", "Contract",
            "description", """
                    We are a fintech building payment APIs. You will design and build REST APIs with
                    Java 21 and Spring Boot, work with PostgreSQL and Kafka, and run services on Kubernetes.
                    Requirements: 5+ years of Java, Spring Boot, SQL; Kafka is a plus. Fluent English.
                    This is a 6-month freelance contract, 30-40 hours per week, fully remote for candidates
                    living in the EU, working hours overlapping with CET. Rate: 60-75 EUR per hour.""");

    @Autowired
    LlmRouter router;

    @Autowired
    ModelRegistry registry;

    @Autowired
    BudgetGuard budget;

    @ParameterizedTest
    @ValueSource(strings = {"groq-gpt-oss-120b", "groq-gpt-oss-20b", "groq-qwen3-27b", "openrouter-nemotron-super"})
    void freeModelExtractsAJob(String modelId) {
        RegisteredModel model = registry.get(modelId);
        assertThat(model.tier()).as("live checks call FREE models only (ADR-0009)").isEqualTo(ModelTier.FREE);
        assumeFalse(model.missingKey(), "no key for " + model.providerId() + " in .env");
        budget.beginRun();

        LlmOutcome<Extraction> outcome = router.callModel(modelId, LlmPurpose.TEST, "extract-v1", JOB, null,
                Extraction.class);

        System.out.println("LIVE " + modelId + " -> " + outcome);
        assertThat(outcome).isInstanceOfSatisfying(LlmResult.class, result -> {
            Extraction extraction = (Extraction) result.value();
            assertThat(result.modelId()).isEqualTo(modelId);
            assertThat(result.inputTokens()).isPositive();
            assertThat(extraction.remoteType()).isIn("FULLY_REMOTE", "REMOTE_RESTRICTED");
            assertThat(extraction.requiredSkills()).isNotEmpty();
        });
    }
}
