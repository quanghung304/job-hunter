package io.github.quanghung304.jobhunter.scoring;

import io.github.quanghung304.jobhunter.llm.LlmUnavailable;

/**
 * Result of {@link ScoringService#computeScore}: one SCORE call, no DB writes.
 *
 * @param result      set when {@code status == OK}; its score equals the breakdown sum
 * @param unavailable set when {@code status == UNAVAILABLE}
 */
public record ScoreComputation(Status status, ScoreResult result, String modelId, LlmUnavailable.Reason unavailable,
        String message) {

    public enum Status {
        OK,
        FAILED,
        UNAVAILABLE
    }
}
