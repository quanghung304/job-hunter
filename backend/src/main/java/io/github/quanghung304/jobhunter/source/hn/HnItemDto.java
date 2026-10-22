package io.github.quanghung304.jobhunter.source.hn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Response of {@code GET /api/v1/items/{storyId}}: the thread with its comment tree. Only the
 * top-level {@code children} are job posts; they stay raw JSON (rawPayload; a malformed child
 * does not fail the thread). Deleted comments are omitted by the API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HnItemDto(Long id, String title, String type, List<JsonNode> children) {

    public HnItemDto {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
