package io.github.quanghung304.jobhunter.llm;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.llm.*} (config-spec.md §2). {@code registryPath} is bound as a String for the
 * same reason as {@code app.profile.path} ({@code ../} paths).
 *
 * @param forceModel  if set (e.g. {@code fake}), every purpose uses this registry model
 * @param allowPaidCalls the owner's standing consent to calls of PAID models (ADR-0009); default false
 * @param fake        test-only behaviour of the fake client ({@code app.llm.fake.simulate=quota-after:3})
 */
@Validated
@ConfigurationProperties("app.llm")
public record LlmProperties(
        @NotBlank @DefaultValue("../config/llm.yaml") String registryPath,
        String forceModel,
        @NotBlank @DefaultValue("extract-v1") String extractPrompt,
        @NotBlank @DefaultValue("score-v1") String scorePrompt,
        @Positive @DefaultValue("150") int maxCallsPerRun,
        @NotNull @DecimalMin("0") @DefaultValue("5.00") BigDecimal monthlyBudgetUsd,
        @DefaultValue("false") boolean allowPaidCalls,
        @NotNull @DefaultValue("60s") Duration timeout,
        @NotNull @DefaultValue("180s") Duration timeoutLocal,
        @DefaultValue Fake fake) {

    public Path registryFile() {
        return Path.of(registryPath);
    }

    public boolean forced() {
        return forceModel != null && !forceModel.isBlank();
    }

    /** {@code app.llm.fake.*}: test-only behaviour of the fake client (testing.md). */
    public record Fake(String simulate) {
    }
}
