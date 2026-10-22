package io.github.quanghung304.jobhunter.source.hn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.quanghung304.jobhunter.common.text.HtmlText;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Pure mapping of a top-level "Who is hiring?" comment to {@link NormalizedPosting} (docs/sources/hn-whoishiring.md). */
public final class HnMapper {

    public static final String SOURCE_CODE = "hn-whoishiring";
    public static final String PERMALINK = "https://news.ycombinator.com/item?id=";

    private static final Pattern PLAIN_URL = Pattern.compile("(?i)\\bhttps?://[^\\s<>\"')\\]]+");
    private static final String HN_HOST = "news.ycombinator.com";

    private HnMapper() {
    }

    /**
     * Whether the child is a job post candidate: a comment with an author and text. Deleted
     * comments are omitted by the API; this also covers an empty or authorless child.
     */
    public static boolean isPost(HnCommentDto comment) {
        return (comment.type() == null || comment.type().equals("comment"))
                && comment.author() != null && !comment.author().isBlank()
                && comment.text() != null && !comment.text().isBlank();
    }

    /**
     * Maps a comment for which {@link #isPost} holds. {@code rawPayload} is the comment without
     * its nested replies.
     *
     * @throws IllegalArgumentException if the comment has no id or an unreadable {@code created_at}
     */
    public static NormalizedPosting toPosting(HnCommentDto comment, JsonNode raw) {
        if (comment.id() == null) {
            throw new IllegalArgumentException("missing id");
        }
        String permalink = PERMALINK + comment.id();
        HnHeader header = HnHeaderParser.fromComment(comment.text());
        String company = header.companyName().isBlank() ? "Unknown (HN " + comment.id() + ")" : header.companyName();
        return new NormalizedPosting(
                SOURCE_CODE,
                String.valueOf(comment.id()),
                permalink,
                header.title(),
                company,
                header.locationText(),
                header.remoteHint(),
                header.employmentHint(),
                HtmlText.toPlain(comment.text()),
                applyUrl(comment.text()).orElse(permalink),
                null,
                null,
                null,
                null,
                List.of(),
                postedAt(comment),
                withoutReplies(raw));
    }

    /** The first http(s) link (an anchor, else a bare URL in the text) that is not on news.ycombinator.com. */
    static Optional<String> applyUrl(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        for (Element anchor : Jsoup.parseBodyFragment(html).select("a[href]")) {
            String href = anchor.attr("href").strip();
            if (isExternal(href)) {
                return Optional.of(href);
            }
        }
        Matcher url = PLAIN_URL.matcher(HtmlText.toPlain(html));
        while (url.find()) {
            String candidate = url.group().replaceAll("[.,;:!?]+$", "");
            if (isExternal(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** {@code created_at} (ISO-8601 UTC), else {@code created_at_i} (epoch seconds). */
    static Instant postedAt(HnCommentDto comment) {
        if (comment.createdAt() != null && !comment.createdAt().isBlank()) {
            try {
                return Instant.parse(comment.createdAt().strip());
            } catch (DateTimeParseException e) {
                if (comment.createdAtEpoch() == null) {
                    throw new IllegalArgumentException("unreadable created_at '" + comment.createdAt().strip() + "'");
                }
            }
        }
        return comment.createdAtEpoch() == null ? null : Instant.ofEpochSecond(comment.createdAtEpoch());
    }

    private static boolean isExternal(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        try {
            String host = URI.create(url).getHost();
            return host != null && !host.equalsIgnoreCase(HN_HOST);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static JsonNode withoutReplies(JsonNode raw) {
        if (raw instanceof ObjectNode object && object.has("children")) {
            ObjectNode copy = object.deepCopy();
            copy.remove("children");
            return copy;
        }
        return raw;
    }
}
