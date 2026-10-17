package io.github.quanghung304.jobhunter.source;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One posting as returned by a collector, already mapped to the common shape
 * (data-model.md §3). Ingest validates and stores it; nullable fields are noted.
 */
public record NormalizedPosting(
        String sourceCode,
        String externalId,
        String url,
        String title,
        String companyName,
        String locationText,          // nullable
        RemoteHint remoteHint,
        String employmentHint,        // raw, nullable
        String descriptionText,       // plain text (HTML converted by the collector)
        String applyUrl,              // nullable
        BigDecimal salaryMin,         // nullable
        BigDecimal salaryMax,         // nullable
        String salaryCurrency,        // nullable
        SalaryPeriod salaryPeriod,    // nullable
        List<String> tags,
        Instant postedAt,             // nullable; ingest uses now() if null
        JsonNode rawPayload) {

    public NormalizedPosting {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
