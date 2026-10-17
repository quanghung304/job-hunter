package io.github.quanghung304.jobhunter.source.jobicy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * One item of {@code jobs[]} (verified 2026-10-17, see docs/sources/jobicy.md). Salary keys are
 * absent, not null, on jobs without a salary.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobicyJobDto(
        Long id,
        String url,
        String jobSlug,
        String jobTitle,
        String companyName,
        List<String> jobIndustry,
        List<String> jobType,
        String jobGeo,
        String jobLevel,
        String jobExcerpt,
        String jobDescription,
        String pubDate,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        String salaryPeriod) {
}
