package io.github.quanghung304.jobhunter.source.hn;

import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.text.HtmlText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic parser of the conventional first line of a "Who is hiring?" comment,
 * {@code Company | Role(s) | Location | REMOTE/ONSITE | Salary | URL}, which posters follow
 * loosely (docs/sources/hn-whoishiring.md, "Parsing a comment"). Segments after the company are
 * classified by keywords, because their order varies.
 */
public final class HnHeaderParser {

    public static final String NO_TITLE = "See description";
    public static final String NO_SEPARATOR_WARNING = "header has no '|' separator";

    static final int MAX_LOCATION = 200;
    static final int MAX_FALLBACK_COMPANY = 60;
    /** A longer segment is prose (no line break before the description), not a field. */
    static final int MAX_SEGMENT = 150;

    private static final Pattern PARAGRAPH = Pattern.compile("(?i)<p\\b");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern URL = Pattern.compile("(?i)\\bhttps?://[^\\s()]+");
    private static final Pattern DASH_SEPARATOR = Pattern.compile("\\s+[—–]\\s+");
    private static final Pattern PARENTHESES = Pattern.compile("\\(([^()]*)\\)");
    /** Where a free-text first line stops being a company name. */
    private static final Pattern FALLBACK_STOP = Pattern.compile("\\s[-–—]\\s|[|,;:(!?]|\\.(?:\\s|$)");

    private static final Pattern NOT_REMOTE = Pattern.compile(
            "(?i)\\b(?:no remote|not remote|on-?site only|in-office only)\\b");
    private static final Pattern REMOTE = Pattern.compile("(?i)\\bremote\\b");
    private static final Pattern HYBRID = Pattern.compile("(?i)\\bhybrid\\b");
    private static final Pattern ONSITE = Pattern.compile("(?i)\\b(?:on-?site|on site|in[- ]office|in[- ]person)\\b");
    private static final Pattern ROLE = Pattern.compile("(?i)\\b(?:engineers?|engineering|developers?|devs?|devops|sre"
            + "|swes?|programmers?|architects?|designers?|scientists?|researchers?|managers?|leads?|head|director"
            + "|vp|cto|founding|co-?founders?|staff|principal|consultants?|specialists?|analysts?|marketing|sales"
            + "|recruiters?|roles?|positions?|technologists?|administrators?|support|operations|qa|testers?|mts"
            + "|pm|infrastructure|backend|frontend|full[- ]?stack)\\b");
    private static final Pattern SALARY = Pattern.compile("[$€£¥₹]|\\b\\d+(?:[.,]\\d+)?\\s?[kK]\\b"
            + "|(?i)\\b(?:salary|usd|eur|gbp|chf|cad|equity|compensation|comp|ote|bonus)\\b");
    private static final Pattern EMPLOYMENT = Pattern.compile("(?i)\\b(?:full[- ]?time|part[- ]?time|contract(?:or)?"
            + "|freelance|interns?|internships?|temporary|permanent|perm|b2b|co-ops?)\\b");

    private enum Kind { WORK_MODE, ROLE, SALARY, EMPLOYMENT, OTHER }

    private HnHeaderParser() {
    }

    /** Parses the header of a comment's HTML ({@link #headerLine}). */
    public static HnHeader fromComment(String commentHtml) {
        return parse(headerLine(commentHtml));
    }

    /**
     * The first line of a comment as plain text: the HTML up to the first {@code <p>} or line
     * break, entities decoded, tags removed, whitespace collapsed. "" for blank input.
     */
    public static String headerLine(String commentHtml) {
        if (commentHtml == null || commentHtml.isBlank()) {
            return "";
        }
        Matcher paragraph = PARAGRAPH.matcher(commentHtml);
        String first = paragraph.find() ? commentHtml.substring(0, paragraph.start()) : commentHtml;
        int newline = first.indexOf('\n');
        String line = firstNonBlankLine(HtmlText.toPlain(newline < 0 ? first : first.substring(0, newline)));
        return line.isEmpty() ? firstNonBlankLine(HtmlText.toPlain(commentHtml)) : line;
    }

    /** Parses one plain-text header line. */
    public static HnHeader parse(String line) {
        String header = line == null ? "" : collapse(line);
        RemoteHint remoteHint = remoteHint(header);
        List<String> segments = segments(header);
        if (segments.size() < 2) {
            return new HnHeader(fallbackCompany(header), NO_TITLE, null, null, remoteHint,
                    List.of(NO_SEPARATOR_WARNING));
        }
        String title = null;
        var location = new ArrayList<String>();
        var employment = new ArrayList<String>();
        for (String segment : segments.subList(1, segments.size())) {
            String text = trimPunctuation(collapse(URL.matcher(segment).replaceAll(" ")));
            if (text.isEmpty() || text.length() > MAX_SEGMENT) {
                continue; // a bare link or prose
            }
            switch (classify(text)) {
                case ROLE -> {
                    if (title == null) {
                        title = text;
                    }
                }
                case WORK_MODE, OTHER -> location.add(text);
                case EMPLOYMENT -> employment.add(text);
                case SALARY -> {
                    // stays in the description only
                }
            }
        }
        return new HnHeader(
                company(segments.get(0)),
                title == null ? NO_TITLE : title,
                location.isEmpty() ? null : truncate(String.join(" | ", location), MAX_LOCATION),
                employment.isEmpty() ? null : String.join(", ", employment),
                remoteHint,
                List.of());
    }

    /**
     * REMOTE if the line says remote (ignoring "no remote", "onsite only"), else HYBRID if it
     * says hybrid, else ONSITE if it says onsite/in office/in person or rules remote out, else UNKNOWN.
     */
    static RemoteHint remoteHint(String header) {
        if (REMOTE.matcher(NOT_REMOTE.matcher(header).replaceAll(" ")).find()) {
            return RemoteHint.REMOTE;
        }
        if (HYBRID.matcher(header).find()) {
            return RemoteHint.HYBRID;
        }
        if (ONSITE.matcher(header).find() || NOT_REMOTE.matcher(header).find()) {
            return RemoteHint.ONSITE;
        }
        return RemoteHint.UNKNOWN;
    }

    /** Split on '|'; without '|', on spaced em/en dashes if that gives at least three parts. */
    private static List<String> segments(String header) {
        String[] parts;
        if (header.indexOf('|') >= 0) {
            parts = header.split("\\|");
        } else {
            parts = DASH_SEPARATOR.split(header);
            if (parts.length < 3) {
                return List.of(header);
            }
        }
        return Arrays.stream(parts).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static Kind classify(String segment) {
        if (isWorkMode(segment)) {
            return Kind.WORK_MODE;
        }
        if (ROLE.matcher(segment).find()) {
            return Kind.ROLE;
        }
        if (SALARY.matcher(segment).find()) {
            return Kind.SALARY;
        }
        if (EMPLOYMENT.matcher(segment).find()) {
            return Kind.EMPLOYMENT;
        }
        return Kind.OTHER;
    }

    private static boolean isWorkMode(String text) {
        return REMOTE.matcher(text).find() || HYBRID.matcher(text).find() || ONSITE.matcher(text).find();
    }

    /** URLs removed; "(…)" groups dropped when empty or a remote/onsite remark ("(Remote US)"). */
    static String company(String segment) {
        Matcher group = PARENTHESES.matcher(URL.matcher(segment).replaceAll(" "));
        var out = new StringBuilder();
        while (group.find()) {
            String inner = collapse(group.group(1)).replaceAll("^[,;\\s]+|[,;\\s]+$", "");
            boolean drop = inner.isEmpty() || isWorkMode(inner);
            group.appendReplacement(out, Matcher.quoteReplacement(drop ? " " : "(" + inner + ")"));
        }
        group.appendTail(out);
        return trimPunctuation(collapse(out.toString()));
    }

    /** Free text: up to the first punctuation (" - ", ",", "(", ". ", ...), at most 60 chars at a word break. */
    static String fallbackCompany(String header) {
        String text = collapse(URL.matcher(header).replaceAll(" "));
        Matcher stop = FALLBACK_STOP.matcher(text);
        String name = stop.find() ? text.substring(0, stop.start()) : text;
        if (name.length() > MAX_FALLBACK_COMPANY) {
            int space = name.lastIndexOf(' ', MAX_FALLBACK_COMPANY);
            name = name.substring(0, space > 0 ? space : MAX_FALLBACK_COMPANY);
        }
        return trimPunctuation(name);
    }

    private static String firstNonBlankLine(String text) {
        return text.lines().map(HnHeaderParser::collapse).filter(l -> !l.isEmpty()).findFirst().orElse("");
    }

    private static String trimPunctuation(String text) {
        return text.replaceAll("^[\\s,;:\\-–—]+|[\\s,;:\\-–—]+$", "");
    }

    private static String collapse(String text) {
        return SPACES.matcher(text.replace(' ', ' ')).replaceAll(" ").strip();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max).strip();
    }
}
