package io.github.quanghung304.jobhunter.source;

import java.time.Instant;
import java.util.List;

/**
 * What to collect in one run.
 *
 * @param since    skip postings older than this; {@code null} = no age limit
 * @param keywords optional search terms for sources that support them
 * @param maxItems maximum number of postings to return
 */
public record CollectRequest(Instant since, List<String> keywords, int maxItems) {

    public CollectRequest {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        if (maxItems <= 0) {
            throw new IllegalArgumentException("maxItems must be positive: " + maxItems);
        }
    }
}
