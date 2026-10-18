package io.github.quanghung304.jobhunter.llm.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelRegistryTest {

    static final Path COMMITTED = Path.of("..", "config", "llm.yaml");
    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @TempDir
    Path dir;

    @Test
    void committedRegistryIsValid() {
        ModelRegistry registry = ModelRegistry.load(COMMITTED, VALIDATOR, env(Map.of("GROQ_API_KEY", "gsk-1")));

        RegisteredModel groq = registry.get("groq-gpt-oss-120b");
        assertThat(groq.providerType()).isEqualTo(ProviderType.OPENAI_COMPATIBLE);
        assertThat(groq.baseUrl()).isEqualTo("https://api.groq.com/openai/v1");
        assertThat(groq.modelId()).isEqualTo("openai/gpt-oss-120b");
        assertThat(groq.tier()).isEqualTo(ModelTier.FREE);
        assertThat(groq.limits().rpm()).isEqualTo(30);
        assertThat(groq.minOutputTokens()).isEqualTo(4096);
        assertThat(groq.maxOutputTokens(1200)).as("reasoning room").isEqualTo(4096);
        assertThat(groq.jsonMode()).as("default for openai-compatible").isTrue();
        assertThat(groq.apiKey()).isEqualTo("gsk-1");
        assertThat(groq.missingKey()).isFalse();

        RegisteredModel haiku = registry.get("claude-haiku");
        assertThat(haiku.tier()).isEqualTo(ModelTier.PAID);
        assertThat(haiku.jsonMode()).isFalse();
        assertThat(haiku.cost(1_000_000, 1_000_000)).contains(new BigDecimal("0.600000"));
        assertThat(haiku.missingKey()).isTrue();

        assertThat(registry.get("ollama-local").missingKey()).as("keyless provider").isFalse();
        assertThat(registry.get("fake").providerType()).isEqualTo(ProviderType.FAKE);
        assertThat(registry.defaults(LlmPurpose.EXTRACT).model()).isEqualTo("groq-gpt-oss-120b");
        assertThat(registry.defaults(LlmPurpose.SCORE).fallbacks())
                .as("no free OpenRouter model for SCORE: its prompt holds CV data")
                .doesNotContain("openrouter-nemotron-super");
        assertThat(registry.get("ollama-local").maxOutputTokens(1200)).isEqualTo(1200);
        assertThat(registry.defaults(LlmPurpose.SCORE).autoFallback()).isFalse();
        assertThat(registry.nearLimitPercent()).isEqualTo(80);
        assertThat(registry.find("github-gpt")).as("GitHub Models was retired").isEmpty();
        assertThat(registry.all()).extracting(RegisteredModel::providerId).as("Cerebras was removed")
                .doesNotContain("cerebras");
    }

    @Test
    void missingKeyMarksOnlyThatProvidersModels() {
        ModelRegistry registry = ModelRegistry.load(COMMITTED, VALIDATOR, env(Map.of("ANTHROPIC_API_KEY", " ")));

        assertThat(registry.all()).filteredOn(RegisteredModel::missingKey).extracting(RegisteredModel::id)
                .containsExactlyInAnyOrder("groq-gpt-oss-120b", "groq-gpt-oss-20b", "groq-qwen3-27b",
                        "openrouter-nemotron-super", "claude-haiku");
    }

    @Test
    void keyIsNeverPrinted() {
        RegisteredModel groq = ModelRegistry.load(COMMITTED, VALIDATOR, env(Map.of("GROQ_API_KEY", "gsk-secret")))
                .get("groq-gpt-oss-120b");

        assertThat(groq.toString()).doesNotContain("gsk-secret");
    }

    @Test
    void invalidEnumValueNamesTheField() throws IOException {
        Path file = write("""
                providers:
                  groq: { type: openai-compatible, baseUrl: http://x, apiKeyEnv: GROQ_API_KEY }
                models:
                  b: { label: B, provider: groq, modelId: y, tier: GOLD }
                defaults:
                  EXTRACT: { model: b }
                  SCORE: { model: b }
                """);

        assertThatThrownBy(() -> ModelRegistry.load(file, VALIDATOR, env(Map.of())))
                .isInstanceOf(InvalidLlmRegistryException.class)
                .hasMessageContaining("models.b.tier");
    }

    @Test
    void crossReferenceProblemsAreAllListed() throws IOException {
        Path file = write("""
                providers:
                  groq: { type: openai-compatible, apiKeyEnv: GROQ_API_KEY }
                models:
                  a: { label: A, provider: nope, modelId: x, tier: FREE }
                  b: { label: B, provider: groq, modelId: y, tier: FREE, resetZone: Mars/Base }
                defaults:
                  EXTRACT: { model: a, fallbacks: [missing] }
                """);

        assertThatThrownBy(() -> ModelRegistry.load(file, VALIDATOR, env(Map.of())))
                .isInstanceOfSatisfying(InvalidLlmRegistryException.class, e -> assertThat(e.problems())
                        .containsExactlyInAnyOrder(
                                "providers.groq.baseUrl: required for type openai-compatible",
                                "models.a.provider: unknown provider 'nope'",
                                "models.b.resetZone: not a time zone id: Mars/Base",
                                "defaults.EXTRACT.fallbacks[0]: unknown model 'missing'",
                                "defaults.SCORE: required"));
    }

    @Test
    void anthropicModelsMustBePaid() throws IOException {
        Path file = write("""
                providers:
                  anthropic: { type: anthropic, apiKeyEnv: ANTHROPIC_API_KEY }
                models:
                  sneaky: { label: S, provider: anthropic, modelId: claude-haiku-5-5, tier: FREE }
                defaults:
                  EXTRACT: { model: sneaky }
                  SCORE: { model: sneaky }
                """);

        assertThatThrownBy(() -> ModelRegistry.load(file, VALIDATOR, env(Map.of())))
                .isInstanceOfSatisfying(InvalidLlmRegistryException.class, e -> assertThat(e.problems())
                        .containsExactly("models.sneaky.tier: must be PAID for an anthropic provider (paid API, ADR-0009)"));
    }

    @Test
    void blankRequiredFieldsAreListedWithTheirPath() throws IOException {
        Path file = write("""
                providers:
                  groq: { type: openai-compatible, baseUrl: http://x, apiKeyEnv: K }
                models:
                  a: { label: "", provider: groq, modelId: x, tier: FREE, limits: { rpm: 0 } }
                defaults:
                  EXTRACT: { model: a }
                  SCORE: { model: a }
                """);

        assertThatThrownBy(() -> ModelRegistry.load(file, VALIDATOR, env(Map.of())))
                .isInstanceOfSatisfying(InvalidLlmRegistryException.class, e -> assertThat(e.problems())
                        .anySatisfy(p -> assertThat(p).startsWith("models.a.label:"))
                        .anySatisfy(p -> assertThat(p).startsWith("models.a.limits.rpm:")));
    }

    @Test
    void unknownKeysAndBadProviderTypesFail() throws IOException {
        Path typo = write("""
                providers:
                  groq: { type: openai-compatible, baseUrl: http://x, apiKeyEnv: K, timeout: 3 }
                """);
        Path badType = write("""
                providers:
                  groq: { type: openai, baseUrl: http://x }
                """);

        assertThatThrownBy(() -> ModelRegistry.load(typo, VALIDATOR, env(Map.of())))
                .isInstanceOf(InvalidLlmRegistryException.class).hasMessageContaining("timeout");
        assertThatThrownBy(() -> ModelRegistry.load(badType, VALIDATOR, env(Map.of())))
                .isInstanceOf(InvalidLlmRegistryException.class).hasMessageContaining("providers.groq.type");
    }

    @Test
    void missingFileFails() {
        assertThatThrownBy(() -> ModelRegistry.load(dir.resolve("none.yaml"), VALIDATOR, env(Map.of())))
                .isInstanceOf(InvalidLlmRegistryException.class).hasMessageContaining("file not found");
    }

    @Test
    void resetZoneAndJsonModeOverride() throws IOException {
        Path file = write("""
                providers:
                  local: { type: openai-compatible, baseUrl: "http://localhost:1/v1", apiKeyEnv: "" }
                models:
                  m: { label: M, provider: local, modelId: x, tier: LOCAL, jsonMode: false, resetZone: America/Los_Angeles }
                defaults:
                  EXTRACT: { model: m }
                  SCORE: { model: m, autoFallback: true }
                """);

        ModelRegistry registry = ModelRegistry.load(file, VALIDATOR, env(Map.of()));

        assertThat(registry.get("m").jsonMode()).isFalse();
        assertThat(registry.get("m").resetZone()).isEqualTo(ZoneId.of("America/Los_Angeles"));
        assertThat(registry.defaults(LlmPurpose.SCORE).autoFallback()).isTrue();
        assertThat(registry.defaults(LlmPurpose.EXTRACT).fallbacks()).isEmpty();
        assertThat(registry.nearLimitPercent()).isEqualTo(80);
    }

    private Path write(String yaml) throws IOException {
        Path file = Files.createTempFile(dir, "llm", ".yaml");
        Files.writeString(file, yaml);
        return file;
    }

    private static UnaryOperator<String> env(Map<String, String> values) {
        return values::get;
    }
}
