package io.github.quanghung304.jobhunter.ingest;

import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read-only view of a deduplicated {@code job} for other modules (prefilter, extraction,
 * scoring). Nullable: {@code locationText}, {@code remoteHint}, {@code employmentHint},
 * {@code applyUrl}, salary fields and {@code postedAt}.
 */
public record JobView(
        long id,
        String title,
        String companyName,
        String companyKey,
        String locationText,
        RemoteHint remoteHint,
        String employmentHint,
        String descriptionText,
        String applyUrl,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        SalaryPeriod salaryPeriod,
        List<String> tags,
        Instant postedAt,
        Instant firstSeenAt,
        Instant lastSeenAt,
        JobStatus status) {

    public JobView {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
