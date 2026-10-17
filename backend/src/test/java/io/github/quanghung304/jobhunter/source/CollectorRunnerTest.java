package io.github.quanghung304.jobhunter.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.quanghung304.jobhunter.source.persistence.CollectorRun;
import io.github.quanghung304.jobhunter.source.persistence.CollectorRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CollectorRunnerTest {

    private static final Instant NOW = Instant.parse("2026-10-17T07:00:00Z");
    private static final String CODE = "fake";

    private final CollectorRunRepository runs = mock(CollectorRunRepository.class);
    /** Snapshots of each saved row, since the runner saves the same entity twice. */
    private final List<String> savedStates = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<CollectorRun> lastSaved = new AtomicReference<>();
    private CollectorRunner runner;

    @BeforeEach
    void setUp() {
        given(runs.save(any(CollectorRun.class))).willAnswer(call -> {
            CollectorRun run = call.getArgument(0);
            if (run.getId() == null) {
                ReflectionTestUtils.setField(run, "id", 41L);
            }
            savedStates.add(run.getStatus() + "/" + run.getFetchedCount());
            lastSaved.set(run);
            return run;
        });
        runner = runner(Duration.ofSeconds(5), null);
    }

    @AfterEach
    void tearDown() {
        runner.shutdown();
    }

    @Test
    void successfulRunIsRecorded() {
        var postings = List.of(posting("a"), posting("b"));

        CollectorRunOutcome outcome = runner.run(collector(r -> CollectResult.completed(postings, 7, List.of())),
                new CollectRequest(null, List.of(), 10));

        assertThat(outcome.skipped()).isFalse();
        assertThat(outcome.runId()).isEqualTo(41L);
        assertThat(outcome.result().postings()).hasSize(2);
        assertThat(savedStates).containsExactly("null/0", "SUCCESS/7");
        CollectorRun run = lastSaved.get();
        assertThat(run.getSourceCode()).isEqualTo(CODE);
        assertThat(run.getStartedAt()).isEqualTo(NOW);
        assertThat(run.getFinishedAt()).isEqualTo(NOW);
        assertThat(run.getErrorMessage()).isNull();
    }

    @Test
    void failedResultIsRecordedWithItsMessage() {
        CollectorRunOutcome outcome = runner.run(collector(r -> CollectResult.failed(0, "page 1: 500 Server Error")),
                new CollectRequest(null, List.of(), 10));

        assertThat(outcome.result().status()).isEqualTo(CollectStatus.FAILED);
        assertThat(lastSaved.get().getStatus()).isEqualTo(CollectStatus.FAILED);
        assertThat(lastSaved.get().getErrorMessage()).isEqualTo("page 1: 500 Server Error");
    }

    @Test
    void exceptionFromTheCollectorBecomesAFailedRun() {
        CollectorRunOutcome outcome = runner.run(collector(r -> {
            throw new IllegalStateException("boom");
        }), new CollectRequest(null, List.of(), 10));

        assertThat(outcome.result().status()).isEqualTo(CollectStatus.FAILED);
        assertThat(outcome.result().errorMessage()).contains("IllegalStateException").contains("boom");
        assertThat(lastSaved.get().getStatus()).isEqualTo(CollectStatus.FAILED);
    }

    @Test
    void slowCollectorTimesOutAndIsInterrupted() throws Exception {
        runner = runner(Duration.ofMillis(200), null);
        var interrupted = new CountDownLatch(1);
        long start = System.nanoTime();

        CollectorRunOutcome outcome = runner.run(collector(r -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return CollectResult.completed(List.of(), 0, List.of());
        }), new CollectRequest(null, List.of(), 10));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        assertThat(outcome.result().status()).isEqualTo(CollectStatus.FAILED);
        assertThat(outcome.result().errorMessage()).isEqualTo("timed out after PT0.2S");
        assertThat(lastSaved.get().getErrorMessage()).isEqualTo("timed out after PT0.2S");
        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void recentSuccessfulRunSkipsTheCollector() {
        runner = runner(Duration.ofSeconds(5), Duration.ofHours(1));
        given(runs.findFirstBySourceCodeAndStatusOrderByStartedAtDesc(CODE, CollectStatus.SUCCESS))
                .willReturn(Optional.of(new CollectorRun(CODE, NOW.minus(Duration.ofMinutes(30)))));
        var called = new AtomicBoolean();

        CollectorRunOutcome outcome = runner.run(collector(r -> {
            called.set(true);
            return CollectResult.completed(List.of(), 0, List.of());
        }), new CollectRequest(null, List.of(), 10));

        assertThat(outcome.skipped()).isTrue();
        assertThat(outcome.skipReason()).contains("min-interval");
        assertThat(outcome.runId()).isNull();
        assertThat(called).isFalse();
        verify(runs, never()).save(any());
    }

    @Test
    void olderSuccessfulRunDoesNotSkip() {
        runner = runner(Duration.ofSeconds(5), Duration.ofHours(1));
        given(runs.findFirstBySourceCodeAndStatusOrderByStartedAtDesc(CODE, CollectStatus.SUCCESS))
                .willReturn(Optional.of(new CollectorRun(CODE, NOW.minus(Duration.ofHours(2)))));

        CollectorRunOutcome outcome = runner.run(collector(r -> CollectResult.completed(List.of(), 0, List.of())),
                new CollectRequest(null, List.of(), 10));

        assertThat(outcome.skipped()).isFalse();
    }

    @Test
    void runWithSinceUsesMaxItemsFromTheSourceSettings() {
        runner = new CollectorRunner(runs, new SourcesProperties(Map.of(CODE,
                new SourcesProperties.Source(true, 33, null))), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(5));
        var seen = new AtomicReference<CollectRequest>();

        runner.run(collector(r -> {
            seen.set(r);
            return CollectResult.completed(List.of(), 0, List.of());
        }), NOW.minus(Duration.ofDays(21)));

        assertThat(seen.get().maxItems()).isEqualTo(33);
        assertThat(seen.get().since()).isEqualTo(NOW.minus(Duration.ofDays(21)));
    }

    @Test
    void ingestCountsAreAddedToTheRun() {
        var run = new CollectorRun(CODE, NOW);
        given(runs.findById(41L)).willReturn(Optional.of(run));

        runner.recordIngestCounts(41L, 12, 5);

        assertThat(run.getNewRawCount()).isEqualTo(12);
        assertThat(run.getNewJobCount()).isEqualTo(5);
        verify(runs).save(run);
    }

    @Test
    void partialRunSummarizesWarningsWithinTheColumnLimit() {
        var warnings = new ArrayList<String>();
        for (int i = 0; i < 8; i++) {
            warnings.add("item " + i + ": " + "x".repeat(600));
        }

        String message = CollectorRunner.errorMessage(CollectResult.completed(List.of(), 8, warnings));

        assertThat(message).startsWith("8 warning(s): item 0").hasSize(CollectorRunner.MAX_ERROR_LENGTH).endsWith("...");
        assertThat(CollectorRunner.errorMessage(CollectResult.completed(List.of(), 1, List.of("w1"))))
                .isEqualTo("1 warning(s): w1");
    }

    private CollectorRunner runner(Duration timeout, Duration minInterval) {
        var sources = new SourcesProperties(Map.of(CODE, new SourcesProperties.Source(true, 200, minInterval)));
        return new CollectorRunner(runs, sources, Clock.fixed(NOW, ZoneOffset.UTC), timeout);
    }

    private static JobCollector collector(Function<CollectRequest, CollectResult> body) {
        return new JobCollector() {
            @Override
            public String sourceCode() {
                return CODE;
            }

            @Override
            public CollectResult collect(CollectRequest request) {
                return body.apply(request);
            }
        };
    }

    private static NormalizedPosting posting(String id) {
        return new NormalizedPosting(CODE, id, "https://example.com/" + id, "Java Dev", "Acme", null,
                RemoteHint.REMOTE, null, "text", null, null, null, null, null, List.of(), NOW, null);
    }
}
