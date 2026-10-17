package io.github.quanghung304.jobhunter.source.weworkremotely;

import io.github.quanghung304.jobhunter.common.text.HtmlText;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import io.github.quanghung304.jobhunter.source.RemoteHint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure mapping of a WWR item to {@link NormalizedPosting} (docs/sources/weworkremotely.md). */
public final class WeWorkRemotelyMapper {

    public static final String SOURCE_CODE = "weworkremotely";
    static final String UNKNOWN_COMPANY = "Unknown";

    /** "Company: Title", tolerating a space before the colon ("Fivetran : Global …"). */
    private static final Pattern COMPANY_TITLE = Pattern.compile("^(.+?)\\s*:\\s+(.+)$", Pattern.DOTALL);
    /** Flag emoji are pairs of regional indicator symbols ("🇺🇸 United States of America"). */
    private static final Pattern FLAG = Pattern.compile("[\\x{1F1E6}-\\x{1F1FF}]+");

    private WeWorkRemotelyMapper() {
    }

    /**
     * The posting plus an optional warning (title without "Company: " → company "Unknown").
     *
     * @param warning null when the item mapped cleanly
     */
    public record Mapped(NormalizedPosting posting, String warning) {
    }

    /** @throws IllegalArgumentException if the item has neither {@code guid} nor {@code link}, or no title */
    public static Mapped toPosting(WeWorkRemotelyItemDto item) {
        String link = blankToNull(item.link());
        String guid = blankToNull(item.guid());
        if (link == null && guid == null) {
            throw new IllegalArgumentException("missing guid and link");
        }
        String fullTitle = blankToNull(item.title());
        if (fullTitle == null) {
            throw new IllegalArgumentException("missing title");
        }
        String company;
        String title;
        String warning = null;
        Matcher m = COMPANY_TITLE.matcher(fullTitle);
        if (m.matches()) {
            company = m.group(1).strip();
            title = m.group(2).strip();
        } else {
            company = UNKNOWN_COMPANY;
            title = fullTitle;
            warning = "title without \"Company: \": " + fullTitle;
        }
        String url = link != null ? link : guid;
        var posting = new NormalizedPosting(
                SOURCE_CODE,
                guid != null ? guid : link,
                url,
                title,
                company,
                blankToNull(item.region()),
                RemoteHint.REMOTE,
                blankToNull(item.type()),
                description(item),
                url, // terms: direct link to the original listing
                null, null, null, null,
                item.categories().stream().filter(Objects::nonNull).map(c -> c.strip().toLowerCase(Locale.ROOT))
                        .filter(c -> !c.isEmpty()).distinct().toList(),
                item.pubDate(),
                item.raw());
        return new Mapped(posting, warning);
    }

    /** Description text plus WWR's extra fields as plain lines, so extraction sees them. */
    static String description(WeWorkRemotelyItemDto item) {
        String text = HtmlText.toPlain(item.descriptionHtml());
        var extras = new ArrayList<String>();
        addLine(extras, "Country", stripFlags(item.country()));
        addLine(extras, "State", item.state());
        addLine(extras, "Skills", item.skills());
        if (extras.isEmpty()) {
            return text;
        }
        return (text.isEmpty() ? "" : text + "\n\n") + String.join("\n", extras);
    }

    static String stripFlags(String value) {
        return value == null ? null : blankToNull(FLAG.matcher(value).replaceAll(""));
    }

    private static void addLine(List<String> lines, String label, String value) {
        String v = blankToNull(value);
        if (v != null) {
            lines.add(label + ": " + v);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
