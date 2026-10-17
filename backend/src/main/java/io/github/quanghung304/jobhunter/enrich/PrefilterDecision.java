package io.github.quanghung304.jobhunter.enrich;

import java.util.List;

/** Prefilter outcome: REJECT if any rule failed; {@code reasons} lists every failed rule in rule order. */
public record PrefilterDecision(PrefilterResult result, List<PrefilterReason> reasons) {

    public PrefilterDecision {
        reasons = List.copyOf(reasons);
        if ((result == PrefilterResult.PASS) != reasons.isEmpty()) {
            throw new IllegalArgumentException("PASS has no reasons, REJECT has at least one: " + result + " " + reasons);
        }
    }

    public static PrefilterDecision of(List<PrefilterReason> reasons) {
        return new PrefilterDecision(reasons.isEmpty() ? PrefilterResult.PASS : PrefilterResult.REJECT, reasons);
    }

    public boolean passed() {
        return result == PrefilterResult.PASS;
    }

    /** Reason names as stored in {@code job_assessment.prefilter_reasons}. */
    public List<String> reasonCodes() {
        return reasons.stream().map(Enum::name).toList();
    }
}
