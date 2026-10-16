package io.github.quanghung304.jobhunter.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HtmlTextTest {

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("plain text", "plain text"),
                Arguments.of("<p>One</p><p>Two</p>", "One\n\nTwo"),
                Arguments.of("line 1<br>line 2<br/>line 3", "line 1\nline 2\nline 3"),
                Arguments.of("<ul><li>Java</li><li>Spring</li></ul>", "Java\nSpring"),
                Arguments.of("<p>Intro</p><ul><li>A</li><li>B</li></ul><p>End</p>", "Intro\n\nA\nB\n\nEnd"),
                Arguments.of("<h2>About</h2><div>We build</div>", "About\n\nWe build"),
                Arguments.of("<p>Tom &amp; Jerry &lt;3&nbsp;!</p>", "Tom & Jerry <3 !"),
                Arguments.of("<p>  lots   of\n\t spaces  </p>", "lots of spaces"),
                Arguments.of("<b>bold</b> and <a href=\"x\">link</a>", "bold and link"),
                Arguments.of("<script>alert(1)</script><style>p{}</style><p>kept</p>", "kept"),
                Arguments.of("a<br><br><br><br><br>b", "a\n\nb"),
                Arguments.of("<p>a</p><p></p><p></p><p>b</p>", "a\n\nb"),
                Arguments.of("first\r\n\r\n\r\n\r\nsecond\n  third  ", "first\n\nsecond\nthird"));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void convertsHtmlToPlainText(String html, String expected) {
        assertThat(HtmlText.toPlain(html)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void blankInputGivesEmptyString(String html) {
        assertThat(HtmlText.toPlain(html)).isEmpty();
    }
}
