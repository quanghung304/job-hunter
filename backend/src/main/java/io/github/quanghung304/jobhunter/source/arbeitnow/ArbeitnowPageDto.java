package io.github.quanghung304.jobhunter.source.arbeitnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * One page of {@code GET /api/job-board-api?page=n}. Items stay raw JSON so that each one can
 * be kept as {@code rawPayload} and a malformed item does not fail the whole page.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArbeitnowPageDto(List<JsonNode> data, Links links) {

    public ArbeitnowPageDto {
        data = data == null ? List.of() : List.copyOf(data);
    }

    /** Only {@code next} is used: {@code null} means there is no further page. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Links(String next) {
    }

    public boolean hasNext() {
        return links != null && links.next() != null;
    }
}
