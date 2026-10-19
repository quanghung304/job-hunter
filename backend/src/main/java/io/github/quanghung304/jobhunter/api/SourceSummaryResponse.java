package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.source.SourceCatalog;
import io.swagger.v3.oas.annotations.media.Schema;

public record SourceSummaryResponse(
        @Schema(requiredMode = REQUIRED) String code,
        @Schema(requiredMode = REQUIRED) String name) {

    static SourceSummaryResponse of(String code) {
        return new SourceSummaryResponse(code, SourceCatalog.name(code));
    }
}
