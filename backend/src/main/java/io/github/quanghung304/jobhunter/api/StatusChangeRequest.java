package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.tracking.TrackingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/jobs/{id}/status}. */
public record StatusChangeRequest(
        @Schema(requiredMode = REQUIRED) @NotNull TrackingStatus to,
        @Schema(nullable = true, maxLength = StatusChangeRequest.MAX_NOTE) @Size(max = StatusChangeRequest.MAX_NOTE)
        String note) {

    static final int MAX_NOTE = 2_000;
}
