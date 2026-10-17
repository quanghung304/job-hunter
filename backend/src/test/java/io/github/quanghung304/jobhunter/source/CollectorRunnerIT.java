package io.github.quanghung304.jobhunter.source;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.TestcontainersConfiguration;
import io.github.quanghung304.jobhunter.source.persistence.CollectorRunRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Same context as ApplicationIT (cached): real config binding and a real collector_run table. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.profile.path=../config/profile.example.yaml")
@Import(TestcontainersConfiguration.class)
class CollectorRunnerIT {

    @Autowired
    CollectorRunner runner;

    @Autowired
    CollectorRunRepository runs;

    @Autowired
    CollectorRegistry registry;

    @Autowired
    SourcesProperties sources;

    @Test
    void applicationYmlEnablesArbeitnow() {
        assertThat(registry.enabled()).extracting(JobCollector::sourceCode).contains("arbeitnow");
        var arbeitnow = sources.source("arbeitnow").orElseThrow();
        assertThat(arbeitnow.maxItems()).isEqualTo(200);
        assertThat(arbeitnow.minInterval()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void successfulAndFailedRunsAreStored() {
        var ok = runner.run(collector("it-ok", CollectResult.completed(List.of(), 3, List.of())),
                new CollectRequest(null, List.of(), 5));
        var failed = runner.run(collector("it-failed", CollectResult.failed(0, "page 1: 503")),
                new CollectRequest(null, List.of(), 5));
        runner.recordIngestCounts(ok.runId(), 2, 1);

        var okRow = runs.findById(ok.runId()).orElseThrow();
        assertThat(okRow.getStatus()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(okRow.getFetchedCount()).isEqualTo(3);
        assertThat(okRow.getNewRawCount()).isEqualTo(2);
        assertThat(okRow.getNewJobCount()).isEqualTo(1);
        assertThat(okRow.getFinishedAt()).isNotNull();
        var failedRow = runs.findById(failed.runId()).orElseThrow();
        assertThat(failedRow.getStatus()).isEqualTo(CollectStatus.FAILED);
        assertThat(failedRow.getErrorMessage()).isEqualTo("page 1: 503");
    }

    private static JobCollector collector(String code, CollectResult result) {
        return new JobCollector() {
            @Override
            public String sourceCode() {
                return code;
            }

            @Override
            public CollectResult collect(CollectRequest request) {
                return result;
            }
        };
    }
}
