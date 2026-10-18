package io.github.quanghung304.jobhunter.llm.registry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.quanghung304.jobhunter.llm.LlmProperties;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The model registry from {@code app.llm.registry-path} (config-spec.md §5), loaded once at
 * startup; an invalid file fails startup with every problem listed. Keys are resolved from
 * the environment by the env var name each provider declares; a model whose key is empty is
 * kept but flagged {@link RegisteredModel#missingKey()}.
 */
@Component
public class ModelRegistry {

    /** Purposes that have an assignment and therefore need {@code defaults}. */
    public static final List<LlmPurpose> ASSIGNABLE = List.of(LlmPurpose.EXTRACT, LlmPurpose.SCORE);

    private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Map<String, RegisteredModel> models;
    private final Map<LlmPurpose, LlmRegistryFile.PurposeDefaults> defaults;
    private final int nearLimitPercent;

    @Autowired
    public ModelRegistry(LlmProperties properties, Validator validator, Environment environment) {
        this(properties.registryFile(), validator, environment::getProperty, properties.forceModel());
    }

    ModelRegistry(Path path, Validator validator, UnaryOperator<String> env, String forceModel) {
        LlmRegistryFile file = parse(path, read(path));
        validate(path, file, validator, forceModel);
        this.models = resolve(file, env);
        this.defaults = Map.copyOf(file.defaults());
        this.nearLimitPercent = file.alerts().nearLimitPercent();
        long missing = models.values().stream().filter(RegisteredModel::missingKey).count();
        log.info("Loaded LLM registry: {} models ({} without key) from {}", models.size(), missing,
                path.toAbsolutePath().normalize());
    }

    /** Loads a registry file for tests and tools. */
    public static ModelRegistry load(Path path, Validator validator, UnaryOperator<String> env) {
        return new ModelRegistry(path, validator, env, null);
    }

    public Optional<RegisteredModel> find(String modelId) {
        return Optional.ofNullable(models.get(modelId));
    }

    public RegisteredModel get(String modelId) {
        return find(modelId).orElseThrow(() -> new IllegalArgumentException("unknown model id: " + modelId));
    }

    /** All models, sorted by id. */
    public List<RegisteredModel> all() {
        return List.copyOf(models.values());
    }

    public LlmRegistryFile.PurposeDefaults defaults(LlmPurpose purpose) {
        LlmRegistryFile.PurposeDefaults value = defaults.get(purpose);
        if (value == null) {
            throw new IllegalArgumentException("no defaults for purpose " + purpose);
        }
        return value;
    }

    public int nearLimitPercent() {
        return nearLimitPercent;
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            throw new InvalidLlmRegistryException(path, List.of(
                    "file not found at " + path.toAbsolutePath().normalize()), e);
        } catch (IOException e) {
            throw new InvalidLlmRegistryException(path, List.of("cannot read file: " + e.getMessage()), e);
        }
    }

    private static LlmRegistryFile parse(Path path, byte[] bytes) {
        try {
            JsonNode tree = YAML.readTree(bytes);
            if (tree == null || tree.isMissingNode() || tree.isNull()) {
                throw new InvalidLlmRegistryException(path, List.of("file is empty"), null);
            }
            return YAML.readValue(bytes, LlmRegistryFile.class);
        } catch (JsonMappingException e) {
            throw new InvalidLlmRegistryException(path, List.of(fieldPath(e) + ": " + e.getOriginalMessage()), e);
        } catch (IOException e) {
            throw new InvalidLlmRegistryException(path, List.of("invalid YAML: " + e.getMessage()), e);
        }
    }

    private static String fieldPath(JsonMappingException e) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : e.getPath()) {
            if (ref.getFieldName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? "(root)" : path.toString();
    }

    private static void validate(Path path, LlmRegistryFile file, Validator validator, String forceModel) {
        List<String> problems = new ArrayList<>();
        validator.validate(file).stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(ModelRegistry::describe)
                .forEach(problems::add);
        file.providers().forEach((id, provider) -> {
            if (provider != null && provider.type() == ProviderType.OPENAI_COMPATIBLE
                    && (provider.baseUrl() == null || provider.baseUrl().isBlank())) {
                problems.add("providers." + id + ".baseUrl: required for type openai-compatible");
            }
        });
        file.models().forEach((id, model) -> {
            if (model != null && model.provider() != null && !file.providers().containsKey(model.provider())) {
                problems.add("models." + id + ".provider: unknown provider '" + model.provider() + "'");
            }
            if (model != null && model.tier() != null && model.tier() != ModelTier.PAID
                    && file.providers().get(model.provider()) != null
                    && file.providers().get(model.provider()).type() == ProviderType.ANTHROPIC) {
                problems.add("models." + id + ".tier: must be PAID for an anthropic provider (paid API, ADR-0009)");
            }
            if (model != null && model.resetZone() != null) {
                try {
                    ZoneId.of(model.resetZone());
                } catch (DateTimeException e) {
                    problems.add("models." + id + ".resetZone: not a time zone id: " + model.resetZone());
                }
            }
        });
        for (LlmPurpose purpose : ASSIGNABLE) {
            LlmRegistryFile.PurposeDefaults value = file.defaults().get(purpose);
            if (value == null) {
                problems.add("defaults." + purpose + ": required");
                continue;
            }
            if (value.model() != null && !file.models().containsKey(value.model())) {
                problems.add("defaults." + purpose + ".model: unknown model '" + value.model() + "'");
            }
            for (int i = 0; i < value.fallbacks().size(); i++) {
                if (!file.models().containsKey(value.fallbacks().get(i))) {
                    problems.add("defaults." + purpose + ".fallbacks[" + i + "]: unknown model '"
                            + value.fallbacks().get(i) + "'");
                }
            }
        }
        file.defaults().keySet().stream()
                .filter(purpose -> !ASSIGNABLE.contains(purpose))
                .forEach(purpose -> problems.add("defaults." + purpose + ": only EXTRACT and SCORE have defaults"));
        if (forceModel != null && !forceModel.isBlank() && !file.models().containsKey(forceModel)) {
            problems.add("app.llm.force-model: unknown model '" + forceModel + "'");
        }
        if (!problems.isEmpty()) {
            throw new InvalidLlmRegistryException(path, problems, null);
        }
    }

    private static String describe(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath().toString()
                .replaceAll("<map value>", "")
                .replaceAll("\\[(\\w[\\w.-]*)]", ".$1");
        return field + ": " + violation.getMessage();
    }

    private static Map<String, RegisteredModel> resolve(LlmRegistryFile file, UnaryOperator<String> env) {
        Map<String, RegisteredModel> result = new LinkedHashMap<>();
        file.models().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    LlmRegistryFile.Model model = entry.getValue();
                    LlmRegistryFile.Provider provider = file.providers().get(model.provider());
                    String key = provider.apiKeyEnv().isEmpty() ? "" : nullToEmpty(env.apply(provider.apiKeyEnv()));
                    boolean missingKey = !provider.apiKeyEnv().isEmpty() && key.isBlank();
                    boolean jsonMode = model.jsonMode() != null
                            ? model.jsonMode()
                            : provider.type() == ProviderType.OPENAI_COMPATIBLE;
                    result.put(entry.getKey(), new RegisteredModel(entry.getKey(), model.label(), model.provider(),
                            provider.type(), provider.baseUrl(), model.modelId(), model.tier(), model.limits(),
                            model.pricing(), jsonMode, key.strip(), missingKey,
                            model.resetZone() == null ? null : ZoneId.of(model.resetZone()),
                            model.minOutputTokens() == null ? 0 : model.minOutputTokens()));
                });
        return result;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
