package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Result of {@code POST /api/admin/collect/{sourceCode}}: one collector run plus ingest. */
public record CollectRunResponse(
        @Schema(requiredMode = REQUIRED) long runId,
        @Schema(requiredMode = REQUIRED) CollectStatus collectStatus,
        @Schema(requiredMode = REQUIRED) int fetched,
        @Schema(requiredMode = REQUIRED) int newRaw,
        @Schema(requiredMode = REQUIRED) int newJobs,
        @Schema(requiredMode = REQUIRED) int updatedJobs,
        @Schema(requiredMode = REQUIRED) int skippedInvalid,
        @Schema(requiredMode = REQUIRED) List<String> warnings,
        @Schema(requiredMode = REQUIRED, nullable = true) String error) {

    public CollectRunResponse {
        warnings = List.copyOf(warnings);
    }
}
