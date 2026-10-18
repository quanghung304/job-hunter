package io.github.quanghung304.jobhunter.enrich;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Eligibility of one job for the profile with the verdict of every rule. {@link #reasons()}
 * is what {@code job_assessment.eligibility_reasons} stores: {@code RULE:UNKNOWN} /
 * {@code RULE:FAIL} for non-OK rules, plus {@code FULL_TIME_ONLY_SOFT} and {@code CONFIDENCE:LOW}.
 */
public record EligibilityResult(
        Eligibility eligibility,
        Map<EligibilityRule, EligibilityRule.Verdict> verdicts,
        boolean fullTimeOnlySoft,
        boolean lowConfidence) {

    public static final String FULL_TIME_ONLY_SOFT = "FULL_TIME_ONLY_SOFT";
    public static final String LOW_CONFIDENCE = "CONFIDENCE:LOW";

    public EligibilityResult {
        verdicts = Map.copyOf(verdicts);
    }

    public EligibilityRule.Verdict verdict(EligibilityRule rule) {
        return verdicts.get(rule);
    }

    /** Non-OK rules in rule order, then the soft markers. */
    public List<String> reasons() {
        List<String> reasons = new ArrayList<>();
        for (EligibilityRule rule : EligibilityRule.values()) {
            EligibilityRule.Verdict verdict = verdicts.get(rule);
            if (verdict != null && verdict != EligibilityRule.Verdict.OK) {
                reasons.add(rule + ":" + verdict);
            }
        }
        if (fullTimeOnlySoft) {
            reasons.add(FULL_TIME_ONLY_SOFT);
        }
        if (lowConfidence) {
            reasons.add(LOW_CONFIDENCE);
        }
        return List.copyOf(reasons);
    }
}
