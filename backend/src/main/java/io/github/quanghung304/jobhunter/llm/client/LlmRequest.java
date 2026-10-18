package io.github.quanghung304.jobhunter.llm.client;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * One HTTP request to a provider: the resolved model, the rendered prompt as system text plus
 * turns (user, or user/assistant/user for the corrective retry) and limits.
 *
 * @param temperature null = provider default (some models reject other values)
 */
public record LlmRequest(
        RegisteredModel model,
        LlmPurpose purpose,
        Long jobId,
        String promptId,
        String system,
        List<ChatMessage> messages,
        int maxOutputTokens,
        BigDecimal temperature,
        Duration timeout) {

    public LlmRequest {
        messages = List.copyOf(messages);
    }
}
