package io.github.quanghung304.jobhunter.source.lever;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.common.text.HtmlText;
import io.github.quanghung304.jobhunter.source.CompanyBoard;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Pure mapping of a Lever posting to {@link NormalizedPosting} (docs/sources/ats.md). */
public final class LeverMapper {

    public static final String SOURCE_CODE = "lever";

    private static final Map<String, RemoteHint> WORKPLACE = Map.of(
            "remote", RemoteHint.REMOTE, "hybrid", RemoteHint.HYBRID, "onsite", RemoteHint.ONSITE);
    private static final Map<String, SalaryPeriod> INTERVALS = Map.of(
            "per-year-salary", SalaryPeriod.YEAR, "per-month-salary", SalaryPeriod.MONTH,
            "per-hour-wage", SalaryPeriod.HOUR);

    private LeverMapper() {
    }

    /** @throws IllegalArgumentException if the posting has no id or hosted url */
    public static NormalizedPosting toPosting(CompanyBoard board, LeverPostingDto posting, JsonNode raw) {
        if (posting.id() == null || posting.id().isBlank()) {
            throw new IllegalArgumentException("missing id");
        }
        if (posting.hostedUrl() == null || posting.hostedUrl().isBlank()) {
            throw new IllegalArgumentException("missing hostedUrl");
        }
        LeverPostingDto.Categories categories = posting.categories() == null
                ? new LeverPostingDto.Categories(null, null, null, null, null) : posting.categories();
        LeverPostingDto.SalaryRange salary = posting.salaryRange();
        SalaryPeriod period = salary == null || salary.interval() == null ? null
                : INTERVALS.get(salary.interval().strip().toLowerCase(Locale.ROOT));
        boolean structured = period != null && (salary.min() != null || salary.max() != null);
        return new NormalizedPosting(
                SOURCE_CODE,
                board.slug() + ":" + posting.id().strip(),
                posting.hostedUrl().strip(),
                posting.text() == null ? null : posting.text().strip(),
                board.name(),
                location(categories),
                posting.workplaceType() == null ? RemoteHint.UNKNOWN
                        : WORKPLACE.getOrDefault(posting.workplaceType().strip().toLowerCase(Locale.ROOT), RemoteHint.UNKNOWN),
                blankToNull(categories.commitment()),
                description(posting),
                blankToNull(posting.applyUrl()),
                structured ? salary.min() : null,
                structured ? salary.max() : null,
                structured ? blankToNull(salary.currency()) : null,
                structured ? period : null,
                Stream.of(categories.department(), categories.team()).map(LeverMapper::blankToNull)
                        .filter(t -> t != null).map(t -> t.toLowerCase(Locale.ROOT)).distinct().toList(),
                posting.createdAt() == null ? null : Instant.ofEpochMilli(posting.createdAt()),
                raw);
    }

    /** {@code descriptionPlain}, then each list as its heading and its items, then {@code additionalPlain}. */
    static String description(LeverPostingDto posting) {
        var parts = new ArrayList<String>();
        addNonBlank(parts, posting.descriptionPlain());
        if (posting.lists() != null) {
            for (LeverPostingDto.ListSection list : posting.lists()) {
                if (list == null) {
                    continue;
                }
                String heading = blankToNull(list.text());
                String items = HtmlText.toPlain(list.content());
                if (heading == null && items.isEmpty()) {
                    continue;
                }
                parts.add(heading == null ? items : items.isEmpty() ? heading : heading + "\n" + items);
            }
        }
        addNonBlank(parts, posting.additionalPlain());
        return String.join("\n\n", parts);
    }

    /** {@code categories.location}, then other {@code allLocations}, joined by ", ". */
    static String location(LeverPostingDto.Categories categories) {
        var places = new LinkedHashSet<String>();
        if (blankToNull(categories.location()) != null) {
            places.add(categories.location().strip());
        }
        if (categories.allLocations() != null) {
            categories.allLocations().stream().map(LeverMapper::blankToNull).filter(p -> p != null).forEach(places::add);
        }
        return places.isEmpty() ? null : String.join(", ", places);
    }

    private static void addNonBlank(List<String> parts, String text) {
        if (text != null && !text.isBlank()) {
            parts.add(text.strip());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
