package io.github.quanghung304.jobhunter.tracking;

/**
 * Score bands of api-spec ({@code scoreBand}); the one place they are computed, also used by the
 * Telegram digest.
 */
public enum ScoreBand {
    EXCELLENT,
    GOOD,
    MAYBE,
    WEAK,
    /** Not scored. */
    NONE;

    /** EXCELLENT ≥ 85, GOOD 70–84, MAYBE 50–69, WEAK &lt; 50, NONE for null. */
    public static ScoreBand of(Integer score) {
        if (score == null) {
            return NONE;
        }
        if (score >= 85) {
            return EXCELLENT;
        }
        if (score >= 70) {
            return GOOD;
        }
        return score >= 50 ? MAYBE : WEAK;
    }
}
