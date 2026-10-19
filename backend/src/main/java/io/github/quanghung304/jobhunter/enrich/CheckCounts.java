package io.github.quanghung304.jobhunter.enrich;

import java.util.List;

/** How many checks of a job are OK, UNKNOWN and FAIL (board cards: "Check N criteria"). */
public record CheckCounts(int ok, int unknown, int fail) {

    public static CheckCounts of(List<EligibilityCheck> checks) {
        int ok = 0;
        int unknown = 0;
        int fail = 0;
        for (EligibilityCheck check : checks) {
            switch (check.result()) {
                case OK -> ok++;
                case UNKNOWN -> unknown++;
                case FAIL -> fail++;
            }
        }
        return new CheckCounts(ok, unknown, fail);
    }

    /** Checks the owner should look at (UNKNOWN or FAIL). */
    public int toReview() {
        return unknown + fail;
    }
}
