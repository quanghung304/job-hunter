package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** Result of {@code POST /api/admin/eval/export} (T-016): the written file and its line count. */
public record EvalExportResponse(
        @Schema(requiredMode = REQUIRED) String path,
        @Schema(requiredMode = REQUIRED) int count) {
}
