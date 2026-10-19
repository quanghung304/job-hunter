package io.github.quanghung304.jobhunter.enrich;

/** Result of one eligibility check (scoring-spec §4); the UI shows UNKNOWN as "Unclear". */
public enum CheckResult {
    OK,
    UNKNOWN,
    FAIL
}
