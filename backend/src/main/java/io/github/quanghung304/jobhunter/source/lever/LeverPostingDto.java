package io.github.quanghung304.jobhunter.source.lever;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * One posting of {@code GET /v0/postings/{slug}?mode=json} (an array; verified 2026-10-22, see
 * docs/sources/ats.md). {@code createdAt} is epoch milliseconds; {@code lists[].content} is HTML.
 * {@code salaryRange} was not seen on any board checked; its shape follows the Lever docs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LeverPostingDto(
        String id,
        String text,
        String hostedUrl,
        String applyUrl,
        Long createdAt,
        Categories categories,
        String workplaceType,
        String descriptionPlain,
        List<ListSection> lists,
        String additionalPlain,
        SalaryRange salaryRange) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Categories(String commitment, String department, String location, String team,
            List<String> allLocations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListSection(String text, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SalaryRange(BigDecimal min, BigDecimal max, String currency, String interval) {
    }
}
