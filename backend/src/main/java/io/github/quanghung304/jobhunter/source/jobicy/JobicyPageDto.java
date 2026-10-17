package io.github.quanghung304.jobhunter.source.jobicy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * One response of {@code GET /api/v2/remote-jobs}. Items stay raw JSON (rawPayload; a
 * malformed item does not fail the page). The last page has {@code nextCursor: null},
 * {@code hasMore: false}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobicyPageDto(List<JsonNode> jobs, String nextCursor, Boolean hasMore) {

    public JobicyPageDto {
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
    }

    public boolean hasNext() {
        return nextCursor != null && !nextCursor.isBlank() && !Boolean.FALSE.equals(hasMore);
    }
}
