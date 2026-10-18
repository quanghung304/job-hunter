package io.github.quanghung304.archfixture.enrich;

/** ArchitectureTest fixture: stands in for the service that calls the LLM. */
public class ExtractionService {

    public String extract(long jobId) {
        return "attributes of " + jobId;
    }
}
