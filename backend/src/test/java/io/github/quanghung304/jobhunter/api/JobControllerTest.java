package io.github.quanghung304.jobhunter.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.enrich.AnalysisResult;
import io.github.quanghung304.jobhunter.enrich.AnalysisService;
import io.github.quanghung304.jobhunter.enrich.AssessmentStatus;
import io.github.quanghung304.jobhunter.enrich.AssessmentView;
import io.github.quanghung304.jobhunter.enrich.CheckCounts;
import io.github.quanghung304.jobhunter.enrich.CheckResult;
import io.github.quanghung304.jobhunter.enrich.EligibilityCheck;
import io.github.quanghung304.jobhunter.enrich.EligibilityRule;
import io.github.quanghung304.jobhunter.enrich.JobAttributes.RemoteType;
import io.github.quanghung304.jobhunter.enrich.PrefilterResult;
import io.github.quanghung304.jobhunter.ingest.JobNotFoundException;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.ingest.PostingView;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.scoring.ScoringResult;
import io.github.quanghung304.jobhunter.scoring.ScoringService;
import io.github.quanghung304.jobhunter.tracking.AnalysisStage;
import io.github.quanghung304.jobhunter.tracking.ChecksFilter;
import io.github.quanghung304.jobhunter.tracking.DecisionFilter;
import io.github.quanghung304.jobhunter.tracking.EligibilityDecision;
import io.github.quanghung304.jobhunter.tracking.TrackingEventKind;
import io.github.quanghung304.jobhunter.tracking.IllegalStatusTransitionException;
import io.github.quanghung304.jobhunter.tracking.JobBoardFilter;
import io.github.quanghung304.jobhunter.tracking.JobBoardPage;
import io.github.quanghung304.jobhunter.tracking.JobBoardQuery;
import io.github.quanghung304.jobhunter.tracking.JobBoardRow;
import io.github.quanghung304.jobhunter.tracking.JobDetailQuery;
import io.github.quanghung304.jobhunter.tracking.JobDetailView;
import io.github.quanghung304.jobhunter.tracking.JobSort;
import io.github.quanghung304.jobhunter.tracking.StatusChange;
import io.github.quanghung304.jobhunter.tracking.TrackingService;
import io.github.quanghung304.jobhunter.tracking.TrackingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobController.class)
@Import(JobDetailMapper.class)
class JobControllerTest {

    static final long ID = 42;
    static final Instant POSTED = Instant.parse("2026-10-16T09:00:00Z");
    static final Instant SEEN = Instant.parse("2026-10-16T10:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    JobBoardQuery board;

    @MockitoBean
    JobDetailQuery details;

    @MockitoBean
    TrackingService tracking;

    @MockitoBean
    AnalysisService analysis;

    @MockitoBean
    ScoringService scoring;

    @MockitoBean
    ProfileProvider profiles;

    @BeforeEach
    void setUp() {
        given(profiles.current()).willReturn(ProfileFixtures.example()); // id "hugh"
    }

    static JobBoardRow row(Integer score, TrackingStatus status, AnalysisStage stage) {
        return new JobBoardRow(ID, "Java Developer", "Acme", "Remote (EU)", score, score == null ? null : "Good match",
                null, new CheckCounts(9, 1, 0), RemoteType.REMOTE_RESTRICTED, List.of("EU"),
                List.of(EmploymentType.CONTRACT),
                POSTED, SEEN, List.of("himalayas", "weworkremotely"), status, JobStatus.ACTIVE, stage);
    }

    JobDetailView detail(Integer score, AnalysisStage stage) throws Exception {
        JobView job = new JobView(ID, "Java Developer", "Acme", "acme", "Remote (EU)", RemoteHint.REMOTE, "contract",
                "Build things.", "https://apply.example/42", new BigDecimal("60000"), null, "EUR", SalaryPeriod.YEAR,
                List.of("java"), POSTED, SEEN, SEEN, JobStatus.ACTIVE);
        var details = score == null ? null : json.readTree("""
                {"breakdown":{"skills":30,"experience":15,"arrangement":15,"domain":8,"other":4},"score":72,
                 "strengths":["Java 17"],"gaps":["Kafka"],"redFlags":[],"summary":"Good match","applyAdvice":"Go"}""");
        var checks = List.of(
                new EligibilityCheck(EligibilityRule.WORK_MODE, true, CheckResult.OK, null, "Remote anywhere"),
                new EligibilityCheck(EligibilityRule.TIMEZONE, false, CheckResult.UNKNOWN, null, "Not stated"));
        var assessment = new AssessmentView(ID, "hugh", "hash", PrefilterResult.PASS, List.of(), checks, 7L, score,
                details, score == null ? null : "Good match", "score-v2", "primary", AssessmentStatus.DONE, 1);
        return new JobDetailView(row(score, TrackingStatus.SHORTLISTED, stage), job, null, assessment,
                List.of(new PostingView("himalayas", "https://himalayas.app/jobs/42", SEEN, SEEN)), "Ask about stack",
                null,
                List.of(new StatusChange(TrackingEventKind.STATUS, "NEW", "SHORTLISTED", "nice", SEEN)),
                Set.of(TrackingStatus.NEW, TrackingStatus.DISMISSED, TrackingStatus.APPLIED));
    }

    @Test
    void listWithoutParametersUsesTheDefaultView() throws Exception {
        given(board.page("hugh", JobBoardFilter.NONE, JobSort.SCORE, 0, 25))
                .willReturn(new JobBoardPage(List.of(row(82, TrackingStatus.NEW, AnalysisStage.SCORED)), 0, 25, 51));

        mvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalItems").value(51))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].id").value(42))
                .andExpect(jsonPath("$.items[0].title").value("Java Developer"))
                .andExpect(jsonPath("$.items[0].company").value("Acme"))
                .andExpect(jsonPath("$.items[0].locationText").value("Remote (EU)"))
                .andExpect(jsonPath("$.items[0].score").value(82))
                .andExpect(jsonPath("$.items[0].scoreBand").value("GOOD"))
                .andExpect(jsonPath("$.items[0].summary").value("Good match"))
                .andExpect(jsonPath("$.items[0].eligibilityDecision").isEmpty())
                .andExpect(jsonPath("$.items[0].checks.ok").value(9))
                .andExpect(jsonPath("$.items[0].checks.unknown").value(1))
                .andExpect(jsonPath("$.items[0].checks.fail").value(0))
                .andExpect(jsonPath("$.items[0].remoteType").value("REMOTE_RESTRICTED"))
                .andExpect(jsonPath("$.items[0].allowedRegions[0]").value("EU"))
                .andExpect(jsonPath("$.items[0].employmentTypes[0]").value("CONTRACT"))
                .andExpect(jsonPath("$.items[0].postedAt").value("2026-10-16T09:00:00Z"))
                .andExpect(jsonPath("$.items[0].firstSeenAt").value("2026-10-16T10:00:00Z"))
                .andExpect(jsonPath("$.items[0].sources[0].code").value("himalayas"))
                .andExpect(jsonPath("$.items[0].sources[0].name").value("Himalayas"))
                .andExpect(jsonPath("$.items[0].sources[1].name").value("We Work Remotely"))
                .andExpect(jsonPath("$.items[0].status").value("NEW"))
                .andExpect(jsonPath("$.items[0].jobStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].analysisStage").value("SCORED"));
    }

    @Test
    void listPassesEveryFilter() throws Exception {
        given(board.page(eq("hugh"), any(), any(), anyInt(), anyInt()))
                .willReturn(new JobBoardPage(List.of(), 2, 10, 0));

        mvc.perform(get("/api/jobs")
                        .param("status", "NEW", "APPLIED")
                        .param("decision", "UNDECIDED")
                        .param("checks", "HAS_FAIL", "HAS_UNKNOWN")
                        .param("minScore", "50").param("maxScore", "90")
                        .param("source", "jobicy")
                        .param("employmentType", "CONTRACT")
                        .param("postedWithinDays", "7")
                        .param("q", "java")
                        .param("includeExpired", "true")
                        .param("analysisStage", "ANALYZED")
                        .param("sort", "posted")
                        .param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalPages").value(0));

        ArgumentCaptor<JobBoardFilter> filter = ArgumentCaptor.forClass(JobBoardFilter.class);
        verify(board).page(eq("hugh"), filter.capture(), eq(JobSort.POSTED), eq(2), eq(10));
        assertThat(filter.getValue()).isEqualTo(new JobBoardFilter(Set.of(TrackingStatus.NEW, TrackingStatus.APPLIED),
                Set.of(DecisionFilter.UNDECIDED), Set.of(ChecksFilter.HAS_FAIL, ChecksFilter.HAS_UNKNOWN), 50, 90,
                Set.of("jobicy"), Set.of(EmploymentType.CONTRACT), 7, "java", true, Set.of(AnalysisStage.ANALYZED)));
    }

    @Test
    void invalidListParametersAreRejected() throws Exception {
        for (String[] param : List.of(new String[] {"size", "101"}, new String[] {"size", "0"},
                new String[] {"page", "-1"}, new String[] {"minScore", "101"}, new String[] {"sort", "title"},
                new String[] {"postedWithinDays", "0"})) {
            mvc.perform(get("/api/jobs").param(param[0], param[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(get("/api/jobs").param("status", "LOST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verify(board, never()).page(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void detailHasTheSummaryFieldsAndTheDetails() throws Exception {
        given(details.find(ID, "hugh")).willReturn(Optional.of(detail(72, AnalysisStage.SCORED)));

        mvc.perform(get("/api/jobs/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.score").value(72))
                .andExpect(jsonPath("$.scoreBand").value("GOOD"))
                .andExpect(jsonPath("$.status").value("SHORTLISTED"))
                .andExpect(jsonPath("$.description").value("Build things."))
                .andExpect(jsonPath("$.applyUrl").value("https://apply.example/42"))
                .andExpect(jsonPath("$.salary.min").value(60000))
                .andExpect(jsonPath("$.salary.max").isEmpty())
                .andExpect(jsonPath("$.salary.currency").value("EUR"))
                .andExpect(jsonPath("$.salary.period").value("YEAR"))
                .andExpect(jsonPath("$.tags[0]").value("java"))
                .andExpect(jsonPath("$.extraction").isEmpty())
                .andExpect(jsonPath("$.eligibilityChecks[1].code").value("TIMEZONE"))
                .andExpect(jsonPath("$.eligibilityChecks[1].critical").value(false))
                .andExpect(jsonPath("$.eligibilityChecks[1].result").value("UNKNOWN"))
                .andExpect(jsonPath("$.eligibilityChecks[1].value").value("Not stated"))
                .andExpect(jsonPath("$.eligibilityChecks[1].reason").isEmpty())
                .andExpect(jsonPath("$.eligibilityDecidedAt").isEmpty())
                .andExpect(jsonPath("$.history[0].kind").value("STATUS"))
                .andExpect(jsonPath("$.prefilterReasons").isEmpty())
                .andExpect(jsonPath("$.scoreBreakdown.skills").value(30))
                .andExpect(jsonPath("$.scoreBreakdown.other").value(4))
                .andExpect(jsonPath("$.strengths[0]").value("Java 17"))
                .andExpect(jsonPath("$.gaps[0]").value("Kafka"))
                .andExpect(jsonPath("$.redFlags").isEmpty())
                .andExpect(jsonPath("$.applyAdvice").value("Go"))
                .andExpect(jsonPath("$.postings[0].sourceCode").value("himalayas"))
                .andExpect(jsonPath("$.postings[0].sourceName").value("Himalayas"))
                .andExpect(jsonPath("$.postings[0].url").value("https://himalayas.app/jobs/42"))
                .andExpect(jsonPath("$.notes").value("Ask about stack"))
                .andExpect(jsonPath("$.history[0].from").value("NEW"))
                .andExpect(jsonPath("$.history[0].to").value("SHORTLISTED"))
                .andExpect(jsonPath("$.history[0].note").value("nice"))
                .andExpect(jsonPath("$.history[0].at").value("2026-10-16T10:00:00Z"))
                .andExpect(jsonPath("$.allowedTransitions.length()").value(3));
    }

    @Test
    void unscoredDetailHasNoScoreFields() throws Exception {
        given(details.find(ID, "hugh")).willReturn(Optional.of(detail(null, AnalysisStage.ANALYZED)));

        mvc.perform(get("/api/jobs/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").isEmpty())
                .andExpect(jsonPath("$.scoreBand").value("NONE"))
                .andExpect(jsonPath("$.scoreBreakdown").isEmpty())
                .andExpect(jsonPath("$.strengths").isEmpty())
                .andExpect(jsonPath("$.applyAdvice").isEmpty());
    }

    @Test
    void unknownJobIs404() throws Exception {
        given(details.find(anyLong(), eq("hugh"))).willReturn(Optional.empty());

        mvc.perform(get("/api/jobs/7"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void statusChangeAnswersTheUpdatedSummary() throws Exception {
        given(board.find(ID, "hugh")).willReturn(Optional.of(row(null, TrackingStatus.SHORTLISTED,
                AnalysisStage.NOT_ANALYZED)));

        mvc.perform(post("/api/jobs/42/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"SHORTLISTED\",\"note\":\"nice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHORTLISTED"))
                .andExpect(jsonPath("$.scoreBand").value("NONE"));
        verify(tracking).changeStatus(ID, "hugh", TrackingStatus.SHORTLISTED, "nice");
    }

    @Test
    void illegalTransitionIs409() throws Exception {
        willThrow(new IllegalStatusTransitionException(TrackingStatus.DISMISSED, TrackingStatus.APPLIED))
                .given(tracking).changeStatus(ID, "hugh", TrackingStatus.APPLIED, null);

        mvc.perform(post("/api/jobs/42/status").contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"APPLIED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATUS_TRANSITION"));
    }

    @Test
    void statusChangeOfUnknownJobIs404AndBadBodyIs400() throws Exception {
        willThrow(new JobNotFoundException(7)).given(tracking).changeStatus(7, "hugh", TrackingStatus.NEW, null);

        mvc.perform(post("/api/jobs/7/status").contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"NEW\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
        mvc.perform(post("/api/jobs/7/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void analyzeAnswersTheDetailAlsoForAFailedAttempt() throws Exception {
        given(details.find(ID, "hugh")).willReturn(Optional.of(detail(null, AnalysisStage.FAILED)));
        given(analysis.analyze(ID)).willReturn(new AnalysisResult(ID, AnalysisResult.Outcome.FAILED, List.of(), true,
                "INVALID_OUTPUT: bad json"));

        mvc.perform(post("/api/jobs/42/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisStage").value("FAILED"));
    }

    @Test
    void analyzeRefusalsHaveTheirCodes() throws Exception {
        expectAnalyzeError(AnalysisResult.Outcome.JOB_EXPIRED, 409, "JOB_EXPIRED");
        expectAnalyzeError(AnalysisResult.Outcome.PREFILTER_REJECTED, 409, "JOB_PREFILTER_REJECTED");
        expectAnalyzeError(AnalysisResult.Outcome.LLM_PAUSED, 409, "LLM_PAUSED")
                .andExpect(jsonPath("$.purpose").value("EXTRACT"));
        expectAnalyzeError(AnalysisResult.Outcome.BUDGET_REACHED, 409, "BUDGET_REACHED");

        given(analysis.analyze(7)).willThrow(new JobNotFoundException(7));
        mvc.perform(post("/api/jobs/7/analyze"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions expectAnalyzeError(AnalysisResult.Outcome outcome,
            int status, String code) throws Exception {
        given(analysis.analyze(ID)).willReturn(new AnalysisResult(ID, outcome, List.of(), false, "detail"));
        return mvc.perform(post("/api/jobs/42/analyze"))
                .andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void eligibilityDecisionIsSetAndAnswersTheSummary() throws Exception {
        given(board.find(ID, "hugh")).willReturn(Optional.of(row(null, TrackingStatus.NEW, AnalysisStage.ANALYZED)));

        mvc.perform(put("/api/jobs/42/eligibility").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"INELIGIBLE\",\"note\":\"US only\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.checks.unknown").value(1));
        verify(tracking).setEligibilityDecision(ID, "hugh", EligibilityDecision.INELIGIBLE, "US only");

        mvc.perform(put("/api/jobs/42/eligibility").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":null}"))
                .andExpect(status().isOk());
        verify(tracking).setEligibilityDecision(ID, "hugh", null, null);

        willThrow(new JobNotFoundException(7)).given(tracking).setEligibilityDecision(7, "hugh",
                EligibilityDecision.ELIGIBLE, null);
        mvc.perform(put("/api/jobs/7/eligibility").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ELIGIBLE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void scoreAnswersTheDetailWhenScoredOrAlreadyScored() throws Exception {
        given(details.find(ID, "hugh")).willReturn(Optional.of(detail(72, AnalysisStage.SCORED)));
        for (ScoringResult.Outcome outcome : List.of(ScoringResult.Outcome.SCORED,
                ScoringResult.Outcome.ALREADY_SCORED)) {
            given(scoring.score(eq(ID), any())).willReturn(new ScoringResult(ID, outcome, 72, "Good match",
                    outcome == ScoringResult.Outcome.SCORED, null));

            mvc.perform(post("/api/jobs/42/score"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.score").value(72));
        }
    }

    @Test
    void scoreRefusalsHaveTheirCodes() throws Exception {
        expectScoreError(ScoringResult.Outcome.JOB_EXPIRED, "JOB_EXPIRED");
        expectScoreError(ScoringResult.Outcome.NOT_ANALYZED, "JOB_NOT_ANALYZED");
        expectScoreError(ScoringResult.Outcome.MARKED_INELIGIBLE, "JOB_MARKED_INELIGIBLE");
        expectScoreError(ScoringResult.Outcome.LLM_PAUSED, "LLM_PAUSED");
        expectScoreError(ScoringResult.Outcome.BUDGET_REACHED, "BUDGET_REACHED");
    }

    private void expectScoreError(ScoringResult.Outcome outcome, String code) throws Exception {
        given(scoring.score(eq(ID), any())).willReturn(new ScoringResult(ID, outcome, null, null, false, "x"));
        mvc.perform(post("/api/jobs/42/score"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void notesAreSavedWith204() throws Exception {
        mvc.perform(put("/api/jobs/42/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Call recruiter\"}"))
                .andExpect(status().isNoContent());
        verify(tracking).updateNotes(ID, "hugh", "Call recruiter");
    }

    @Test
    void tooLongNotesAreRejected() throws Exception {
        String notes = "x".repeat(TrackingService.MAX_NOTES + 1);
        mvc.perform(put("/api/jobs/42/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"" + notes + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(tracking, never()).updateNotes(anyLong(), any(), any());
    }
}
