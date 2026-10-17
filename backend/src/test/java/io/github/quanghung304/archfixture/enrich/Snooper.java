package io.github.quanghung304.archfixture.enrich;

import io.github.quanghung304.archfixture.ingest.persistence.JobRow;

/** ArchitectureTest fixture: enrich may read ingest, but not through its persistence package. */
public class Snooper {

    public long peek(JobRow row) {
        return row.id();
    }
}
