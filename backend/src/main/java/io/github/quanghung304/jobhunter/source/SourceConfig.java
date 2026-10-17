package io.github.quanghung304.jobhunter.source;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SourceConfig {

    @Bean
    public Sleeper sleeper() {
        return Sleeper.SYSTEM;
    }
}
