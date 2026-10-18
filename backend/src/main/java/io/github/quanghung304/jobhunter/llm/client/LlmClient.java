package io.github.quanghung304.jobhunter.llm.client;

import io.github.quanghung304.jobhunter.llm.registry.ProviderType;

/**
 * One provider API. Sends exactly one HTTP request per call: no retries, no parsing of the
 * answer, no logging — the router does those, so every attempt is logged once.
 */
public interface LlmClient {

    ProviderType type();

    /** @throws LlmCallException for every non-2xx answer, timeout or network error */
    LlmResponse send(LlmRequest request);
}
