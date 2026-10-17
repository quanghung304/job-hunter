package io.github.quanghung304.jobhunter.source.jobicy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.common.text.HtmlText;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Pure mapping of a Jobicy job to {@link NormalizedPosting} (docs/sources/jobicy.md). */
public final class JobicyMapper {

    public static final String SOURCE_CODE = "jobicy";

    private static final Map<String, SalaryPeriod> PERIODS = Map.of(
            "yearly", SalaryPeriod.YEAR, "annual", SalaryPeriod.YEAR,
            "monthly", SalaryPeriod.MONTH, "hourly", SalaryPeriod.HOUR);
    private static final DateTimeFormatter LEGACY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JobicyMapper() {
    }

    /** @throws IllegalArgumentException if the job has no {@code id} or {@code url}, or an unreadable {@code pubDate} */
    public static NormalizedPosting toPosting(JobicyJobDto job, JsonNode raw) {
        if (job.id() == null) {
            throw new IllegalArgumentException("missing id");
        }
        if (job.url() == null || job.url().isBlank()) {
            throw new IllegalArgumentException("missing url");
        }
        String url = job.url().strip();
        boolean hasAmount = job.salaryMin() != null || job.salaryMax() != null;
        SalaryPeriod period = job.salaryPeriod() == null ? SalaryPeriod.YEAR
                : PERIODS.get(job.salaryPeriod().strip().toLowerCase(Locale.ROOT));
        boolean structured = hasAmount && period != null;

        return new NormalizedPosting(
                SOURCE_CODE,
                String.valueOf(job.id()),
                url,
                strip(job.jobTitle()),
                strip(job.companyName()),
                location(job.jobGeo()),
                RemoteHint.REMOTE,
                join(job.jobType()),
                description(job, hasAmount && !structured),
                url, // terms: preserve the canonical Jobicy job URL (public API has no direct apply link)
                structured ? job.salaryMin() : null,
                structured ? job.salaryMax() : null,
                structured ? blankToNull(job.salaryCurrency()) : null,
                structured ? period : null,
                tags(job),
                parsePubDate(job.pubDate()),
                raw);
    }

    /**
     * ISO-8601 with offset ("2026-10-17T08:33:07+00:00", the current API) or the older
     * "yyyy-MM-dd HH:mm:ss" without zone, read as UTC. Null or blank → null.
     *
     * @throws IllegalArgumentException for any other format
     */
    public static Instant parsePubDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // fall through to the zone-less forms
        }
        try {
            return LocalDateTime.parse(text, LEGACY).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("unreadable pubDate '" + text + "'");
        }
    }

    /** "Germany,  Ireland,  Spain" → "Germany, Ireland, Spain". */
    static String location(String jobGeo) {
        if (jobGeo == null || jobGeo.isBlank()) {
            return null;
        }
        return String.join(", ", Arrays.stream(jobGeo.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
    }

    private static String description(JobicyJobDto job, boolean unstructuredSalary) {
        String text = HtmlText.toPlain(job.jobDescription());
        if (!unstructuredSalary) {
            return text;
        }
        return text + (text.isEmpty() ? "" : "\n\n") + "Salary: " + amount(job.salaryMin()) + " - "
                + amount(job.salaryMax()) + (job.salaryCurrency() == null ? "" : " " + job.salaryCurrency().strip())
                + " (" + job.salaryPeriod().strip() + ")";
    }

    /** Industries, then levels ("Entry-Level, Junior" → two tags; "Any" dropped), lower-case, distinct. */
    private static List<String> tags(JobicyJobDto job) {
        Stream<String> industries = job.jobIndustry() == null ? Stream.empty() : job.jobIndustry().stream();
        Stream<String> levels = job.jobLevel() == null ? Stream.empty() : Arrays.stream(job.jobLevel().split(","));
        return Stream.concat(industries, levels)
                .filter(Objects::nonNull)
                .map(t -> t.strip().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty() && !t.equals("any"))
                .distinct()
                .toList();
    }

    private static String join(List<String> values) {
        if (values == null) {
            return null;
        }
        String joined = String.join(", ", values.stream().filter(Objects::nonNull).map(String::strip)
                .filter(v -> !v.isEmpty()).toList());
        return joined.isEmpty() ? null : joined;
    }

    private static String amount(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
