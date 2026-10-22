package io.github.quanghung304.jobhunter.source.ashby;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.common.text.HtmlText;
import io.github.quanghung304.jobhunter.source.CompanyBoard;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Pure mapping of an Ashby job to {@link NormalizedPosting} (docs/sources/ats.md). */
public final class AshbyMapper {

    public static final String SOURCE_CODE = "ashby";

    private static final Map<String, RemoteHint> WORKPLACE = Map.of(
            "remote", RemoteHint.REMOTE, "hybrid", RemoteHint.HYBRID, "onsite", RemoteHint.ONSITE);
    private static final Map<String, SalaryPeriod> INTERVALS = Map.of(
            "1 YEAR", SalaryPeriod.YEAR, "1 MONTH", SalaryPeriod.MONTH, "1 HOUR", SalaryPeriod.HOUR);

    private AshbyMapper() {
    }

    /**
     * Empty for an unlisted job ({@code isListed: false}).
     *
     * @throws IllegalArgumentException if the job has no id or url, or an unreadable {@code publishedAt}
     */
    public static Optional<NormalizedPosting> toPosting(CompanyBoard board, AshbyJobDto job, JsonNode raw) {
        if (Boolean.FALSE.equals(job.isListed())) {
            return Optional.empty();
        }
        if (job.id() == null || job.id().isBlank()) {
            throw new IllegalArgumentException("missing id");
        }
        if (job.jobUrl() == null || job.jobUrl().isBlank()) {
            throw new IllegalArgumentException("missing jobUrl");
        }
        Optional<AshbyJobDto.Component> salary = salary(job.compensation());
        SalaryPeriod period = salary.map(s -> INTERVALS.get(s.interval())).orElse(null);
        return Optional.of(new NormalizedPosting(
                SOURCE_CODE,
                board.slug() + ":" + job.id().strip(),
                job.jobUrl().strip(),
                job.title() == null ? null : job.title().strip(),
                board.name(),
                location(job),
                remoteHint(job),
                blankToNull(job.employmentType()),
                description(job, salary.isEmpty()),
                blankToNull(job.applyUrl()),
                salary.map(AshbyJobDto.Component::minValue).orElse(null),
                salary.map(AshbyJobDto.Component::maxValue).orElse(null),
                salary.map(s -> blankToNull(s.currencyCode())).orElse(null),
                period,
                Stream.of(job.department(), job.team()).map(AshbyMapper::blankToNull).filter(Objects::nonNull)
                        .map(t -> t.toLowerCase(Locale.ROOT)).distinct().toList(),
                publishedAt(job.publishedAt()),
                raw));
    }

    /** {@code workplaceType}; when it is missing, REMOTE if {@code isRemote}, else UNKNOWN. */
    static RemoteHint remoteHint(AshbyJobDto job) {
        if (job.workplaceType() != null && !job.workplaceType().isBlank()) {
            return WORKPLACE.getOrDefault(job.workplaceType().strip().toLowerCase(Locale.ROOT), RemoteHint.UNKNOWN);
        }
        return Boolean.TRUE.equals(job.isRemote()) ? RemoteHint.REMOTE : RemoteHint.UNKNOWN;
    }

    /** The single Salary component with an amount and a known interval; empty if none or several. */
    static Optional<AshbyJobDto.Component> salary(AshbyJobDto.Compensation compensation) {
        if (compensation == null || compensation.summaryComponents() == null) {
            return Optional.empty();
        }
        List<AshbyJobDto.Component> salaries = compensation.summaryComponents().stream()
                .filter(Objects::nonNull)
                .filter(c -> "Salary".equals(c.compensationType()))
                .filter(c -> c.minValue() != null || c.maxValue() != null)
                .toList();
        return salaries.size() == 1 && INTERVALS.containsKey(salaries.get(0).interval())
                ? Optional.of(salaries.get(0)) : Optional.empty();
    }

    /** {@code descriptionPlain} (else HTML to text), plus the compensation summary when the salary is not structured. */
    static String description(AshbyJobDto job, boolean appendCompensation) {
        String text = job.descriptionPlain() != null && !job.descriptionPlain().isBlank()
                ? job.descriptionPlain().strip() : HtmlText.toPlain(job.descriptionHtml());
        String summary = job.compensation() == null ? null : blankToNull(job.compensation().compensationTierSummary());
        if (!appendCompensation || summary == null) {
            return text;
        }
        return text + (text.isEmpty() ? "" : "\n\n") + "Compensation: " + summary;
    }

    /** {@code location}, then the secondary locations, joined by ", ". */
    static String location(AshbyJobDto job) {
        var places = new LinkedHashSet<String>();
        if (blankToNull(job.location()) != null) {
            places.add(job.location().strip());
        }
        if (job.secondaryLocations() != null) {
            job.secondaryLocations().stream().filter(Objects::nonNull).map(AshbyJobDto.SecondaryLocation::location)
                    .map(AshbyMapper::blankToNull).filter(Objects::nonNull).forEach(places::add);
        }
        return places.isEmpty() ? null : String.join(", ", places);
    }

    static Instant publishedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.strip()).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("unreadable publishedAt '" + value.strip() + "'");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
