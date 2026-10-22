package io.github.quanghung304.jobhunter.source.hn;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Headers are real first lines of the October 2026 thread (as {@link HnHeaderParser#headerLine} returns them). */
class HnHeaderParserTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiterString = " § ", nullValues = "-", quoteCharacter = '"', textBlock = """
            Sidekick Labs | Senior SWE, R&D Engineer, Intelligence Engineer | REMOTE (US Timezones) | Full-time | $170k to $230k Base + Equity + Health § Sidekick Labs § Senior SWE, R&D Engineer, Intelligence Engineer § REMOTE (US Timezones) § Full-time § REMOTE
            Shepherd (Series B) | ONSITE | San Francisco, CA & New York City, NY § Shepherd (Series B) § See description § ONSITE | San Francisco, CA & New York City, NY § - § ONSITE
            PAGNOS | Cloud Engineer, AWS Serverless (TypeScript/Node.js) | REMOTE (Germany) | Full-time | €75k–80k | business-fluent German required § PAGNOS § Cloud Engineer, AWS Serverless (TypeScript/Node.js) § REMOTE (Germany) | business-fluent German required § Full-time § REMOTE
            Smarkets (https://www.smarkets.com) | Full Time | Hybrid - Onsite (London, UK) § Smarkets § See description § Hybrid - Onsite (London, UK) § Full Time § HYBRID
            Mainmatter (https://mainmatter.com/rust-consulting/) | REMOTE (EU) | Lead Rust Engineering Consultant | Full-time | 100–140k€ § Mainmatter § Lead Rust Engineering Consultant § REMOTE (EU) § Full-time § REMOTE
            Octane | Senior Software Engineer, Decisioning | REMOTE (US only) or NYC/Dallas | $130K to $170K base + bonus eligibility + employee stock options § Octane § Senior Software Engineer, Decisioning § REMOTE (US only) or NYC/Dallas § - § REMOTE
            Fastly | Software Engineers (Senior, Staff, Principal) | US, UK, EU, APAC ONSITE PREFERRED | Full-time § Fastly § Software Engineers (Senior, Staff, Principal) § US, UK, EU, APAC ONSITE PREFERRED § Full-time § ONSITE
            All Gravy | Senior Software Engineer (Full-stack TypeScript) | Copenhagen or London | REMOTE (European time zones) or ONSITE | Full-time § All Gravy § Senior Software Engineer (Full-stack TypeScript) § Copenhagen or London | REMOTE (European time zones) or ONSITE § Full-time § REMOTE
            Vistulo | Senior Java, C#, React & Calypso engineers, Calypso BA/PO | REMOTE (Poland or Romania) | B2B contract, pay ranges below § Vistulo § Senior Java, C#, React & Calypso engineers, Calypso BA/PO § REMOTE (Poland or Romania) § B2B contract, pay ranges below § REMOTE
            Open Education Applications / Neon | Senior/Lead Platform & DevOps Engineer, Senior Frontend Engineer | Utrecht, The Netherlands | HYBRID | DUTCH REQUIRED § Open Education Applications / Neon § Senior/Lead Platform & DevOps Engineer, Senior Frontend Engineer § Utrecht, The Netherlands | HYBRID | DUTCH REQUIRED § - § HYBRID
            Planlab.ai | | Product Engineer (Design) & Platform Engineer | ONSITE in London, UK | 100k-160k GBP + 0.25-0.75% Equity | Visa sponsorship § Planlab.ai § Product Engineer (Design) & Platform Engineer § ONSITE in London, UK | Visa sponsorship § - § ONSITE
            PrairieLearn (Remote US) — Full-Stack Software Engineer — TypeScript / Postgres / React / AI § PrairieLearn § Full-Stack Software Engineer § TypeScript / Postgres / React / AI § - § REMOTE
            Thunder Compute (YC S24) | C++ Systems, Infrastructure, BizOps | San Francisco (Onsite) | Full-time | https://www.thundercompute.com § Thunder Compute (YC S24) § C++ Systems, Infrastructure, BizOps § San Francisco (Onsite) § Full-time § ONSITE
            Google | Senior Staff Software Engineer, CodeAI | Munich, Germany | On-site § Google § Senior Staff Software Engineer, CodeAI § Munich, Germany | On-site § - § ONSITE
            Snout https://snout.com/ | Multiple Engineering + Product Roles | Remote US or Ontario, Canada | Full Time § Snout § Multiple Engineering + Product Roles § Remote US or Ontario, Canada § Full Time § REMOTE
            Hiya | Senior Backend Software Engineer | Seattle, WA | HYBRID (3 days/week in office) | Full-time | $113K–$197K + equity § Hiya § Senior Backend Software Engineer § Seattle, WA | HYBRID (3 days/week in office) § Full-time § HYBRID
            Mechanize | San Francisco, CA (Onsite) | Junior SWE ($300k + equity + bonus), SWE ($350k + equity + bonus), Research Engineer, Alignment ($300k-$400k + equity + bonus) § Mechanize § Junior SWE ($300k + equity + bonus), SWE ($350k + equity + bonus), Research Engineer, Alignment ($300k-$400k + equity + bonus) § San Francisco, CA (Onsite) § - § ONSITE
            Reactor | https://reactor.inc | San Francisco, CA, USA | Full Time § Reactor § See description § San Francisco, CA, USA § Full Time § UNKNOWN
            Tandem Health | Engineers, Designers, PMs | On-site in Stockholm, Sweden | Full time § Tandem Health § Engineers, Designers, PMs § On-site in Stockholm, Sweden § Full time § ONSITE
            ParadeDB (YC S23, https://paradedb.com) | Database Internals Engineers, Platform Engineers | Remote or hybrid in San Francisco | Full-time § ParadeDB (YC S23) § Database Internals Engineers, Platform Engineers § Remote or hybrid in San Francisco § Full-time § REMOTE
            Estuary | US-Based / Remote | Full-time – https://estuary.dev/ § Estuary § See description § US-Based / Remote § Full-time § REMOTE
            Acme | Backend Engineer | Berlin, Germany | No remote § Acme § Backend Engineer § Berlin, Germany | No remote § - § ONSITE
            """)
    void parsesRealHeaders(String header, String company, String title, String location, String employment,
            RemoteHint remoteHint) {
        HnHeader parsed = HnHeaderParser.parse(header);

        assertThat(parsed.companyName()).isEqualTo(company);
        assertThat(parsed.title()).isEqualTo(title);
        assertThat(parsed.locationText()).isEqualTo(location);
        assertThat(parsed.employmentHint()).isEqualTo(employment);
        assertThat(parsed.remoteHint()).isEqualTo(remoteHint);
        assertThat(parsed.warnings()).isEmpty();
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiterString = " § ", quoteCharacter = '"', textBlock = """
            DuckDuckGo - we are looking for candidates that are excited to join us on a mission to raise the standard of trust online. All of our roles are fully-remote. § DuckDuckGo § REMOTE
            Okta (Auth0) § Okta § UNKNOWN
            whitecircle.com/careers, we are building the most advanced AI safety infra, raised roughly $70m in total, join us § whitecircle.com/careers § UNKNOWN
            Hiring for several roles at StudyTurtle, an edutainment brand building learning videos for Education. § Hiring for several roles at StudyTurtle § UNKNOWN
            I lead the A3 Lab at Aarhus University and are hiring a Three-year PhD in Physical AI; onsite in Aarhus, Denmark. § I lead the A3 Lab at Aarhus University and are hiring a § ONSITE
            Role: UX/UI Designer (BAY AREA, CA - HYBRID) 1099 CONTRACT § Role § HYBRID
            """)
    void headerWithoutSeparatorUsesTheStartAsCompanyAndWarns(String header, String company, RemoteHint remoteHint) {
        HnHeader parsed = HnHeaderParser.parse(header);

        assertThat(parsed.companyName()).isEqualTo(company);
        assertThat(parsed.companyName().length()).isLessThanOrEqualTo(HnHeaderParser.MAX_FALLBACK_COMPANY);
        assertThat(parsed.title()).isEqualTo(HnHeaderParser.NO_TITLE);
        assertThat(parsed.locationText()).isNull();
        assertThat(parsed.remoteHint()).isEqualTo(remoteHint);
        assertThat(parsed.warnings()).containsExactly(HnHeaderParser.NO_SEPARATOR_WARNING);
    }

    @Test
    void headerOfOnlyALinkHasABlankCompany() {
        HnHeader parsed = HnHeaderParser.parse("https://grnh.se/rlbk86k09us");

        assertThat(parsed.companyName()).isEmpty();
        assertThat(parsed.title()).isEqualTo(HnHeaderParser.NO_TITLE);
    }

    @Test
    void proseSegmentsAreNeitherTitleNorLocation() {
        HnHeader parsed = HnHeaderParser.parse("Runway | Remote (see below) | Full-time | https://runway.com/ Runway is"
                + " building foundational Real-World Intelligence that can understand, simulate and act in the world. We"
                + " offer products and services built on-top of this intelligence to empower individuals and engineers");

        assertThat(parsed.title()).isEqualTo(HnHeaderParser.NO_TITLE);
        assertThat(parsed.locationText()).isEqualTo("Remote (see below)");
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiterString = " § ", textBlock = """
            Acme | Engineer | REMOTE § REMOTE
            Acme | Engineer | Remote-first § REMOTE
            Acme | Engineer | REMOTE(Worldwide) or ONSITE § REMOTE
            Acme | Engineer | London | onsite only § ONSITE
            Acme | Engineer | London | no remote § ONSITE
            Acme | Engineer | London | no remote, hybrid 2 days § HYBRID
            Acme | Engineer | In Office (Long Beach, CA ) § ONSITE
            Acme | Engineer | In Person - Kirkland, WA § ONSITE
            Acme | Engineer | NYC/JC Hybrid § HYBRID
            Acme | Engineer | Berkeley, CA § UNKNOWN
            """)
    void remoteHint(String header, RemoteHint expected) {
        assertThat(HnHeaderParser.remoteHint(header)).isEqualTo(expected);
    }

    @Test
    void headerLineStopsAtTheFirstParagraphAndDecodesEntities() {
        String html = "Sidekick Labs | Senior SWE, R&amp;D Engineer | REMOTE (US Timezones)<p>Sidekick builds AI security";

        assertThat(HnHeaderParser.headerLine(html)).isEqualTo("Sidekick Labs | Senior SWE, R&D Engineer | REMOTE (US Timezones)");
    }

    @Test
    void headerLineStopsAtALineBreakAndDropsTags() {
        String html = "<i>Fastly | Software Engineers (Senior, Staff, Principal) | Full-time</i>\nWhen you look at the"
                + " headers of major websites<p>More";

        assertThat(HnHeaderParser.headerLine(html)).isEqualTo("Fastly | Software Engineers (Senior, Staff, Principal) | Full-time");
    }

    @Test
    void headerLineKeepsLinkTextAndDecodesSlashes() {
        String html = "Mainmatter (<a href=\"https:&#x2F;&#x2F;mainmatter.com&#x2F;\" rel=\"nofollow\">"
                + "https:&#x2F;&#x2F;mainmatter.com&#x2F;</a>) | REMOTE (EU)<p>Body";

        assertThat(HnHeaderParser.headerLine(html)).isEqualTo("Mainmatter (https://mainmatter.com/) | REMOTE (EU)");
        assertThat(HnHeaderParser.fromComment(html).companyName()).isEqualTo("Mainmatter");
    }

    @Test
    void headerLineOfACommentStartingWithAParagraphIsItsFirstText() {
        assertThat(HnHeaderParser.headerLine("<p>Acme | Backend Engineer<p>Body")).isEqualTo("Acme | Backend Engineer");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void blankCommentHasABlankHeader(String html) {
        assertThat(HnHeaderParser.headerLine(html)).isEmpty();
    }

    @Test
    void longLocationIsTruncated() {
        String header = "Acme | Engineer | " + "Berlin, Munich, Hamburg, Cologne, Frankfurt | ".repeat(6) + "REMOTE";

        assertThat(HnHeaderParser.parse(header).locationText()).hasSizeLessThanOrEqualTo(HnHeaderParser.MAX_LOCATION);
    }
}
