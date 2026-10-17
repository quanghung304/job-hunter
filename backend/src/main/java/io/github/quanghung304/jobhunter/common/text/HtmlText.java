package io.github.quanghung304.jobhunter.common.text;

import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/** Converts HTML job descriptions to readable plain text. */
public final class HtmlText {

    /** Elements rendered as a block separated by a blank line. */
    private static final Set<String> PARAGRAPHS = Set.of(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "table", "ul", "ol");

    /** Elements rendered on their own line. */
    private static final Set<String> LINES = Set.of("li", "div", "tr", "dt", "dd", "section", "article");

    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[ \\t\\x0B\\f\\u00A0]+");
    private static final Pattern THREE_OR_MORE_NEWLINES = Pattern.compile("\\n{3,}");
    /** One escaped tag can be literal text ("use &lt;div&gt;"); several mean escaped markup. */
    private static final int MIN_ESCAPED_TAGS = 3;
    private static final Pattern ESCAPED_TAG = Pattern.compile("&lt;/?[a-zA-Z][a-zA-Z0-9]*[\\s/&]");

    private HtmlText() {
    }

    /**
     * Returns the plain text of {@code html}: {@code <p>}, {@code <li>}, {@code <br>} and other
     * block elements become line breaks, entities are decoded, scripts and styles are dropped,
     * lines are trimmed and runs of more than one blank line collapse to a single blank line.
     * Input without markup keeps its own line breaks. HTML that was entity-escaped once more
     * (three or more {@code &lt;tag} sequences, as some feeds send it, sometimes followed by a
     * real HTML footer) is unescaped before rendering.
     * {@code null} or blank input gives "".
     */
    public static String toPlain(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String source = isEscapedHtml(html) ? Parser.unescapeEntities(html, false) : html;
        String text = source.indexOf('<') < 0 && source.indexOf('&') < 0 ? source : render(source);
        return tidy(text);
    }

    private static boolean isEscapedHtml(String text) {
        var matcher = ESCAPED_TAG.matcher(text);
        int found = 0;
        while (found < MIN_ESCAPED_TAGS && matcher.find()) {
            found++;
        }
        return found >= MIN_ESCAPED_TAGS;
    }

    private static String render(String html) {
        var out = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    out.append(textNode.text());
                } else if (node instanceof Element element) {
                    if (element.normalName().equals("br")) {
                        out.append('\n');
                    } else {
                        breakFor(element);
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element) {
                    breakFor(element);
                }
            }

            private void breakFor(Element element) {
                String tag = element.normalName();
                if (PARAGRAPHS.contains(tag)) {
                    endWithNewlines(out, 2);
                } else if (LINES.contains(tag)) {
                    endWithNewlines(out, 1);
                }
            }
        }, Jsoup.parse(html).body());
        return out.toString();
    }

    /** Makes {@code out} end with at least {@code count} line breaks, unless it is empty. */
    private static void endWithNewlines(StringBuilder out, int count) {
        if (out.isEmpty()) {
            return;
        }
        int trailing = 0;
        while (trailing < out.length() && out.charAt(out.length() - 1 - trailing) == '\n') {
            trailing++;
        }
        out.append("\n".repeat(Math.max(0, count - trailing)));
    }

    private static String tidy(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        var lines = new StringBuilder(normalized.length());
        for (String line : normalized.split("\n", -1)) {
            lines.append(HORIZONTAL_SPACE.matcher(line).replaceAll(" ").strip()).append('\n');
        }
        return THREE_OR_MORE_NEWLINES.matcher(lines).replaceAll("\n\n").strip();
    }
}
