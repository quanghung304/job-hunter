package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** {@code GET /api/status}: the app header's status line, polled on every page. */
public record StatusResponse(
        @Schema(requiredMode = REQUIRED, nullable = true, description = "Newest pipeline run; null before the first")
        LastRun lastRun,
        @Schema(requiredMode = REQUIRED) boolean pipelineRunning,
        @Schema(requiredMode = REQUIRED) List<LlmAlertResponse> llmAlerts) {

    public StatusResponse {
        llmAlerts = List.copyOf(llmAlerts);
    }

    /** {@code scored}: jobs scored (owner actions) since the run started; the run itself scores nothing. */
    @Schema(name = "StatusLastRun")
    public record LastRun(
            @Schema(requiredMode = REQUIRED) Instant startedAt,
            @Schema(requiredMode = REQUIRED) int newJobs,
            @Schema(requiredMode = REQUIRED) long scored,
            @Schema(requiredMode = REQUIRED) CollectStatus status) {
    }
}
