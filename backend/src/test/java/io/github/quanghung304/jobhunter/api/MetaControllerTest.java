package io.github.quanghung304.jobhunter.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.quanghung304.jobhunter.common.ProgressBoard;
import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.ExtractionService;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.llm.LlmUsageService;
import io.github.quanghung304.jobhunter.pipeline.PipelineOrchestrator;
import io.github.quanghung304.jobhunter.pipeline.PipelineReport;
import io.github.quanghung304.jobhunter.pipeline.PipelineSchedule;
import io.github.quanghung304.jobhunter.pipeline.SourceReport;
import io.github.quanghung304.jobhunter.pipeline.Trigger;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.github.quanghung304.jobhunter.source.CollectorRegistry;
import io.github.quanghung304.jobhunter.source.CollectorRunner;
import io.github.quanghung304.jobhunter.source.CompanyService;
import io.github.quanghung304.jobhunter.tracking.JobBoardQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MetaController.class)
@Import({AdminViews.class, ProgressBoard.class})
class MetaControllerTest {

    private static final Instant STARTED = Instant.parse("2026-10-17T05:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProfileProvider profileProvider;

    @MockitoBean
    CollectorRegistry collectors;

    @MockitoBean
    CollectorRunner runner;

    @MockitoBean
    CompanyService companies;

    @MockitoBean
    PipelineOrchestrator pipeline;

    @MockitoBean
    PipelineSchedule schedule;

    @MockitoBean
    LlmUsageService llmUsage;

    @MockitoBean
    ExtractionService extraction;

    @MockitoBean
    AssessmentService assessments;

    @MockitoBean
    JobQueryService jobs;

    @MockitoBean
    JobBoardQuery board;

    @BeforeEach
    void setUp() {
        given(profileProvider.current()).willReturn(ProfileFixtures.example());
    }

    @Test
    void returnsMetaForTheCurrentProfile() throws Exception {
        given(collectors.allCodes()).willReturn(List.of("arbeitnow", "weworkremotely"));

        mvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sources[0].code").value("arbeitnow"))
                .andExpect(jsonPath("$.sources[1].name").value("We Work Remotely"))
                .andExpect(jsonPath("$.statuses.length()").value(8))
                .andExpect(jsonPath("$.statuses[0]").value("NEW"))
                .andExpect(jsonPath("$.employmentTypes[0]").value("FULL_TIME"))
                .andExpect(jsonPath("$.decisions[2]").value("UNDECIDED"))
                .andExpect(jsonPath("$.decisions.length()").value(3))
                .andExpect(jsonPath("$.checkResults[0]").value("ALL_OK"))
                .andExpect(jsonPath("$.checkResults.length()").value(4))
                .andExpect(jsonPath("$.profileId").value("hugh"))
                .andExpect(jsonPath("$.summaryLanguage").value("vi"));
    }

    @Test
    void statusBeforeTheFirstRun() throws Exception {
        given(pipeline.lastReport()).willReturn(Optional.empty());

        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastRun").isEmpty())
                .andExpect(jsonPath("$.pipelineRunning").value(false))
                .andExpect(jsonPath("$.llmAlerts").isEmpty());
    }

    @Test
    void statusShowsTheNewestRun() throws Exception {
        given(pipeline.lastReport()).willReturn(Optional.of(new PipelineReport(4, Trigger.MANUAL, STARTED,
                STARTED.plusSeconds(30), List.of(new SourceReport("arbeitnow", CollectStatus.FAILED, 0, 0, 0, 0, "503"),
                new SourceReport("jobicy", CollectStatus.SUCCESS, 10, 5, 4, 0, null)),
                4, 0, 10, 8, 2, 8, 0, 0, null)));
        given(pipeline.isRunning()).willReturn(true);
        given(assessments.countScoredSince("hugh", STARTED)).willReturn(9L);

        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastRun.startedAt").value("2026-10-17T05:00:00Z"))
                .andExpect(jsonPath("$.lastRun.newJobs").value(4))
                .andExpect(jsonPath("$.lastRun.scored").value(9))
                .andExpect(jsonPath("$.lastRun.status").value("PARTIAL"))
                .andExpect(jsonPath("$.pipelineRunning").value(true));
    }
}
