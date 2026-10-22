package io.github.quanghung304.jobhunter.source.hn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One top-level child of a thread (verified 2026-10-22). {@code text} is HN's HTML
 * ({@code <p>} between paragraphs, entities such as {@code &#x2F;}); {@code created_at} is
 * ISO-8601 UTC with milliseconds ("2026-10-01T15:03:36.000Z"). Nested replies are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HnCommentDto(
        Long id,
        String type,
        String author,
        String text,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("created_at_i") Long createdAtEpoch,
        @JsonProperty("parent_id") Long parentId) {
}
