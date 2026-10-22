package io.github.quanghung304.jobhunter.source.greenhouse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One item of {@code jobs[]} of {@code GET /v1/boards/{slug}/jobs?content=true} (verified
 * 2026-10-22, see docs/sources/ats.md). {@code content} is HTML that is entity-escaped once
 * more ({@code &lt;p&gt;}); dates are ISO-8601 with offset.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GreenhouseJobDto(
        Long id,
        String title,
        @JsonProperty("absolute_url") String absoluteUrl,
        Location location,
        String content,
        List<Named> departments,
        @JsonProperty("first_published") String firstPublished,
        @JsonProperty("updated_at") String updatedAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Named(String name) {
    }
}
