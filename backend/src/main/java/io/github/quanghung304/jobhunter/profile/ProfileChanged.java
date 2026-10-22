package io.github.quanghung304.jobhunter.profile;

import java.util.Optional;

/**
 * Published after the owner saved a new profile (ADR-0012). Listeners run synchronously in the
 * saving thread; the one that re-checks the jobs ({@code enrich}) reports its counts with
 * {@link #reassessed(Reassessment)}, so the save can answer with them.
 */
public final class ProfileChanged {

    private final String oldHash;
    private final String newHash;
    private final Profile profile;
    private Reassessment reassessment;

    public ProfileChanged(String oldHash, String newHash, Profile profile) {
        this.oldHash = oldHash;
        this.newHash = newHash;
        this.profile = profile;
    }

    public String oldHash() {
        return oldHash;
    }

    public String newHash() {
        return newHash;
    }

    public Profile profile() {
        return profile;
    }

    public void reassessed(Reassessment counts) {
        this.reassessment = counts;
    }

    public Optional<Reassessment> reassessment() {
        return Optional.ofNullable(reassessment);
    }

    /**
     * Jobs re-checked after a save (no LLM): rows re-evaluated, rows whose prefilter result changed,
     * rows whose eligibility checks were evaluated again from the stored extraction.
     */
    public record Reassessment(int jobs, int prefilterChanged, int checksUpdated) {

        public static final Reassessment NONE = new Reassessment(0, 0, 0);
    }
}
