package io.github.quanghung304.jobhunter.source;

/**
 * Fetches postings from one source (docs/sources/&lt;code&gt;.md). Implementations are Spring
 * beans; {@link CollectorRegistry} exposes the enabled ones.
 */
public interface JobCollector {

    /** Source code as in docs/sources/catalog.md, e.g. {@code arbeitnow}. */
    String sourceCode();

    /** Never throws for source errors (HTTP, parsing); reports them in the result. */
    CollectResult collect(CollectRequest request);
}
