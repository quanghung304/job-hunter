package io.github.quanghung304.jobhunter.common.text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Keys used to recognize the same job across sources (data-model.md §4). Both keys are
 * lower-case, accent-free, whitespace-collapsed strings.
 */
public final class TextNormalizer {

    /**
     * Legal-form words removed from the end of a company name, repeatedly
     * ("Acme GmbH &amp; Co. KG" → "acme"). Spec list plus common European forms.
     */
    static final Set<String> LEGAL_SUFFIXES = Set.of(
            "inc", "ltd", "llc", "gmbh", "srl", "spa", "bv", "sa", "co", "corp", "limited",
            "incorporated", "corporation", "plc", "llp", "ag", "kg", "ug", "se", "nv", "sas", "sarl",
            "sl", "oy", "ab", "as", "aps", "pty", "pvt");

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern BRACKETED = Pattern.compile("\\([^()]*\\)|\\[[^\\[\\]]*]");
    /** m/f/d, w/m/d, f/m/x, m/w/d/x, ... */
    private static final Pattern GENDER_TAG = Pattern.compile("(?<![\\p{L}\\p{N}])[mfwdx](?:\\s*/\\s*[mfwdx]){1,3}(?![\\p{L}\\p{N}])");
    private static final Pattern SEPARATORS = Pattern.compile("[-–—|/,]");
    private static final Pattern REMOTE_WORD = Pattern.compile("(?<![\\p{L}\\p{N}])remote(?![\\p{L}\\p{N}])");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TextNormalizer() {
    }

    /**
     * Lower-case, accents stripped, punctuation removed, trailing legal suffixes removed,
     * whitespace collapsed: "Acme, Inc." → {@code acme}, "Società S.r.l." → {@code societa}.
     * If only legal words remain, they are kept ("Co" stays {@code co}).
     */
    public static String companyKey(String companyName) {
        if (companyName == null) {
            return "";
        }
        String plain = NOT_LETTER_OR_DIGIT.matcher(fold(companyName)).replaceAll(" ").strip();
        if (plain.isEmpty()) {
            return "";
        }
        List<String> words = new ArrayList<>(Arrays.asList(WHITESPACE.split(plain)));
        // "S.r.l." and "S.p.A." became single letters: rejoin a trailing run of them.
        joinTrailingLetters(words);
        while (words.size() > 1 && LEGAL_SUFFIXES.contains(words.get(words.size() - 1))) {
            words.remove(words.size() - 1);
        }
        return String.join(" ", words);
    }

    /**
     * Lower-case, accents stripped, (…) and […] removed, gender tags (m/f/d, w/m/d, …) removed,
     * separators {@code - – — | / ,} replaced by spaces, the word "remote" removed,
     * whitespace collapsed: "Senior Backend Engineer (Remote) - m/f/d" → {@code senior backend engineer}.
     */
    public static String titleKey(String title) {
        if (title == null) {
            return "";
        }
        String text = fold(title);
        String previous;
        do { // nested brackets: remove innermost first
            previous = text;
            text = BRACKETED.matcher(text).replaceAll(" ");
        } while (!text.equals(previous));
        text = GENDER_TAG.matcher(text).replaceAll(" ");
        text = SEPARATORS.matcher(text).replaceAll(" ");
        text = REMOTE_WORD.matcher(text).replaceAll(" ");
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    /** Lower-case without diacritics ("Società" → "societa"). */
    static String fold(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static void joinTrailingLetters(List<String> words) {
        int start = words.size();
        while (start > 0 && words.get(start - 1).length() == 1 && Character.isLetter(words.get(start - 1).charAt(0))) {
            start--;
        }
        if (words.size() - start >= 2 && start > 0) {
            String joined = String.join("", words.subList(start, words.size()));
            words.subList(start, words.size()).clear();
            words.add(joined);
        }
    }
}
