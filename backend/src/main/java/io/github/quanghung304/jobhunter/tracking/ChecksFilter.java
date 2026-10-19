package io.github.quanghung304.jobhunter.tracking;

/** Board filter on the eligibility check results (api-spec {@code checks}); values are OR-ed. */
public enum ChecksFilter {
    /** Analyzed and every check OK. */
    ALL_OK,
    /** At least one UNKNOWN check. */
    HAS_UNKNOWN,
    /** At least one FAIL check. */
    HAS_FAIL,
    /** Not analyzed for this profile (no checks). */
    NOT_CHECKED
}
