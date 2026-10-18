package io.github.quanghung304.jobhunter.llm.prompt;

import java.math.BigDecimal;

/** A prompt with all placeholders filled; {@code promptId} is stored as {@code prompt_version}. */
public record RenderedPrompt(String promptId, String system, String user, int maxOutputTokens, BigDecimal temperature) {
}
