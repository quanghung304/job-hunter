package io.github.quanghung304.archfixture.llm.client;

/** ArchitectureTest fixture: stands in for the real provider client interface. */
public interface LlmClient {

    String send(String prompt);
}
