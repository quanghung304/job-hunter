package io.github.quanghung304.jobhunter.profile;

/**
 * The single candidate profile of Phase 1: loaded at startup, replaced when the owner saves it
 * from the Profile page (ADR-0012). Read {@link #snapshot()} once when profile and hash must
 * belong together.
 */
public interface ProfileProvider {

    ProfileSnapshot snapshot();

    default Profile current() {
        return snapshot().profile();
    }

    /** Lower-case hex sha256 of the profile file bytes; stored as {@code profile_hash}. */
    default String profileHash() {
        return snapshot().hash();
    }
}
