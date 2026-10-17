package io.github.quanghung304.jobhunter.source.weworkremotely;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.sources.weworkremotely.*} source-specific keys (docs/sources/weworkremotely.md);
 * {@code enabled}, {@code max-items} and {@code min-interval} are in SourcesProperties.
 *
 * @param baseUrl   site host, overridden in tests
 * @param feeds     RSS paths relative to the base URL, read in order
 * @param feedDelay pause between feed requests (rate rule: at least 2 s)
 */
@Validated
@ConfigurationProperties("app.sources.weworkremotely")
public record WeWorkRemotelyProperties(
        @NotBlank @DefaultValue("https://weworkremotely.com") String baseUrl,
        List<@NotBlank String> feeds,
        @DefaultValue("2s") Duration feedDelay) {

    /** Verified 2026-10-17 (HTTP 200, application/rss+xml). */
    public static final List<String> DEFAULT_FEEDS = List.of(
            "/categories/remote-back-end-programming-jobs.rss",
            "/categories/remote-full-stack-programming-jobs.rss",
            "/categories/remote-devops-sysadmin-jobs.rss");

    public WeWorkRemotelyProperties {
        feeds = feeds == null || feeds.isEmpty() ? DEFAULT_FEEDS : List.copyOf(feeds);
    }
}
