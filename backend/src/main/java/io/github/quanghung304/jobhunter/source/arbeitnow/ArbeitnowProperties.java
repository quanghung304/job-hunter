package io.github.quanghung304.jobhunter.source.arbeitnow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.sources.arbeitnow.*} source-specific keys (docs/sources/arbeitnow.md);
 * {@code enabled}, {@code max-items} and {@code min-interval} are in SourcesProperties.
 *
 * @param baseUrl    API host, overridden in tests
 * @param remoteOnly skip items with {@code remote == false}
 * @param pageDelay  pause between page requests (rate rule: at least 1 s; 2 s avoids the
 *                   Cloudflare challenge seen after ~13 pages in quick succession)
 * @param maxPages   safety cap on pages per run
 */
@Validated
@ConfigurationProperties("app.sources.arbeitnow")
public record ArbeitnowProperties(
        @NotBlank @DefaultValue("https://www.arbeitnow.com") String baseUrl,
        @DefaultValue("true") boolean remoteOnly,
        @DefaultValue("2s") Duration pageDelay,
        @Positive @DefaultValue("20") int maxPages) {
}
