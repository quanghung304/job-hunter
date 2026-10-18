package io.github.quanghung304.jobhunter.pipeline;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} (pipeline cron; the digest cron comes with T-015). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class PipelineConfig {
}
