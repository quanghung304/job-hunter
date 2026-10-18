package io.github.quanghung304.jobhunter.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the UTC clock and the real {@link Sleeper}; inject them instead of calling
 * {@code Instant.now()} or {@code Thread.sleep}.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Sleeper sleeper() {
        return Sleeper.SYSTEM;
    }
}
