package io.github.quanghung304.jobhunter.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.quanghung304.jobhunter.llm.Assignment;
import io.github.quanghung304.jobhunter.llm.AssignmentChangeReason;
import io.github.quanghung304.jobhunter.llm.LlmAdminService;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmUsageService.ModelUsage;
import io.github.quanghung304.jobhunter.llm.ModelChoice;
import io.github.quanghung304.jobhunter.llm.ModelStatus;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import io.github.quanghung304.jobhunter.llm.ModelUnusableException;
import io.github.quanghung304.jobhunter.llm.PaidCallsDisabledException;
import io.github.quanghung304.jobhunter.llm.PaidConfirmationRequiredException;
import io.github.quanghung304.jobhunter.llm.UnknownModelException;
import io.github.quanghung304.jobhunter.llm.quota.ModelState;
import io.github.quanghung304.jobhunter.llm.registry.LlmRegistryFile;
import io.github.quanghung304.jobhunter.llm.registry.ProviderType;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LlmAdminController.class)
class LlmAdminControllerTest {

    static final Instant AT = Instant.parse("2026-10-22T08:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LlmAdminService llm;

    static RegisteredModel model(String id, String label, ModelTier tier, boolean missingKey) {
        return new RegisteredModel(id, label, "groq", ProviderType.OPENAI_COMPATIBLE, "https://api.groq.example",
                "openai/" + id, tier, new LlmRegistryFile.Limits(30, 1000, null, null, null), null, true,
                missingKey ? "" : "key", missingKey, null, 0);
    }

    static LlmAdminService.AssignmentView view(String modelId, String by) {
        return new LlmAdminService.AssignmentView(new Assignment(LlmPurpose.EXTRACT, modelId, List.of("backup"),
                false, false, by), AT);
    }

    @Test
    void overviewListsAssignmentsModelsHistoryAndConsent() throws Exception {
        var groq = model("groq-gpt-oss-120b", "GPT-OSS 120B (Groq)", ModelTier.FREE, false);
        given(llm.overview()).willReturn(new LlmAdminService.Overview(
                List.of(view("groq-gpt-oss-120b", "config")),
                List.of(new LlmAdminService.ModelView(groq, new ModelState(groq.id(), ModelStatus.NEAR_LIMIT, null,
                        "Daily request limit 812/1000 (81%)"), new ModelUsage(812, 2_400_000, 310_000, null),
                        List.of(LlmPurpose.EXTRACT))),
                List.of(new LlmAdminService.HistoryEntry(LlmPurpose.SCORE, "a", "b", AssignmentChangeReason.USER, AT)),
                false));

        mvc.perform(get("/api/admin/llm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].purpose").value("EXTRACT"))
                .andExpect(jsonPath("$.assignments[0].fallbacks[0]").value("backup"))
                .andExpect(jsonPath("$.assignments[0].updatedBy").value("config"))
                .andExpect(jsonPath("$.models[0].modelId").value("groq-gpt-oss-120b"))
                .andExpect(jsonPath("$.models[0].provider").value("groq"))
                .andExpect(jsonPath("$.models[0].providerModel").value("openai/groq-gpt-oss-120b"))
                .andExpect(jsonPath("$.models[0].status").value("NEAR_LIMIT"))
                .andExpect(jsonPath("$.models[0].limits.rpd").value(1000))
                .andExpect(jsonPath("$.models[0].usageToday.calls").value(812))
                .andExpect(jsonPath("$.models[0].usageToday.costUsd").isEmpty())
                .andExpect(jsonPath("$.models[0].keyConfigured").value(true))
                .andExpect(jsonPath("$.models[0].assignedTo[0]").value("EXTRACT"))
                .andExpect(jsonPath("$.models[0].lastEval").isEmpty())
                .andExpect(jsonPath("$.history[0].reason").value("USER"))
                .andExpect(jsonPath("$.paidCallsAllowed").value(false));
    }

    @Test
    void putSwitchesTheModel() throws Exception {
        given(llm.switchModel(eq(LlmPurpose.EXTRACT), eq("backup"), isNull(), isNull(), isNull(), eq(false)))
                .willReturn(new LlmAdminService.SwitchResult(view("backup", "user"), null));

        mvc.perform(put("/api/admin/llm/assignments/EXTRACT").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"backup\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignment.modelId").value("backup"))
                .andExpect(jsonPath("$.assignment.updatedBy").value("user"))
                .andExpect(jsonPath("$.warning").isEmpty());
    }

    @Test
    void putToAPaidModelWarnsAndPassesTheConfirmation() throws Exception {
        given(llm.switchModel(eq(LlmPurpose.SCORE), eq("claude-haiku"), eq(List.of("backup")), eq(true), eq(true),
                eq(true))).willReturn(new LlmAdminService.SwitchResult(view("claude-haiku", "user"),
                        LlmAdminService.PAID_WARNING));

        mvc.perform(put("/api/admin/llm/assignments/SCORE").contentType(MediaType.APPLICATION_JSON).content("""
                        {"modelId":"claude-haiku","fallbacks":["backup"],"autoFallback":true,"allowPaid":true,
                         "confirmPaid":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").value("PAID model: counts against the monthly budget"));
    }

    @Test
    void putErrors() throws Exception {
        given(llm.switchModel(any(), eq("nope"), any(), any(), any(), anyBoolean()))
                .willThrow(new UnknownModelException("nope", false));
        given(llm.switchModel(any(), eq("broken"), any(), any(), any(), anyBoolean()))
                .willThrow(new ModelUnusableException("Broken", "missing key"));
        var haiku = new ModelChoice("claude-haiku", "Claude Haiku 5.5", ModelTier.PAID, true);
        given(llm.switchModel(any(), eq("claude-haiku"), any(), any(), any(), eq(false)))
                .willThrow(new PaidConfirmationRequiredException(haiku, new BigDecimal("0.0012")));
        given(llm.switchModel(any(), eq("paid-off"), any(), any(), any(), anyBoolean()))
                .willThrow(new PaidCallsDisabledException(new ModelChoice("paid-off", "Paid", ModelTier.PAID, false)));

        mvc.perform(put("/api/admin/llm/assignments/EXTRACT").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_MODEL"));
        mvc.perform(put("/api/admin/llm/assignments/EXTRACT").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"broken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_UNUSABLE"));
        mvc.perform(put("/api/admin/llm/assignments/EXTRACT").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"claude-haiku\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAID_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.modelLabel").value("Claude Haiku 5.5"))
                .andExpect(jsonPath("$.tier").value("PAID"))
                .andExpect(jsonPath("$.estimatedCostPerCallUsd").value(0.0012));
        mvc.perform(put("/api/admin/llm/assignments/EXTRACT").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"paid-off\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAID_CALLS_DISABLED"));
        mvc.perform(put("/api/admin/llm/assignments/EXTRACT").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(put("/api/admin/llm/assignments/MAGIC").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"backup\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCallsTheModelOnce() throws Exception {
        given(llm.test("backup", false)).willReturn(new LlmAdminService.TestResult(true, 412, ModelStatus.AVAILABLE,
                null));
        given(llm.test("nope", false)).willThrow(new UnknownModelException("nope", true));

        mvc.perform(post("/api/admin/llm/models/backup/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.latencyMs").value(412))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mvc.perform(post("/api/admin/llm/models/nope/test"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNKNOWN_MODEL"));
    }

    @Test
    void testOfAPaidModelPassesTheConfirmation() throws Exception {
        given(llm.test("claude-haiku", true)).willReturn(new LlmAdminService.TestResult(false, 30, ModelStatus.ERROR,
                "AUTH: invalid key"));

        mvc.perform(post("/api/admin/llm/models/claude-haiku/test").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmPaid\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.detail").value("AUTH: invalid key"));
        verify(llm).test("claude-haiku", true);
    }
}
