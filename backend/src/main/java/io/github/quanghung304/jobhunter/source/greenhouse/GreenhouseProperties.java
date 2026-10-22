package io.github.quanghung304.jobhunter.source.greenhouse;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.sources.greenhouse.*} source-specific keys (docs/sources/ats.md);
 * {@code enabled}, {@code max-items} and {@code min-interval} are in SourcesProperties.
 *
 * @param baseUrl      Job Board API host, overridden in tests
 * @param requestDelay pause between two company boards
 */
@Validated
@ConfigurationProperties("app.sources.greenhouse")
public record GreenhouseProperties(
        @NotBlank @DefaultValue("https://boards-api.greenhouse.io") String baseUrl,
        @DefaultValue("1s") Duration requestDelay) {
}
