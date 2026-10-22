package io.github.quanghung304.jobhunter.llm;

/**
 * The model serving a purpose changed (ADR-0008): by the owner ({@code USER}) or by the router's
 * auto-fallback ({@code AUTO_FALLBACK}); notify (T-022) announces auto-fallback switches.
 */
public record LlmAssignmentSwitched(LlmPurpose purpose, String fromModelId, String toModelId,
        AssignmentChangeReason reason) {
}
