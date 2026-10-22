package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.profile.ProfileChanged;
import io.github.quanghung304.jobhunter.profile.ProfileService;
import io.github.quanghung304.jobhunter.profile.ProfileSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Bodies of {@code GET/PUT /api/profile} (api-spec "Profile", ADR-0012). */
final class ProfileResponses {

    private ProfileResponses() {
    }

    /** {@code GET /api/profile}. */
    record ProfileResponse(
            @Schema(requiredMode = REQUIRED) String id,
            @Schema(requiredMode = REQUIRED, description = "sha256 of profile.yaml; send it back as baseHash")
            String hash,
            @Schema(requiredMode = REQUIRED, description = "Last change of profile.yaml") Instant savedAt,
            @Schema(requiredMode = REQUIRED) ProfileDto profile) {

        static ProfileResponse of(ProfileSnapshot snapshot) {
            return new ProfileResponse(snapshot.profile().id(), snapshot.hash(), snapshot.savedAt(),
                    ProfileDto.of(snapshot.profile()));
        }
    }

    /** Body of {@code PUT /api/profile}. */
    record ProfileSaveRequest(
            @Schema(requiredMode = REQUIRED, description = "The hash the client loaded") @NotBlank String baseHash,
            @Schema(requiredMode = REQUIRED) @NotNull @Valid ProfileDto profile) {
    }

    /** Jobs re-checked after a save (no LLM). */
    record ReassessedResponse(
            @Schema(requiredMode = REQUIRED) int jobs,
            @Schema(requiredMode = REQUIRED) int prefilterChanged,
            @Schema(requiredMode = REQUIRED) int checksUpdated) {

        static ReassessedResponse of(ProfileChanged.Reassessment counts) {
            return new ReassessedResponse(counts.jobs(), counts.prefilterChanged(), counts.checksUpdated());
        }
    }

    /** Answer of {@code PUT /api/profile}. */
    record ProfileSaveResponse(
            @Schema(requiredMode = REQUIRED) String id,
            @Schema(requiredMode = REQUIRED) String hash,
            @Schema(requiredMode = REQUIRED) Instant savedAt,
            @Schema(requiredMode = REQUIRED) ReassessedResponse reassessed) {

        static ProfileSaveResponse of(ProfileService.SaveResult result) {
            ProfileSnapshot snapshot = result.snapshot();
            return new ProfileSaveResponse(snapshot.profile().id(), snapshot.hash(), snapshot.savedAt(),
                    ReassessedResponse.of(result.reassessed()));
        }
    }
}
