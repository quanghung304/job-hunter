package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/admin/rescore}: run the dry run first, then confirm with {@code false}. */
public record DryRunRequest(@Schema(requiredMode = REQUIRED) @NotNull Boolean dryRun) {
}
