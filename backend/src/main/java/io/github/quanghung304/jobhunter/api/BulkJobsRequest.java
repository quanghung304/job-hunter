package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body of the admin bulk actions {@code POST /api/admin/jobs/analyze} and {@code /score}: at most
 * 200 job ids (more → 400 {@code TOO_MANY_JOBS}); {@code dryRun} only counts.
 */
public record BulkJobsRequest(
        @Schema(requiredMode = REQUIRED, description = "At most 200 ids") @NotNull List<@NotNull Long> jobIds,
        @Schema(requiredMode = REQUIRED) @NotNull Boolean dryRun) {
}
