package io.github.quanghung304.jobhunter.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class TextNormalizerTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            Acme, Inc.                     | acme
            ACME Inc                       | acme
            Acme                           | acme
            acme ltd.                      | acme
            Acme Corp.                     | acme
            Acme Limited                   | acme
            Acme LLC                       | acme
            Società Srl                    | societa
            Società S.r.l.                 | societa
            Ferrari S.p.A.                 | ferrari
            Siemens AG                     | siemens
            Acme GmbH & Co. KG             | acme
            Zalando SE                     | zalando
            Booking.com B.V.               | booking com
            Nestlé S.A.                    | nestle
            Café Crème Ltd                 | cafe creme
            Wolt - English                 | wolt english
            Tracium GmbH                   | tracium
            '  The   Acme   Company  '     | the acme company
            Co                             | co
            Inc. Ltd.                      | inc
            Coca-Cola Co                   | coca cola
            J. P. Morgan                   | j p morgan
            Spotify AB                     | spotify
            Ørsted A/S                     | ørsted
            """)
    void companyKey(String name, String expected) {
        assertThat(TextNormalizer.companyKey(name)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            Senior Backend Engineer (Remote) - m/f/d         | senior backend engineer
            Senior Backend Engineer                          | senior backend engineer
            Senior Backend Engineer (m/w/d)                  | senior backend engineer
            Senior Backend Engineer – Remote                 | senior backend engineer
            'Senior Backend Engineer | Remote'               | senior backend engineer
            Senior Backend Engineer, Remote, EU              | senior backend engineer eu
            [Remote] Senior Backend Engineer                 | senior backend engineer
            Senior Backend Engineer f/m/d                    | senior backend engineer
            Senior Backend Engineer w/m/d                    | senior backend engineer
            Senior Backend Engineer (f/m/x)                  | senior backend engineer
            Senior Backend Engineer m / w / d                | senior backend engineer
            Développeur Backend Java (H/F)                   | developpeur backend java
            Full-Stack Engineer – AI Authoring Platform      | full stack engineer ai authoring platform
            Backend/Platform Engineer                        | backend platform engineer
            C++ Developer                                    | c++ developer
            C# .NET Developer                                | c# .net developer
            Remote-first Java Developer                      | first java developer
            Remoteness Analyst                               | remoteness analyst
            Java Developer (Senior (Lead))                   | java developer
            Werkstudent Software (m/w/d) - Berlin            | werkstudent software berlin
            """)
    void titleKey(String title, String expected) {
        assertThat(TextNormalizer.titleKey(title)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void blankInputGivesEmptyKeys(String text) {
        assertThat(TextNormalizer.companyKey(text)).isEmpty();
        assertThat(TextNormalizer.titleKey(text)).isEmpty();
    }
}
