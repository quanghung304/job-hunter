package io.github.quanghung304.archfixture.common;

import io.github.quanghung304.archfixture.ingest.IngestThing;

/** ArchitectureTest fixture: a forbidden common -> ingest dependency. */
public class LeakyHelper {

    public String describe(IngestThing thing) {
        return thing.name();
    }
}
