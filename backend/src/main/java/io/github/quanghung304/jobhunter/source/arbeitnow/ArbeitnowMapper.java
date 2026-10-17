package io.github.quanghung304.jobhunter.source.arbeitnow;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.quanghung304.jobhunter.common.text.HtmlText;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import io.github.quanghung304.jobhunter.source.RemoteHint;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Pure mapping of an Arbeitnow item to {@link NormalizedPosting} (docs/sources/arbeitnow.md). */
public final class ArbeitnowMapper {

    public static final String SOURCE_CODE = "arbeitnow";

    private static final Pattern REMOTE_WORD = Pattern.compile("\\bremote\\b", Pattern.CASE_INSENSITIVE);

    private ArbeitnowMapper() {
    }

    /** @throws IllegalArgumentException if the item has no {@code slug} or {@code url} */
    public static NormalizedPosting toPosting(ArbeitnowJobDto item, JsonNode raw) {
        String slug = required(item.slug(), "slug");
        String url = required(item.url(), "url");
        return new NormalizedPosting(
                SOURCE_CODE,
                slug,
                url,
                strip(item.title()),
                strip(item.companyName()),
                blankToNull(item.location()),
                isRemote(item) ? RemoteHint.REMOTE : RemoteHint.ONSITE,
                employmentHint(item.jobTypes()),
                HtmlText.toPlain(item.description()),
                url,
                null, null, null, null,
                tags(item.tags()),
                item.createdAt() == null ? null : Instant.ofEpochSecond(item.createdAt()),
                raw);
    }

    /**
     * {@code remote == true}, or the location names "remote" (the flag is sometimes false for
     * {@code location: "Remote"}). Owner's rule: a false positive beats a missed job.
     */
    public static boolean isRemote(ArbeitnowJobDto item) {
        return Boolean.TRUE.equals(item.remote())
                || item.location() != null && REMOTE_WORD.matcher(item.location()).find();
    }

    private static String employmentHint(List<String> jobTypes) {
        if (jobTypes == null) {
            return null;
        }
        String joined = String.join(", ", jobTypes.stream().filter(Objects::nonNull).map(String::strip)
                .filter(t -> !t.isEmpty()).toList());
        return joined.isEmpty() ? null : joined;
    }

    private static List<String> tags(List<String> tags) {
        return tags == null ? List.of() : tags.stream()
                .filter(Objects::nonNull)
                .map(t -> t.strip().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value.strip();
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
