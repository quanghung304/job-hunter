package io.github.quanghung304.jobhunter.source.lever;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.sources.lever.*} source-specific keys (docs/sources/ats.md);
 * {@code enabled}, {@code max-items} and {@code min-interval} are in SourcesProperties.
 *
 * @param baseUrl      Postings API host for {@code LEVER} boards, overridden in tests
 * @param euBaseUrl    Postings API host for {@code LEVER_EU} boards
 * @param requestDelay pause between two company boards (Lever: 1 request per second)
 */
@Validated
@ConfigurationProperties("app.sources.lever")
public record LeverProperties(
        @NotBlank @DefaultValue("https://api.lever.co") String baseUrl,
        @NotBlank @DefaultValue("https://api.eu.lever.co") String euBaseUrl,
        @DefaultValue("1s") Duration requestDelay) {
}
