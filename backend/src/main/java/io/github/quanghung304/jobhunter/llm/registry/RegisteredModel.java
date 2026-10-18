package io.github.quanghung304.jobhunter.llm.registry;

import io.github.quanghung304.jobhunter.llm.ModelTier;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Optional;

/**
 * One usable registry entry: model joined with its provider.
 *
 * @param id          registry id (e.g. {@code groq-gpt-oss-120b}), stored as {@code llm_call.model_id}
 * @param providerId  provider key in {@code llm.yaml}
 * @param modelId     model name sent to the provider
 * @param apiKey      resolved key, empty when the provider needs none
 * @param missingKey  the provider names a key env var that is empty → model status ERROR
 * @param resetZone   zone in which the provider resets daily limits; null = UTC
 * @param minOutputTokens lower bound for {@code max_tokens}: reasoning models spend output
 *                    tokens on thinking before the answer; 0 = use the prompt's budget
 */
public record RegisteredModel(
        String id,
        String label,
        String providerId,
        ProviderType providerType,
        String baseUrl,
        String modelId,
        ModelTier tier,
        LlmRegistryFile.Limits limits,
        LlmRegistryFile.Pricing pricing,
        boolean jsonMode,
        String apiKey,
        boolean missingKey,
        ZoneId resetZone,
        int minOutputTokens) {

    /** {@code max_tokens} for a prompt whose answer needs {@code promptMaxOutputTokens}. */
    public int maxOutputTokens(int promptMaxOutputTokens) {
        return Math.max(promptMaxOutputTokens, minOutputTokens);
    }

    /** Cost of one call in USD for PAID models with known pricing; empty otherwise. */
    public Optional<BigDecimal> cost(int inputTokens, int outputTokens) {
        if (tier != ModelTier.PAID || pricing == null || pricing.inputPerMTok() == null
                || pricing.outputPerMTok() == null) {
            return Optional.empty();
        }
        BigDecimal million = BigDecimal.valueOf(1_000_000);
        BigDecimal cost = pricing.inputPerMTok().multiply(BigDecimal.valueOf(inputTokens))
                .add(pricing.outputPerMTok().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(million, 6, java.math.RoundingMode.HALF_UP);
        return Optional.of(cost);
    }

    @Override
    public String toString() {
        // never print the key
        return "RegisteredModel[" + id + " " + providerId + "/" + modelId + " " + tier
                + (missingKey ? " missing key" : "") + "]";
    }
}
