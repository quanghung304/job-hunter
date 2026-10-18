package io.github.quanghung304.jobhunter.enrich;

/**
 * Result of prefiltering the ACTIVE jobs in a pipeline run (no LLM).
 *
 * @param recorded rows created or changed (new jobs, or a different decision)
 */
public record PrefilterSummary(int evaluated, int recorded, int passed, int rejected) {
}
