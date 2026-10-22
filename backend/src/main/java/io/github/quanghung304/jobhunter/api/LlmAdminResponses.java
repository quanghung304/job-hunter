package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.llm.AssignmentChangeReason;
import io.github.quanghung304.jobhunter.llm.LlmAdminService;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelStatus;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import io.github.quanghung304.jobhunter.llm.registry.LlmRegistryFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Bodies of the LLM models endpoints (api-spec "LLM models", ADR-0008). */
final class LlmAdminResponses {

    private LlmAdminResponses() {
    }

    @Schema(name = "LlmAssignment")
    record AssignmentResponse(
            @Schema(requiredMode = REQUIRED) LlmPurpose purpose,
            @Schema(requiredMode = REQUIRED) String modelId,
            @Schema(requiredMode = REQUIRED) List<String> fallbacks,
            @Schema(requiredMode = REQUIRED) boolean autoFallback,
            @Schema(requiredMode = REQUIRED) boolean allowPaid,
            @Schema(requiredMode = REQUIRED) Instant updatedAt,
            @Schema(requiredMode = REQUIRED, description = "config, user or auto-fallback") String updatedBy) {

        static AssignmentResponse of(LlmAdminService.AssignmentView view) {
            var a = view.assignment();
            return new AssignmentResponse(a.purpose(), a.modelId(), a.fallbacks(), a.autoFallback(), a.allowPaid(),
                    view.updatedAt(), a.updatedBy());
        }
    }

    @Schema(name = "LlmLimits")
    record Limits(
            @Schema(nullable = true) Integer rpm,
            @Schema(nullable = true) Integer rpd,
            @Schema(nullable = true) Integer tokensPerMinute,
            @Schema(nullable = true) Integer tokensPerDay,
            @Schema(nullable = true) Integer requestsPerMonth) {

        static Limits of(LlmRegistryFile.Limits limits) {
            return limits == null ? new Limits(null, null, null, null, null)
                    : new Limits(limits.rpm(), limits.rpd(), limits.tokensPerMinute(), limits.tokensPerDay(),
                            limits.requestsPerMonth());
        }
    }

    @Schema(name = "LlmUsageToday")
    record UsageToday(
            @Schema(requiredMode = REQUIRED) long calls,
            @Schema(requiredMode = REQUIRED) long inputTokens,
            @Schema(requiredMode = REQUIRED) long outputTokens,
            @Schema(requiredMode = REQUIRED, nullable = true, description = "null when no call had a cost")
            BigDecimal costUsd) {
    }

    @Schema(name = "LlmModel")
    record ModelResponse(
            @Schema(requiredMode = REQUIRED) String modelId,
            @Schema(requiredMode = REQUIRED) String label,
            @Schema(requiredMode = REQUIRED) String provider,
            @Schema(requiredMode = REQUIRED) String providerModel,
            @Schema(requiredMode = REQUIRED) ModelTier tier,
            @Schema(requiredMode = REQUIRED) ModelStatus status,
            @Schema(requiredMode = REQUIRED, nullable = true) Instant until,
            @Schema(requiredMode = REQUIRED, nullable = true) String detail,
            @Schema(requiredMode = REQUIRED) Limits limits,
            @Schema(requiredMode = REQUIRED, description = "Since 00:00 UTC") UsageToday usageToday,
            @Schema(requiredMode = REQUIRED) boolean keyConfigured,
            @Schema(requiredMode = REQUIRED) List<LlmPurpose> assignedTo,
            @Schema(requiredMode = REQUIRED, nullable = true, description = "Newest eval run (T-016); null until then")
            LastEval lastEval) {

        static ModelResponse of(LlmAdminService.ModelView view) {
            var m = view.model();
            var u = view.usageToday();
            return new ModelResponse(m.id(), m.label(), m.providerId(), m.modelId(), m.tier(), view.state().status(),
                    view.state().until(), view.state().detail(), Limits.of(m.limits()),
                    new UsageToday(u.calls(), u.inputTokens(), u.outputTokens(), u.costUsd()), !m.missingKey(),
                    view.assignedTo(), null);
        }
    }

    /** Summary of the newest eval run of a model (api-spec; filled from T-016). */
    @Schema(name = "LlmLastEval")
    record LastEval(
            @Schema(requiredMode = REQUIRED) java.time.LocalDate date,
            @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal falseEligible,
            @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal highRecall) {
    }

    @Schema(name = "LlmSwitchEvent")
    record HistoryResponse(
            @Schema(requiredMode = REQUIRED) LlmPurpose purpose,
            @Schema(requiredMode = REQUIRED) String fromModelId,
            @Schema(requiredMode = REQUIRED) String toModelId,
            @Schema(requiredMode = REQUIRED) AssignmentChangeReason reason,
            @Schema(requiredMode = REQUIRED) Instant at) {
    }

    @Schema(name = "LlmAdminResponse")
    record AdminResponse(
            @Schema(requiredMode = REQUIRED) List<AssignmentResponse> assignments,
            @Schema(requiredMode = REQUIRED) List<ModelResponse> models,
            @Schema(requiredMode = REQUIRED, description = "Last 10 switches, newest first")
            List<HistoryResponse> history,
            @Schema(requiredMode = REQUIRED, description = "app.llm.allow-paid-calls (ADR-0009)")
            boolean paidCallsAllowed) {

        static AdminResponse of(LlmAdminService.Overview overview) {
            return new AdminResponse(
                    overview.assignments().stream().map(AssignmentResponse::of).toList(),
                    overview.models().stream().map(ModelResponse::of).toList(),
                    overview.history().stream()
                            .map(h -> new HistoryResponse(h.purpose(), h.fromModelId(), h.toModelId(), h.reason(),
                                    h.at()))
                            .toList(),
                    overview.paidCallsAllowed());
        }
    }

    /** Body of {@code PUT /api/admin/llm/assignments/{purpose}}; null keeps the current value. */
    @Schema(name = "LlmAssignmentRequest")
    record AssignmentRequest(
            @Schema(requiredMode = REQUIRED) @NotBlank String modelId,
            @Schema(nullable = true) List<String> fallbacks,
            @Schema(nullable = true) Boolean autoFallback,
            @Schema(nullable = true) Boolean allowPaid,
            @Schema(nullable = true, description = "Required (true) to switch to a PAID model or turn on allowPaid")
            Boolean confirmPaid) {
    }

    @Schema(name = "LlmAssignmentSaved")
    record AssignmentSaved(
            @Schema(requiredMode = REQUIRED) AssignmentResponse assignment,
            @Schema(requiredMode = REQUIRED, nullable = true, description = "Set for a PAID model") String warning) {
    }

    /** Optional body of {@code POST /api/admin/llm/models/{modelId}/test}. */
    @Schema(name = "LlmTestRequest")
    record TestRequest(
            @Schema(nullable = true, description = "Required (true) to test a PAID model") Boolean confirmPaid) {
    }

    @Schema(name = "LlmTestResult")
    record TestResponse(
            @Schema(requiredMode = REQUIRED) boolean ok,
            @Schema(requiredMode = REQUIRED) long latencyMs,
            @Schema(requiredMode = REQUIRED, description = "The model's status after the call") ModelStatus status,
            @Schema(requiredMode = REQUIRED, nullable = true, description = "Why it failed") String detail) {
    }
}
