package io.github.quanghung304.jobhunter.pipeline;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.pipeline.*} (config-spec.md §2); {@code collector-timeout} is read by the source module.
 *
 * @param cron           Spring cron; {@code "-"} disables the schedule (tests)
 * @param catchUpEnabled false disables the startup catch-up run (tests)
 */
@Validated
@ConfigurationProperties("app.pipeline")
public record PipelineProperties(
        @NotBlank @DefaultValue("0 0 7,19 * * *") String cron,
        @NotBlank @DefaultValue("Europe/Rome") String zone,
        @NotNull @DefaultValue("6h") Duration catchUpAfter,
        @DefaultValue("true") boolean catchUpEnabled) {
}
