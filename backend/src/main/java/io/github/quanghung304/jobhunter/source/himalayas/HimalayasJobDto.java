package io.github.quanghung304.jobhunter.source.himalayas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/** One item of {@code jobs[]} (verified 2026-10-17, see docs/sources/himalayas.md). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HimalayasJobDto(
        String guid,
        String title,
        String companyName,
        String companySlug,
        String employmentType,
        List<String> seniority,
        List<String> locationRestrictions,
        List<BigDecimal> timezoneRestrictions,
        List<String> categories,
        List<String> parentCategories,
        String description,
        String excerpt,
        BigDecimal minSalary,
        BigDecimal maxSalary,
        String currency,
        String salaryPeriod,
        Long pubDate,
        Long expiryDate,
        String applicationLink) {
}
