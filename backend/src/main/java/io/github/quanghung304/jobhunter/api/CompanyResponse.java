package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.source.AtsType;
import io.github.quanghung304.jobhunter.source.CompanyCheckStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** One company board (admin "Companies" table). */
public record CompanyResponse(
        @Schema(requiredMode = REQUIRED) long id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) AtsType ats,
        @Schema(requiredMode = REQUIRED) String slug,
        @Schema(requiredMode = REQUIRED) boolean enabled,
        @Schema(requiredMode = REQUIRED, nullable = true) CompanyCheckStatus lastCheckStatus,
        @Schema(requiredMode = REQUIRED, nullable = true) Instant lastCheckedAt,
        @Schema(requiredMode = REQUIRED, description = "ACTIVE jobs whose company matches the name")
        long activeJobs) {
}
