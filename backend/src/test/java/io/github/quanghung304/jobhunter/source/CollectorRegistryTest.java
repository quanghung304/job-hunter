package io.github.quanghung304.jobhunter.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollectorRegistryTest {

    @Test
    void enabledAppliesTheSourceSettingsAndSortsByCode() {
        var registry = new CollectorRegistry(List.of(collector("jobicy"), collector("arbeitnow"), collector("himalayas"),
                collector("unconfigured")), new SourcesProperties(Map.of(
                "jobicy", new SourcesProperties.Source(true, 200, null),
                "arbeitnow", new SourcesProperties.Source(true, 200, null),
                "himalayas", new SourcesProperties.Source(false, 200, null))));

        assertThat(registry.enabled()).extracting(JobCollector::sourceCode).containsExactly("arbeitnow", "jobicy");
        assertThat(registry.find("himalayas")).isPresent();
        assertThat(registry.find("unconfigured")).isPresent();
        assertThat(registry.find("linkedin")).isEmpty();
    }

    @Test
    void duplicateSourceCodesFailFast() {
        assertThatThrownBy(() -> new CollectorRegistry(List.of(collector("a"), collector("a")),
                new SourcesProperties(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a");
    }

    @Test
    void userAgentNamesTheOwnerContactWhenSet() {
        assertThat(HttpClients.userAgent("me@example.com")).isEqualTo("job-hunter/0.1 (+personal use; contact: me@example.com)");
        assertThat(HttpClients.userAgent(" ")).isEqualTo("job-hunter/0.1 (+personal use)");
        assertThat(HttpClients.userAgent(null)).isEqualTo("job-hunter/0.1 (+personal use)");
    }

    private static JobCollector collector(String code) {
        return new JobCollector() {
            @Override
            public String sourceCode() {
                return code;
            }

            @Override
            public CollectResult collect(CollectRequest request) {
                return CollectResult.completed(List.of(), 0, List.of());
            }
        };
    }
}
