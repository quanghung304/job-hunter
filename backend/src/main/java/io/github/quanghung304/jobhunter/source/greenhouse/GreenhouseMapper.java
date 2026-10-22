package io.github.quanghung304.jobhunter.source.greenhouse;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.text.HtmlText;
import io.github.quanghung304.jobhunter.source.CompanyBoard;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jsoup.parser.Parser;

/** Pure mapping of a Greenhouse board job to {@link NormalizedPosting} (docs/sources/ats.md). */
public final class GreenhouseMapper {

    public static final String SOURCE_CODE = "greenhouse";

    private static final Pattern REMOTE = Pattern.compile("(?i)\\b(?:remote|anywhere|distributed)\\b");

    private GreenhouseMapper() {
    }

    /** @throws IllegalArgumentException if the job has no id or url, or an unreadable date */
    public static NormalizedPosting toPosting(CompanyBoard board, GreenhouseJobDto job, JsonNode raw) {
        if (job.id() == null) {
            throw new IllegalArgumentException("missing id");
        }
        if (job.absoluteUrl() == null || job.absoluteUrl().isBlank()) {
            throw new IllegalArgumentException("missing absolute_url");
        }
        String url = job.absoluteUrl().strip();
        String location = job.location() == null ? null : blankToNull(job.location().name());
        return new NormalizedPosting(
                SOURCE_CODE,
                board.slug() + ":" + job.id(),
                url,
                job.title() == null ? null : job.title().strip(),
                board.name(),
                location,
                location != null && REMOTE.matcher(location).find() ? RemoteHint.REMOTE : RemoteHint.UNKNOWN,
                null,
                description(job.content()),
                url,
                null,
                null,
                null,
                null,
                tags(job.departments()),
                postedAt(job),
                raw);
    }

    /** The content is escaped HTML: unescape once, then HTML to text. */
    static String description(String content) {
        return content == null ? "" : HtmlText.toPlain(Parser.unescapeEntities(content, false));
    }

    /** {@code first_published}, else {@code updated_at}. */
    static Instant postedAt(GreenhouseJobDto job) {
        String value = blankToNull(job.firstPublished()) != null ? job.firstPublished() : job.updatedAt();
        if (blankToNull(value) == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.strip()).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("unreadable date '" + value.strip() + "'");
        }
    }

    private static List<String> tags(List<GreenhouseJobDto.Named> departments) {
        if (departments == null) {
            return List.of();
        }
        return departments.stream().filter(Objects::nonNull).map(GreenhouseJobDto.Named::name)
                .map(GreenhouseMapper::blankToNull).filter(Objects::nonNull)
                .map(n -> n.toLowerCase(Locale.ROOT)).distinct().toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
