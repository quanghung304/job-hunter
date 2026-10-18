package io.github.quanghung304.jobhunter.enrich;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Read-only view of a {@code job_assessment} row for other modules (scoring, tracking).
 *
 * @param scoreDetails the full score object of scoring-spec §5 ({@code score_breakdown} column)
 */
public record AssessmentView(
        long jobId,
        String profileId,
        String profileHash,
        PrefilterResult prefilterResult,
        List<String> prefilterReasons,
        Eligibility eligibility,
        List<String> eligibilityReasons,
        Long extractionId,
        Integer score,
        JsonNode scoreDetails,
        String summary,
        String scorePromptVersion,
        String scoreModel,
        AssessmentStatus status,
        int attempts) {

    public AssessmentView {
        prefilterReasons = List.copyOf(prefilterReasons);
        eligibilityReasons = List.copyOf(eligibilityReasons);
    }

    public boolean scored() {
        return score != null;
    }
}
