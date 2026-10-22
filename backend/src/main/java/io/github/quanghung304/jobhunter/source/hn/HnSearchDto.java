package io.github.quanghung304.jobhunter.source.hn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response of {@code GET /api/v1/search_by_date?tags=story,author_whoishiring} (verified
 * 2026-10-22, see docs/sources/hn-whoishiring.md): the monthly stories, newest first.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HnSearchDto(List<Hit> hits) {

    public HnSearchDto {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    /** One story; {@code created_at} is ISO-8601 UTC ("2026-10-01T15:02:07Z"). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit(
            @JsonProperty("objectID") String objectId,
            String title,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("created_at_i") Long createdAtEpoch) {
    }
}
