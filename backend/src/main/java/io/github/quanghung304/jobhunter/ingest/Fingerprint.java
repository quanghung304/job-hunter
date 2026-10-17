package io.github.quanghung304.jobhunter.ingest;

import io.github.quanghung304.jobhunter.common.Hashing;
import io.github.quanghung304.jobhunter.common.text.TextNormalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Job identity across sources: sha256(company_key + "|" + title_key) (data-model.md §4). */
public final class Fingerprint {

    private static final DateTimeFormatter REPOST_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private Fingerprint() {
    }

    public static String of(String companyKey, String titleKey) {
        return Hashing.sha256Hex(companyKey + "|" + titleKey);
    }

    public static String ofPosting(String companyName, String title) {
        return of(TextNormalizer.companyKey(companyName), TextNormalizer.titleKey(title));
    }

    /** Fingerprint of a re-posting: {@code <fingerprint>#yyyyMMdd} of the new job's first_seen (UTC). */
    public static String repost(String fingerprint, Instant firstSeen) {
        return fingerprint + "#" + REPOST_SUFFIX.format(firstSeen);
    }
}
