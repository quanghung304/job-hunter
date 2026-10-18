package io.github.quanghung304.jobhunter.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.quanghung304.jobhunter.Fixtures;
import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.TestcontainersConfiguration;
import io.github.quanghung304.jobhunter.common.RunCompleted;
import io.github.quanghung304.jobhunter.enrich.AnalysisResult;
import io.github.quanghung304.jobhunter.enrich.AnalysisService;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * End to end with two sources served by WireMock from real fixtures (Arbeitnow, Himalayas), the
 * other sources off, a fixed clock and the fake LLM: the pipeline collects, ingests,
 * prefilters and expires, and never calls an LLM (ADR-0010).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "app.profile.path=../config/profile.example.yaml",
        "app.llm.force-model=fake",
        "app.pipeline.collector-timeout=3s",
        "app.sources.arbeitnow.min-interval=0s", "app.sources.arbeitnow.page-delay=0s",
        "app.sources.himalayas.min-interval=0s", "app.sources.himalayas.page-delay=0s",
        "app.sources.jobicy.enabled=false", "app.sources.weworkremotely.enabled=false"})
@Import({TestcontainersConfiguration.class, PipelineOrchestratorIT.FixedClock.class})
@RecordApplicationEvents
class PipelineOrchestratorIT {

    static final Instant NOW = Instant.parse("2026-10-18T06:00:00Z");
    static final String ARBEITNOW = "/arbeitnow/api/job-board-api";
    static final String HIMALAYAS = "/himalayas/jobs/api";

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void sources(DynamicPropertyRegistry registry) {
        registry.add("app.sources.arbeitnow.base-url", () -> wm.baseUrl() + "/arbeitnow");
        registry.add("app.sources.himalayas.base-url", () -> wm.baseUrl() + "/himalayas");
    }

    @Autowired
    PipelineOrchestrator pipeline;

    @Autowired
    AnalysisService analysis;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents events;

    @BeforeEach
    void cleanState() {
        jdbc.execute("delete from job_assessment");
        jdbc.execute("delete from job_extraction");
        jdbc.execute("delete from llm_call");
        jdbc.execute("delete from raw_posting");
        jdbc.execute("delete from job");
        jdbc.execute("delete from collector_run");
        clock.set(NOW);
        fake.reset();
        wm.resetAll();
    }

    @Test
    void firstRunCollectsAndPrefiltersWithZeroLlmCalls() {
        stubArbeitnow();
        stubHimalayas();

        PipelineReport report = pipeline.runOnce(Trigger.MANUAL);

        assertThat(report.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(report.sources()).extracting(SourceReport::sourceCode).containsExactlyInAnyOrder("arbeitnow",
                "himalayas");
        long jobs = count("job");
        assertThat(jobs).isPositive();
        assertThat(report.newJobs()).isEqualTo(jobs);
        assertThat(report.prefilterEvaluated()).isEqualTo(jobs);
        assertThat(report.prefilterPassed() + report.prefilterRejected()).isEqualTo(jobs);
        assertThat(count("job_assessment")).isEqualTo(jobs);
        assertThat(report.notAnalyzed()).isEqualTo(report.prefilterPassed());
        assertThat(report.llmCalls()).isZero();
        assertThat(count("llm_call")).isZero();
        assertThat(count("job_extraction")).isZero();
        assertThat(fake.requests()).isEmpty();
        assertThat(events.stream(RunCompleted.class)).singleElement()
                .satisfies(e -> assertThat(e.newJobIds()).hasSize((int) jobs));
        assertThat(pipeline.lastReport()).contains(report);
    }

    @Test
    void secondRunWithNoNewDataChangesNothing() {
        stubArbeitnow();
        stubHimalayas();
        PipelineReport first = pipeline.runOnce(Trigger.MANUAL);
        String before = jdbc.queryForObject("select string_agg(id || ':' || updated_at, ',' order by id) "
                + "from job_assessment", String.class);
        clock.set(NOW.plusSeconds(60));

        PipelineReport second = pipeline.runOnce(Trigger.SCHEDULED);

        assertThat(second.newJobs()).isZero();
        assertThat(second.llmCalls()).isZero();
        assertThat(jdbc.queryForObject("select string_agg(id || ':' || updated_at, ',' order by id) "
                + "from job_assessment", String.class)).isEqualTo(before);
        assertThat(pipeline.recentReports()).startsWith(second, first).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void aFailingSourceDoesNotStopTheOthers() {
        wm.stubFor(get(urlPathEqualTo(ARBEITNOW)).willReturn(serverError()));
        stubHimalayas();

        PipelineReport report = pipeline.runOnce(Trigger.MANUAL);

        assertThat(report.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(source(report, "arbeitnow").status()).isEqualTo(CollectStatus.FAILED);
        assertThat(source(report, "himalayas").status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(report.newJobs()).isPositive();
    }

    @Test
    void aHangingSourceTimesOutAndTheOthersRun() {
        wm.stubFor(get(urlPathEqualTo(ARBEITNOW)).willReturn(okJson(Fixtures.text("arbeitnow/page1.json"))
                .withFixedDelay(6_000)));
        stubHimalayas();

        PipelineReport report = pipeline.runOnce(Trigger.MANUAL);

        assertThat(source(report, "arbeitnow").status()).isEqualTo(CollectStatus.FAILED);
        assertThat(source(report, "arbeitnow").message()).contains("timed out");
        assertThat(source(report, "himalayas").status()).isEqualTo(CollectStatus.SUCCESS);
    }

    @Test
    void profileChangeReassessesThePrefilter() {
        stubArbeitnow();
        stubHimalayas();
        pipeline.runOnce(Trigger.MANUAL);
        int rows = jdbc.update("update job_assessment set profile_hash = 'old-profile'");

        PipelineReport report = pipeline.runOnce(Trigger.MANUAL);

        assertThat(report.reassessed()).isEqualTo(rows).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from job_assessment where profile_hash = 'old-profile'",
                Long.class)).isZero();
        assertThat(report.llmCalls()).isZero();
    }

    @Test
    void jobsNotSeenForTwoWeeksExpireAndCannotBeAnalyzed() {
        stubArbeitnow();
        stubHimalayas();
        pipeline.runOnce(Trigger.MANUAL);
        long jobs = count("job");
        wm.resetAll();
        wm.stubFor(get(urlPathEqualTo(ARBEITNOW)).willReturn(okJson(Fixtures.text("arbeitnow/last-page.json"))));
        wm.stubFor(get(urlPathEqualTo(HIMALAYAS)).willReturn(serverError()));
        clock.set(NOW.plus(Duration.ofDays(15)));

        PipelineReport report = pipeline.runOnce(Trigger.SCHEDULED);

        assertThat(report.expired()).isEqualTo(jobs);
        assertThat(jdbc.queryForObject("select count(*) from job where status = 'EXPIRED'", Long.class))
                .isEqualTo(jobs);
        assertThat(report.notAnalyzed()).isZero();
        long expiredId = jdbc.queryForObject("select min(id) from job", Long.class);
        assertThat(analysis.analyze(expiredId).outcome()).isEqualTo(AnalysisResult.Outcome.JOB_EXPIRED);
        assertThat(fake.requests()).isEmpty();
    }

    private static void stubArbeitnow() {
        wm.stubFor(get(urlPathEqualTo(ARBEITNOW)).atPriority(5)
                .willReturn(okJson(Fixtures.text("arbeitnow/last-page.json"))));
        wm.stubFor(get(urlPathEqualTo(ARBEITNOW)).withQueryParam("page", equalTo("1")).atPriority(1)
                .willReturn(okJson(Fixtures.text("arbeitnow/page1.json"))));
    }

    private static void stubHimalayas() {
        wm.stubFor(get(urlPathEqualTo(HIMALAYAS)).withQueryParam("cursor", absent())
                .willReturn(okJson(Fixtures.text("himalayas/page1.json"))));
        wm.stubFor(get(urlPathEqualTo(HIMALAYAS)).withQueryParam("cursor", matching(".+"))
                .willReturn(okJson(Fixtures.text("himalayas/last-page.json"))));
    }

    private static SourceReport source(PipelineReport report, String code) {
        return report.sources().stream().filter(s -> s.sourceCode().equals(code)).findFirst().orElseThrow();
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }
}
