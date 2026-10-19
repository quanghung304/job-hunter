package io.github.quanghung304.jobhunter.source;

import java.time.Instant;

/** One company board ({@code company}); {@code lastCheckStatus} and {@code lastCheckedAt} may be null. */
public record CompanyView(long id, String name, AtsType ats, String slug, boolean enabled,
        CompanyCheckStatus lastCheckStatus, Instant lastCheckedAt) {
}
