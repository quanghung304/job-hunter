package io.github.quanghung304.jobhunter.profile;

/** The single candidate profile of Phase 1, loaded once at startup. */
public interface ProfileProvider {

    Profile current();

    /** Lower-case hex sha256 of the profile file bytes; stored as {@code profile_hash}. */
    String profileHash();
}
