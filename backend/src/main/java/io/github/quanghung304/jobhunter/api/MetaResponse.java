package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MetaResponse(
        @Schema(requiredMode = REQUIRED) List<SourceSummaryResponse> sources,
        @Schema(requiredMode = REQUIRED) List<String> statuses,
        @Schema(requiredMode = REQUIRED) List<String> employmentTypes,
        @Schema(requiredMode = REQUIRED) List<String> eligibilities,
        @Schema(requiredMode = REQUIRED) String profileId,
        @Schema(requiredMode = REQUIRED, allowableValues = {"vi", "en"}) String summaryLanguage) {

    public MetaResponse {
        sources = List.copyOf(sources);
        statuses = List.copyOf(statuses);
        employmentTypes = List.copyOf(employmentTypes);
        eligibilities = List.copyOf(eligibilities);
    }
}
