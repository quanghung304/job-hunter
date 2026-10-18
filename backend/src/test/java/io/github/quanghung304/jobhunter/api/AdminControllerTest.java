package io.github.quanghung304.jobhunter.api;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.quanghung304.jobhunter.ingest.IngestResult;
import io.github.quanghung304.jobhunter.ingest.IngestService;
import io.github.quanghung304.jobhunter.pipeline.PipelineAlreadyRunningException;
import io.github.quanghung304.jobhunter.pipeline.PipelineOrchestrator;
import io.github.quanghung304.jobhunter.pipeline.Trigger;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.source.CollectRequest;
import io.github.quanghung304.jobhunter.source.CollectResult;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.github.quanghung304.jobhunter.source.CollectorRegistry;
import io.github.quanghung304.jobhunter.source.CollectorRunOutcome;
import io.github.quanghung304.jobhunter.source.CollectorRunner;
import io.github.quanghung304.jobhunter.source.JobCollector;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-17T07:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CollectorRegistry registry;

    @MockitoBean
    CollectorRunner runner;

    @MockitoBean
    IngestService ingestService;

    @MockitoBean
    ProfileProvider profileProvider;

    @MockitoBean
    Clock clock;

    @MockitoBean
    PipelineOrchestrator pipeline;

    private final JobCollector arbeitnow = mock(JobCollector.class);

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
        given(profileProvider.current()).willReturn(ProfileFixtures.example()); // maxAgeDays 21
        given(registry.find("arbeitnow")).willReturn(Optional.of(arbeitnow));
        given(runner.defaultRequest("arbeitnow", NOW.minus(Duration.ofDays(21))))
                .willReturn(new CollectRequest(NOW.minus(Duration.ofDays(21)), List.of(), 200));
    }

    @Test
    void collectsIngestsAndRecordsTheCounts() throws Exception {
        var result = new CollectResult(List.of(), 300, List.of("page 1 item x: missing slug"),
                CollectStatus.PARTIAL, null);
        given(runner.runNow(arbeitnow, new CollectRequest(NOW.minus(Duration.ofDays(21)), List.of(), 200)))
                .willReturn(new CollectorRunOutcome(7L, result, null));
        given(ingestService.ingest(anyList())).willReturn(new IngestResult(12, 9, 2, 1));

        mvc.perform(post("/api/admin/collect/arbeitnow"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.runId").value(7))
                .andExpect(jsonPath("$.collectStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.fetched").value(300))
                .andExpect(jsonPath("$.newRaw").value(12))
                .andExpect(jsonPath("$.newJobs").value(9))
                .andExpect(jsonPath("$.updatedJobs").value(2))
                .andExpect(jsonPath("$.skippedInvalid").value(1))
                .andExpect(jsonPath("$.warnings[0]").value("page 1 item x: missing slug"))
                .andExpect(jsonPath("$.error").isEmpty());
        verify(runner).recordIngestCounts(7L, 12, 9);
    }

    @Test
    void failedCollectorStillAnswersWithTheError() throws Exception {
        given(runner.runNow(arbeitnow, new CollectRequest(NOW.minus(Duration.ofDays(21)), List.of(), 200)))
                .willReturn(new CollectorRunOutcome(8L, CollectResult.failed(0, "page 1: 503"), null));
        given(ingestService.ingest(List.of())).willReturn(IngestResult.EMPTY);

        mvc.perform(post("/api/admin/collect/arbeitnow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectStatus").value("FAILED"))
                .andExpect(jsonPath("$.error").value("page 1: 503"))
                .andExpect(jsonPath("$.newRaw").value(0));
        verify(runner).recordIngestCounts(8L, 0, 0);
    }

    @Test
    void unknownSourceIsNotFound() throws Exception {
        given(registry.find("linkedin")).willReturn(Optional.empty());

        mvc.perform(post("/api/admin/collect/linkedin"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SOURCE_NOT_FOUND"));
    }

    @Test
    void pipelineRunIsAcceptedAndStartedAsync() throws Exception {
        mvc.perform(post("/api/admin/pipeline/run"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));

        verify(pipeline).startAsync(Trigger.MANUAL);
    }

    @Test
    void pipelineRunWhileRunningIs409() throws Exception {
        willThrow(new PipelineAlreadyRunningException()).given(pipeline).startAsync(Trigger.MANUAL);

        mvc.perform(post("/api/admin/pipeline/run"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIPELINE_ALREADY_RUNNING"));
    }
}
