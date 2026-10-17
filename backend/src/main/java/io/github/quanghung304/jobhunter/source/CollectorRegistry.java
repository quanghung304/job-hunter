package io.github.quanghung304.jobhunter.source;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** All {@link JobCollector} beans; {@link #enabled()} applies {@code app.sources.<code>.enabled}. */
@Component
public class CollectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(CollectorRegistry.class);

    private final Map<String, JobCollector> byCode;
    private final SourcesProperties properties;

    public CollectorRegistry(List<JobCollector> collectors, SourcesProperties properties) {
        this.byCode = collectors.stream().collect(Collectors.toUnmodifiableMap(
                JobCollector::sourceCode, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("Two collectors share the source code " + a.sourceCode());
                }));
        this.properties = properties;
        byCode.keySet().stream()
                .filter(code -> properties.source(code).isEmpty())
                .forEach(code -> log.warn("Collector '{}' has no app.sources.{} settings and stays disabled", code, code));
    }

    /** Enabled collectors, sorted by source code (stable run order). */
    public List<JobCollector> enabled() {
        return byCode.values().stream()
                .filter(c -> properties.source(c.sourceCode()).map(SourcesProperties.Source::enabled).orElse(false))
                .sorted(Comparator.comparing(JobCollector::sourceCode))
                .toList();
    }

    /** Any registered collector, enabled or not (admin "collect one source"). */
    public Optional<JobCollector> find(String sourceCode) {
        return Optional.ofNullable(byCode.get(sourceCode));
    }
}
