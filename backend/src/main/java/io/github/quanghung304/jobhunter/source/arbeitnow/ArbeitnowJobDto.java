package io.github.quanghung304.jobhunter.source.arbeitnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** One item of {@code data[]} (verified 2026-10-17, see docs/sources/arbeitnow.md). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArbeitnowJobDto(
        String slug,
        @JsonProperty("company_name") String companyName,
        String title,
        String description,
        Boolean remote,
        String url,
        List<String> tags,
        @JsonProperty("job_types") List<String> jobTypes,
        String location,
        @JsonProperty("created_at") Long createdAt) {
}
