package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.llm.registry.LlmRegistryFile;
import io.github.quanghung304.jobhunter.llm.registry.ProviderType;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.math.BigDecimal;

/** {@link RegisteredModel}s for unit tests. */
public final class LlmTestModels {

    public static final LlmRegistryFile.Limits NO_LIMITS = new LlmRegistryFile.Limits(null, null, null, null, null);

    private LlmTestModels() {
    }

    public static RegisteredModel openAi(String baseUrl, boolean jsonMode) {
        return new RegisteredModel("groq-test", "Groq test", "groq", ProviderType.OPENAI_COMPATIBLE, baseUrl,
                "llama-test", ModelTier.FREE, NO_LIMITS, null, jsonMode, "secret-key", false, null, 0);
    }

    public static RegisteredModel anthropic(String baseUrl) {
        return new RegisteredModel("claude-test", "Claude test", "anthropic", ProviderType.ANTHROPIC, baseUrl,
                "claude-haiku-5-5", ModelTier.PAID, NO_LIMITS,
                new LlmRegistryFile.Pricing(new BigDecimal("0.10"), new BigDecimal("0.50")), false, "sk-test", false,
                null, 0);
    }

    public static RegisteredModel withLimits(String id, ModelTier tier, LlmRegistryFile.Limits limits) {
        return new RegisteredModel(id, id, "fake", ProviderType.FAKE, null, id, tier, limits, null, false, "", false,
                null, 0);
    }
}
