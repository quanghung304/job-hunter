package io.github.quanghung304.jobhunter.source;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings shared by all sources: {@code app.sources.<code>.enabled|max-items|min-interval}
 * (config-spec.md §2). Source-specific keys under the same prefix are bound by each source's
 * own properties record. Bound at {@code app} so that the map sits at {@code app.sources}.
 */
@Validated
@ConfigurationProperties("app")
public record SourcesProperties(Map<String, @Valid Source> sources) {

    public static final int DEFAULT_MAX_ITEMS = 200;

    public SourcesProperties {
        sources = sources == null ? Map.of() : Map.copyOf(sources);
    }

    public Optional<Source> source(String code) {
        return Optional.ofNullable(sources.get(code));
    }

    /**
     * @param enabled     off unless set
     * @param maxItems    postings per run
     * @param minInterval skip a run if the last successful one is more recent; null = never skip
     */
    public record Source(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("" + DEFAULT_MAX_ITEMS) @Positive int maxItems,
            Duration minInterval) {
    }
}
