package io.github.quanghung304.jobhunter.enrich;

/**
 * One eligibility criterion for a job and profile (scoring-spec §4, ADR-0011).
 *
 * @param reason a reason code such as {@code FULL_TIME_ONLY_SOFT}, or null
 * @param value  the short job-side text shown to the owner, e.g. "EU, IT" or "Not stated"
 */
public record EligibilityCheck(EligibilityRule code, boolean critical, CheckResult result, String reason,
        String value) {

    public static final String FULL_TIME_ONLY_SOFT = "FULL_TIME_ONLY_SOFT";

    static EligibilityCheck of(EligibilityRule rule, CheckResult result, String value) {
        return new EligibilityCheck(rule, rule.critical(), result, null, value);
    }

    EligibilityCheck withReason(String reasonCode) {
        return new EligibilityCheck(code, critical, result, reasonCode, value);
    }

    /** One prompt line (score-v2): {@code CODE: RESULT — value (reason)}. */
    public String promptLine() {
        return code + ": " + result + " — " + value + (reason == null ? "" : " (" + reason + ")");
    }
}
