package io.github.quanghung304.jobhunter.source.ashby;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * One item of {@code jobs[]} of {@code GET /posting-api/job-board/{slug}?includeCompensation=true}
 * (verified 2026-10-22, see docs/sources/ats.md). {@code workplaceType} is {@code Remote},
 * {@code Hybrid}, {@code OnSite} or null; {@code publishedAt} is ISO-8601 with offset.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AshbyJobDto(
        String id,
        String title,
        String location,
        List<SecondaryLocation> secondaryLocations,
        String department,
        String team,
        Boolean isListed,
        Boolean isRemote,
        String workplaceType,
        String employmentType,
        String descriptionHtml,
        String descriptionPlain,
        String publishedAt,
        String jobUrl,
        String applyUrl,
        Compensation compensation) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecondaryLocation(String location) {
    }

    /** {@code summaryComponents} are the components of all tiers; empty when the company hides pay. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Compensation(String compensationTierSummary, List<Component> summaryComponents) {
    }

    /** {@code compensationType}: Salary, EquityCashValue, EquityPercentage, ...; {@code interval}: "1 YEAR", "NONE", ... */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Component(String compensationType, String interval, String currencyCode, BigDecimal minValue,
            BigDecimal maxValue) {
    }
}
