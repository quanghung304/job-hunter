package io.github.quanghung304.archfixture.scoring;

import io.github.quanghung304.archfixture.llm.client.LlmClient;

/** ArchitectureTest fixture: calls a provider client directly instead of through LlmRouter. */
public class DirectCaller {

    public String bypass(LlmClient client) {
        return client.send("score this");
    }
}
