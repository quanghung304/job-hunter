package io.github.quanghung304.jobhunter.enrich;

/**
 * Criteria of scoring-spec.md §4, in table order. {@code critical} orders and highlights checks
 * in the UI and is used by the eval-only rollup (§7); the app itself has no verdict (ADR-0011).
 */
public enum EligibilityRule {
    WORK_MODE(true),
    REGION(true),
    WORK_AUTH(true),
    TIMEZONE(false),
    EMPLOYMENT_TYPE(true),
    HOURS(false),
    SENIORITY(false),
    LANGUAGE(true),
    SCAM(true),
    EXTRACTION_CONFIDENCE(true);

    private final boolean critical;

    EligibilityRule(boolean critical) {
        this.critical = critical;
    }

    public boolean critical() {
        return critical;
    }
}
