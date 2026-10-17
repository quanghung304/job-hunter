package io.github.quanghung304.jobhunter.source.weworkremotely;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/**
 * One RSS {@code <item>} (verified 2026-10-17, see docs/sources/weworkremotely.md). Standard
 * fields come from Rome; {@code region}, {@code country}, {@code state}, {@code skills},
 * {@code type} and {@code expires_at} are WWR's own elements. Text values are entity-decoded.
 *
 * @param raw every element of the item as a JSON object (rawPayload)
 */
public record WeWorkRemotelyItemDto(
        String title,
        String link,
        String guid,
        String descriptionHtml,
        Instant pubDate,
        List<String> categories,
        String region,
        String country,
        String state,
        String skills,
        String type,
        String expiresAt,
        JsonNode raw) {

    public WeWorkRemotelyItemDto {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }
}
