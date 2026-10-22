package io.github.quanghung304.jobhunter.source.ashby;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.sources.ashby.*} source-specific keys (docs/sources/ats.md);
 * {@code enabled}, {@code max-items} and {@code min-interval} are in SourcesProperties.
 *
 * @param baseUrl      Public Job Posting API host, overridden in tests
 * @param requestDelay pause between two company boards
 */
@Validated
@ConfigurationProperties("app.sources.ashby")
public record AshbyProperties(
        @NotBlank @DefaultValue("https://api.ashbyhq.com") String baseUrl,
        @DefaultValue("1s") Duration requestDelay) {
}
