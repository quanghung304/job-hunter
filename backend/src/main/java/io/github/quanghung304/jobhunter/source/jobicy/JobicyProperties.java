package io.github.quanghung304.jobhunter.source.jobicy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.sources.jobicy.*} source-specific keys (docs/sources/jobicy.md);
 * {@code enabled}, {@code max-items} and {@code min-interval} are in SourcesProperties.
 *
 * @param baseUrl          API host, overridden in tests
 * @param queries          one feed request per query; results are deduplicated by job id
 * @param count            jobs per request (API accepts 1–200)
 * @param callDelay        pause between any two requests (rate rule: at least 2 s)
 * @param maxPagesPerQuery cursor pages per query (the feed only covers the last 7 days)
 */
@Validated
@ConfigurationProperties("app.sources.jobicy")
public record JobicyProperties(
        @NotBlank @DefaultValue("https://jobicy.com") String baseUrl,
        List<@Valid Query> queries,
        @Positive @Max(200) @DefaultValue("100") int count,
        @DefaultValue("2s") Duration callDelay,
        @Positive @DefaultValue("3") int maxPagesPerQuery) {

    /** Verified slugs (2026-10-17): see docs/sources/jobicy.md. */
    public static final List<Query> DEFAULT_QUERIES = List.of(
            new Query("europe", "engineering", null),
            new Query("anywhere", "engineering", null),
            new Query(null, null, "java"));

    public JobicyProperties {
        queries = queries == null || queries.isEmpty() ? DEFAULT_QUERIES : List.copyOf(queries);
    }

    /**
     * Filters of one request; null or blank values are omitted.
     *
     * @param geo      location slug, e.g. {@code europe}, {@code anywhere}, {@code italy}
     * @param industry category slug, e.g. {@code engineering}
     * @param tag      keyword search
     */
    public record Query(String geo, String industry, String tag) {

        @Override
        public String toString() {
            var parts = new StringBuilder();
            append(parts, "geo", geo);
            append(parts, "industry", industry);
            append(parts, "tag", tag);
            return parts.isEmpty() ? "(all)" : parts.toString();
        }

        private static void append(StringBuilder parts, String name, String value) {
            if (value != null && !value.isBlank()) {
                parts.append(parts.isEmpty() ? "" : "&").append(name).append('=').append(value.strip());
            }
        }
    }
}
