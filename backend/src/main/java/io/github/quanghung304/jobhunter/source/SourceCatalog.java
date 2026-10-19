package io.github.quanghung304.jobhunter.source;

import java.util.Map;

/** Display names of the sources in docs/sources/catalog.md ("via Himalayas" links in the UI). */
public final class SourceCatalog {

    private static final Map<String, String> NAMES = Map.of(
            "arbeitnow", "Arbeitnow",
            "himalayas", "Himalayas",
            "jobicy", "Jobicy",
            "weworkremotely", "We Work Remotely",
            "hn-whoishiring", "Hacker News: Who is hiring?",
            "greenhouse", "Greenhouse",
            "lever", "Lever",
            "ashby", "Ashby");

    private SourceCatalog() {
    }

    /** The source's display name; the code itself for a source not in the catalog. */
    public static String name(String sourceCode) {
        return NAMES.getOrDefault(sourceCode, sourceCode);
    }
}
