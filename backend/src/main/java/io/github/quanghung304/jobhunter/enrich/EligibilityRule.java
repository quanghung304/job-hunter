package io.github.quanghung304.jobhunter.enrich;

/** Rules of scoring-spec.md §4; {@code critical} rules turn UNKNOWN into UNCERTAIN. */
public enum EligibilityRule {
    WORK_MODE(true),
    REGION(true),
    WORK_AUTH(true),
    TIMEZONE(false),
    EMPLOYMENT_TYPE(true),
    HOURS(false),
    SENIORITY(false),
    LANGUAGE(true),
    SCAM(true);

    private final boolean critical;

    EligibilityRule(boolean critical) {
        this.critical = critical;
    }

    public boolean critical() {
        return critical;
    }

    /** Outcome of one rule. */
    public enum Verdict {
        OK,
        UNKNOWN,
        FAIL
    }
}
