package io.github.quanghung304.jobhunter.profile;

import java.time.Instant;

/**
 * The loaded profile with the sha256 hex of its file bytes (stored as {@code profile_hash}) and
 * the file's last-modified time. Replaced as a whole when the profile is saved (ADR-0012).
 */
public record ProfileSnapshot(Profile profile, String hash, Instant savedAt) {
}
