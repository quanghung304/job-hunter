package io.github.quanghung304.jobhunter.source;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.http.*}: timeouts of the source HTTP clients (overview.md, Cross-cutting). */
@ConfigurationProperties("app.http")
public record HttpProperties(
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("20s") Duration readTimeout) {
}
