package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.tracking.ChecksFilter;
import io.github.quanghung304.jobhunter.tracking.DecisionFilter;
import io.github.quanghung304.jobhunter.tracking.TrackingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MetaResponse(
        @Schema(requiredMode = REQUIRED, description = "Registered sources, by code") List<SourceSummaryResponse> sources,
        @Schema(requiredMode = REQUIRED) List<TrackingStatus> statuses,
        @Schema(requiredMode = REQUIRED) List<EmploymentType> employmentTypes,
        @Schema(requiredMode = REQUIRED, description = "Values of the decision filter") List<DecisionFilter> decisions,
        @Schema(requiredMode = REQUIRED, description = "Values of the checks filter") List<ChecksFilter> checkResults,
        @Schema(requiredMode = REQUIRED) String profileId,
        @Schema(requiredMode = REQUIRED, allowableValues = {"vi", "en"}) String summaryLanguage) {

    public MetaResponse {
        sources = List.copyOf(sources);
        statuses = List.copyOf(statuses);
        employmentTypes = List.copyOf(employmentTypes);
        decisions = List.copyOf(decisions);
        checkResults = List.copyOf(checkResults);
    }
}
