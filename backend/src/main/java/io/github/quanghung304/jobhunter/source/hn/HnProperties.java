package io.github.quanghung304.jobhunter.source.hn;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.sources.hn-whoishiring.*} source-specific keys (docs/sources/hn-whoishiring.md);
 * {@code enabled}, {@code max-items} and {@code min-interval} are in SourcesProperties.
 *
 * @param baseUrl            Algolia HN Search API host, overridden in tests
 * @param callDelay          pause between the thread search and each thread fetch
 * @param searchHits         stories of {@code whoishiring} read to find the threads
 * @param previousThreadDays the previous month's thread is also fetched on days 1..N of a month (UTC)
 */
@Validated
@ConfigurationProperties("app.sources.hn-whoishiring")
public record HnProperties(
        @NotBlank @DefaultValue("https://hn.algolia.com") String baseUrl,
        @DefaultValue("1s") Duration callDelay,
        @Positive @Max(50) @DefaultValue("10") int searchHits,
        @DefaultValue("7") int previousThreadDays) {
}
