package io.github.quanghung304.archfixture.pipeline;

import io.github.quanghung304.archfixture.enrich.ExtractionService;

/** ArchitectureTest fixture: a pipeline that extracts every job (forbidden by ADR-0010). */
public class EagerPipeline {

    public String run(ExtractionService extraction) {
        return extraction.extract(1L);
    }
}
