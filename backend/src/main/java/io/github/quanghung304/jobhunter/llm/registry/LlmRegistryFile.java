package io.github.quanghung304.jobhunter.llm.registry;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** The shape of {@code config/llm.yaml} (config-spec.md §5). Immutable; defaults applied. */
public record LlmRegistryFile(
        @NotEmpty Map<String, @NotNull @Valid Provider> providers,
        @NotEmpty Map<String, @NotNull @Valid Model> models,
        @NotNull Map<LlmPurpose, @NotNull @Valid PurposeDefaults> defaults,
        @Valid Alerts alerts) {

    public LlmRegistryFile {
        providers = providers == null ? Map.of() : Map.copyOf(providers);
        models = models == null ? Map.of() : Map.copyOf(models);
        defaults = defaults == null ? Map.of() : Map.copyOf(defaults);
        alerts = alerts != null ? alerts : new Alerts(null);
    }

    public record Provider(
            @NotNull ProviderType type,
            String baseUrl,
            String apiKeyEnv) {

        public Provider {
            apiKeyEnv = apiKeyEnv == null ? "" : apiKeyEnv.strip();
        }
    }

    public record Model(
            @NotBlank String label,
            @NotBlank String provider,
            @NotBlank String modelId,
            @NotNull ModelTier tier,
            @Valid Limits limits,
            @Valid Pricing pricing,
            Boolean jsonMode,
            @Positive Integer minOutputTokens,
            String resetZone) {

        public Model {
            limits = limits != null ? limits : new Limits(null, null, null, null, null);
        }
    }

    public record Limits(
            @Positive Integer rpm,
            @Positive Integer rpd,
            @Positive Integer tokensPerMinute,
            @Positive Integer tokensPerDay,
            @Positive Integer requestsPerMonth) {
    }

    /** USD per million tokens; null = unknown. */
    public record Pricing(
            @PositiveOrZero BigDecimal inputPerMTok,
            @PositiveOrZero BigDecimal outputPerMTok) {
    }

    public record PurposeDefaults(
            @NotBlank String model,
            List<@NotBlank String> fallbacks,
            Boolean autoFallback,
            Boolean allowPaid) {

        public PurposeDefaults {
            fallbacks = fallbacks == null ? List.of() : List.copyOf(fallbacks);
            autoFallback = autoFallback != null && autoFallback;
            allowPaid = allowPaid != null && allowPaid;
        }
    }

    public record Alerts(@Min(1) @Max(100) @JsonProperty("nearLimitPercent") Integer nearLimitPercent) {

        public static final int DEFAULT_NEAR_LIMIT_PERCENT = 80;

        public Alerts {
            nearLimitPercent = nearLimitPercent != null ? nearLimitPercent : DEFAULT_NEAR_LIMIT_PERCENT;
        }
    }
}
