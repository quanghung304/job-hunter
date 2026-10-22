package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.api.LlmAdminResponses.AdminResponse;
import io.github.quanghung304.jobhunter.api.LlmAdminResponses.AssignmentRequest;
import io.github.quanghung304.jobhunter.api.LlmAdminResponses.AssignmentResponse;
import io.github.quanghung304.jobhunter.api.LlmAdminResponses.AssignmentSaved;
import io.github.quanghung304.jobhunter.api.LlmAdminResponses.TestRequest;
import io.github.quanghung304.jobhunter.api.LlmAdminResponses.TestResponse;
import io.github.quanghung304.jobhunter.llm.LlmAdminService;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM models of the Admin page (ADR-0008): assignments, model status and usage, switching per
 * purpose and the "Test" call. A switch makes no LLM call (ADR-0010); PAID needs consent (ADR-0009).
 */
@RestController
@RequestMapping(path = "/api/admin/llm", produces = MediaType.APPLICATION_JSON_VALUE)
public class LlmAdminController {

    private final LlmAdminService llm;

    public LlmAdminController(LlmAdminService llm) {
        this.llm = llm;
    }

    @GetMapping
    @Operation(summary = "Assignments, models (status, limits, usage today) and switch history")
    public AdminResponse overview() {
        return AdminResponse.of(llm.overview());
    }

    @PutMapping(path = "/assignments/{purpose}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Switch the model of EXTRACT or SCORE; no LLM call is made, the purpose is un-paused")
    @ApiResponse(responseCode = "200", description = "Saved")
    @ApiResponse(responseCode = "400", description = "UNKNOWN_MODEL or VALIDATION_FAILED",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @ApiResponse(responseCode = "409",
            description = "MODEL_UNUSABLE, PAID_CALLS_DISABLED or PAID_CONFIRMATION_REQUIRED (with estimatedCostPerCallUsd)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    public AssignmentSaved switchModel(@PathVariable LlmPurpose purpose, @Valid @RequestBody AssignmentRequest request) {
        LlmAdminService.SwitchResult result = llm.switchModel(purpose, request.modelId(), request.fallbacks(),
                request.autoFallback(), request.allowPaid(), Boolean.TRUE.equals(request.confirmPaid()));
        return new AssignmentSaved(AssignmentResponse.of(result.assignment()), result.warning());
    }

    @PostMapping("/models/{modelId}/test")
    @Operation(summary = "One tiny call to the model (purpose TEST); a success clears ERROR and expired statuses")
    @ApiResponse(responseCode = "200", description = "Tested (ok false when the call failed)")
    @ApiResponse(responseCode = "404", description = "UNKNOWN_MODEL",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @ApiResponse(responseCode = "409", description = "PAID_CALLS_DISABLED or PAID_CONFIRMATION_REQUIRED",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    public TestResponse test(@PathVariable String modelId, @RequestBody(required = false) TestRequest request) {
        boolean confirmPaid = request != null && Boolean.TRUE.equals(request.confirmPaid());
        LlmAdminService.TestResult result = llm.test(modelId, confirmPaid);
        return new TestResponse(result.ok(), result.latencyMs(), result.status(), result.detail());
    }
}
