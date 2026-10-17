package io.github.quanghung304.jobhunter.source.himalayas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * One page of {@code GET /jobs/api?limit=n[&cursor=c]}. Items stay raw JSON (rawPayload, and a
 * malformed item does not fail the page). {@code nextCursor} is absent on the last page.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HimalayasPageDto(List<JsonNode> jobs, String nextCursor) {

    public HimalayasPageDto {
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
    }

    public boolean hasNext() {
        return nextCursor != null && !nextCursor.isBlank();
    }
}
