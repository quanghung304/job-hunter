package io.github.quanghung304.jobhunter.profile;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Renders the candidate excerpt sent to the scoring prompt ({@code {{candidateBlock}}}):
 * headline, experience, location/timezone, languages, summary, skills by tier, domains and
 * preferences as plain lines. Ids, prefilter and notify settings are not included.
 * E-mail addresses and phone numbers that slip into free text are redacted.
 */
public final class CandidateBlock {

    static final String REDACTED = "[redacted]";

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    /** Digit runs with phone punctuation; only redacted when they contain 9+ digits. */
    private static final Pattern PHONE_CANDIDATE = Pattern.compile("\\+?\\d[\\d ()./-]{7,}\\d");
    private static final int MIN_PHONE_DIGITS = 9;

    private CandidateBlock() {
    }

    public static String render(Profile profile) {
        var c = profile.candidate();
        var p = profile.preferences();
        var out = new StringBuilder();

        line(out, "Headline: " + c.headline());
        line(out, "Experience: " + plain(c.yearsExperience()) + " years");
        line(out, "Lives in: " + (c.city() != null && !c.city().isBlank() ? c.city() + ", " : "")
                + c.residenceCountry() + " (timezone " + c.timezone().getId() + ")");
        line(out, "Languages: " + c.languages().stream()
                .map(l -> l.code() + " " + l.level())
                .collect(Collectors.joining(", ")));
        line(out, "Summary:");
        c.summary().strip().lines().forEach(l -> line(out, l));
        line(out, "Core skills: " + join(c.skills().core()));
        optional(out, "Secondary skills: ", c.skills().secondary());
        optional(out, "Learning: ", c.skills().learning());
        optional(out, "Domains: ", c.domains());

        line(out, "Preferences:");
        line(out, "- Employment types: " + join(p.employmentTypes()));
        line(out, "- Seniority: " + join(p.seniority()));
        if (p.maxHoursPerWeek() != null) {
            line(out, "- Max hours per week: " + p.maxHoursPerWeek());
        }
        line(out, "- Can work under region restrictions: " + join(p.acceptRegions()));
        line(out, "- Max timezone difference: " + p.timezone().maxOffsetDiffHours() + " hours");
        if (p.minSalary() != null) {
            var s = p.minSalary();
            line(out, "- Minimum salary: " + plain(s.amount()) + " " + s.currency() + " per " + s.period());
        }
        return redactContacts(out.toString().strip());
    }

    static String redactContacts(String text) {
        String withoutEmails = EMAIL.matcher(text).replaceAll(REDACTED);
        Matcher m = PHONE_CANDIDATE.matcher(withoutEmails);
        var out = new StringBuilder();
        while (m.find()) {
            long digits = m.group().chars().filter(Character::isDigit).count();
            m.appendReplacement(out, Matcher.quoteReplacement(digits >= MIN_PHONE_DIGITS ? REDACTED : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static void optional(StringBuilder out, String label, List<String> values) {
        if (!values.isEmpty()) {
            line(out, label + join(values));
        }
    }

    private static String join(List<?> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String plain(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString();
    }

    private static void line(StringBuilder out, String text) {
        out.append(text).append('\n');
    }
}
