package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.tracking.JobBoardPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** One page of {@code GET /api/jobs}; {@code page} is 0-based. */
public record JobPageResponse(
        @Schema(requiredMode = REQUIRED) List<JobSummaryResponse> items,
        @Schema(requiredMode = REQUIRED) int page,
        @Schema(requiredMode = REQUIRED) int size,
        @Schema(requiredMode = REQUIRED) long totalItems,
        @Schema(requiredMode = REQUIRED) int totalPages) {

    public JobPageResponse {
        items = List.copyOf(items);
    }

    static JobPageResponse of(JobBoardPage page) {
        return new JobPageResponse(page.items().stream().map(JobSummaryResponse::of).toList(), page.page(),
                page.size(), page.totalItems(), page.totalPages());
    }
}
