package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.tracking.TrackingService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/jobs/{id}/notes}; an empty string clears the notes. */
public record NotesRequest(
        @Schema(requiredMode = REQUIRED, maxLength = TrackingService.MAX_NOTES)
        @NotNull @Size(max = TrackingService.MAX_NOTES) String notes) {
}
