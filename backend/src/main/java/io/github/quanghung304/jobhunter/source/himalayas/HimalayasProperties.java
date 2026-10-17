package io.github.quanghung304.jobhunter.source.himalayas;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.sources.himalayas.*} source-specific keys (docs/sources/himalayas.md);
 * {@code enabled}, {@code max-items} and {@code min-interval} are in SourcesProperties.
 *
 * @param baseUrl   API host, overridden in tests
 * @param apiKey    optional free API key (env {@code HIMALAYAS_API_KEY}); blank = public allowance
 * @param pageSize  jobs per request; the browse endpoint caps it at 20
 * @param pageDelay pause between page requests
 * @param maxPages  safety cap on pages per run
 */
@Validated
@ConfigurationProperties("app.sources.himalayas")
public record HimalayasProperties(
        @NotBlank @DefaultValue("https://himalayas.app") String baseUrl,
        String apiKey,
        @Positive @Max(20) @DefaultValue("20") int pageSize,
        @DefaultValue("2s") Duration pageDelay,
        @Positive @DefaultValue("25") int maxPages) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Never print the key. */
    @Override
    public String toString() {
        return "HimalayasProperties[baseUrl=" + baseUrl + ", apiKey=" + (hasApiKey() ? "***" : "none")
                + ", pageSize=" + pageSize + ", pageDelay=" + pageDelay + ", maxPages=" + maxPages + "]";
    }
}
