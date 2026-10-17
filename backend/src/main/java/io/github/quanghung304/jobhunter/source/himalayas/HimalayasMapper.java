package io.github.quanghung304.jobhunter.source.himalayas;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.common.text.HtmlText;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Pure mapping of a Himalayas job to {@link NormalizedPosting} (docs/sources/himalayas.md). */
public final class HimalayasMapper {

    public static final String SOURCE_CODE = "himalayas";
    static final String WORLDWIDE = "Worldwide";
    /** Jobs without a timezone restriction list every offset (37 seen); treat ≥ 24 as "none". */
    static final int ALL_TIMEZONES_THRESHOLD = 24;

    private static final Map<String, SalaryPeriod> PERIODS = Map.of(
            "annual", SalaryPeriod.YEAR, "monthly", SalaryPeriod.MONTH, "hourly", SalaryPeriod.HOUR);

    private HimalayasMapper() {
    }

    /** @throws IllegalArgumentException if the job has no {@code guid} */
    public static NormalizedPosting toPosting(HimalayasJobDto job, JsonNode raw) {
        String guid = required(job.guid(), "guid");
        List<String> locations = nonBlank(job.locationRestrictions());
        SalaryPeriod period = job.salaryPeriod() == null ? SalaryPeriod.YEAR
                : PERIODS.get(job.salaryPeriod().strip().toLowerCase(Locale.ROOT));
        boolean hasAmount = job.minSalary() != null || job.maxSalary() != null;
        boolean structuredSalary = hasAmount && period != null;

        return new NormalizedPosting(
                SOURCE_CODE,
                guid,
                guid,
                strip(job.title()),
                strip(job.companyName()),
                locations.isEmpty() ? WORLDWIDE : String.join(", ", locations),
                RemoteHint.REMOTE,
                blankToNull(job.employmentType()),
                description(job, locations, hasAmount && !structuredSalary),
                // API terms: every Apply action must use applicationLink.
                job.applicationLink() == null || job.applicationLink().isBlank() ? guid : job.applicationLink().strip(),
                structuredSalary ? job.minSalary() : null,
                structuredSalary ? job.maxSalary() : null,
                structuredSalary ? blankToNull(job.currency()) : null,
                structuredSalary ? period : null,
                tags(job),
                job.pubDate() == null ? null : Instant.ofEpochSecond(job.pubDate()),
                raw);
    }

    /**
     * Description text plus the restrictions as plain lines, so extraction sees them:
     * "Location restrictions: …" / "none listed", "Timezone restrictions: UTC+1, …" / "none",
     * and the salary when its period (weekly, fortnightly) has no structured equivalent.
     */
    static String description(HimalayasJobDto job, List<String> locations, boolean unstructuredSalary) {
        var text = new StringBuilder(HtmlText.toPlain(job.description()));
        if (!text.isEmpty()) {
            text.append("\n\n");
        }
        text.append("Location restrictions: ").append(locations.isEmpty() ? "none listed" : String.join(", ", locations));
        text.append("\nTimezone restrictions: ").append(timezones(job.timezoneRestrictions()));
        if (unstructuredSalary) {
            text.append("\nSalary: ").append(amount(job.minSalary())).append(" - ").append(amount(job.maxSalary()))
                    .append(job.currency() == null ? "" : " " + job.currency().strip())
                    .append(" (").append(job.salaryPeriod().strip()).append(")");
        }
        return text.toString();
    }

    static String timezones(List<BigDecimal> offsets) {
        List<BigDecimal> distinct = offsets == null ? List.of()
                : offsets.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (distinct.isEmpty() || distinct.size() >= ALL_TIMEZONES_THRESHOLD) {
            return "none";
        }
        return String.join(", ", distinct.stream().map(HimalayasMapper::utcOffset).toList());
    }

    /** 1 → "UTC+1", -3 → "UTC-3", 5.5 → "UTC+5:30", 0 → "UTC+0". */
    static String utcOffset(BigDecimal hours) {
        String sign = hours.signum() < 0 ? "-" : "+";
        BigDecimal abs = hours.abs();
        int whole = abs.intValue();
        int minutes = abs.subtract(BigDecimal.valueOf(whole)).multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        return "UTC" + sign + whole + (minutes == 0 ? "" : ":" + String.format(Locale.ROOT, "%02d", minutes));
    }

    /** Categories ("Backend-Engineer" → "backend engineer") then seniority, lower-case, distinct. */
    private static List<String> tags(HimalayasJobDto job) {
        return Stream.concat(nonBlank(job.categories()).stream().map(c -> c.replace('-', ' ')),
                        nonBlank(job.seniority()).stream())
                .map(t -> t.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static String amount(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }

    private static List<String> nonBlank(List<String> values) {
        if (values == null) {
            return List.of();
        }
        var result = new ArrayList<String>();
        values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip).forEach(result::add);
        return result;
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
