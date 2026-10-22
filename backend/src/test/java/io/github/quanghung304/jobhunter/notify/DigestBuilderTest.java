package io.github.quanghung304.jobhunter.notify;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.notify.DigestBuilder.Digest;
import io.github.quanghung304.jobhunter.tracking.DigestQuery.ScoredJob;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class DigestBuilderTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 22);
    static final String BASE = "http://localhost:8080/";

    static ScoredJob job(long id, int score) {
        return new ScoredJob(id, "Java Developer " + id, "Acme " + id, score, "Good match " + id,
                "https://himalayas.app/jobs/" + id, Instant.parse("2026-10-21T10:00:00Z"));
    }

    @Test
    void nothingWaitingAndNothingScoredSendsNothing() {
        assertThat(DigestBuilder.build(DAY, 0, List.of(), 65, BASE)).isEmpty();
    }

    @Test
    void listsWaitingJobsAndScoredJobsWithLinks() {
        Digest digest = DigestBuilder.build(DAY, 3, List.of(job(42, 91), job(7, 70)), 65, BASE).orElseThrow();

        assertThat(digest.html()).isEqualTo("""
                <b>Job Hunter · 22 Oct</b>
                3 new jobs wait for Analyze · <a href="http://localhost:8080/?analysisStage=NOT_ANALYZED&amp;status=NEW">Open board</a>

                <b>Scored 65+</b>
                <b>91</b> <a href="http://localhost:8080/jobs/42">Java Developer 42</a> · Acme 42 · <a href="https://himalayas.app/jobs/42">Posting</a>
                <i>Good match 42</i>
                <b>70</b> <a href="http://localhost:8080/jobs/7">Java Developer 7</a> · Acme 7 · <a href="https://himalayas.app/jobs/7">Posting</a>
                <i>Good match 7</i>""");
        assertThat(digest.jobIds()).containsExactly(42L, 7L);
    }

    @Test
    void onlyWaitingJobs() {
        Digest digest = DigestBuilder.build(DAY, 1, List.of(), 65, BASE).orElseThrow();

        assertThat(digest.html()).contains("1 new job waits for Analyze").doesNotContain("Scored");
        assertThat(digest.jobIds()).isEmpty();
    }

    @Test
    void listsFifteenAndCountsTheRestWhichAreCoveredToo() {
        List<ScoredJob> scored = LongStream.rangeClosed(1, 19).mapToObj(id -> job(id, 100 - (int) id)).toList();

        Digest digest = DigestBuilder.build(DAY, 0, scored, 65, BASE).orElseThrow();

        assertThat(digest.html().lines().filter(line -> line.contains("/jobs/"))).hasSize(DigestBuilder.MAX_LISTED);
        assertThat(digest.html()).contains("Java Developer 15<").doesNotContain("Java Developer 16<")
                .contains("and 4 more · <a href=\"http://localhost:8080/?status=NEW&amp;minScore=65\">Open board</a>");
        assertThat(digest.jobIds()).as("the 4 more are covered: never sent again").hasSize(19);
    }

    @Test
    void textAndUrlsAreEscapedForTelegramHtml() {
        var nasty = new ScoredJob(5, "C++ <Senior> & Lead", "Smith & \"Sons\" <GmbH>", 80,
                "Uses <script> & stuff\nacross lines", "https://jobs.example/?a=1&b=\"2\"", Instant.EPOCH);

        String html = DigestBuilder.build(DAY, 0, List.of(nasty), 65, BASE).orElseThrow().html();

        assertThat(html).contains("C++ &lt;Senior&gt; &amp; Lead</a>")
                .contains("Smith &amp; \"Sons\" &lt;GmbH&gt;")
                .contains("<i>Uses &lt;script&gt; &amp; stuff across lines</i>")
                .contains("href=\"https://jobs.example/?a=1&amp;b=&quot;2&quot;\"")
                .doesNotContain("<Senior>", "<script>", "<GmbH>");
        assertThat(html.lines()).allSatisfy(line -> assertThat(count(line, "<a ")).isEqualTo(count(line, "</a>")));
    }

    @Test
    void aJobWithoutPostingOrSummaryHasNoSuchParts() {
        var bare = new ScoredJob(9, "Dev", "Co", 66, null, null, Instant.EPOCH);

        String html = DigestBuilder.build(DAY, 0, List.of(bare), 65, BASE).orElseThrow().html();

        assertThat(html).endsWith("<b>66</b> <a href=\"http://localhost:8080/jobs/9\">Dev</a> · Co")
                .doesNotContain("Posting", "<i>");
    }

    @Test
    void longSummariesAreShortened() {
        var wordy = new ScoredJob(1, "Dev", "Co", 90, "w".repeat(500), null, Instant.EPOCH);

        String html = DigestBuilder.build(DAY, 0, List.of(wordy), 65, BASE).orElseThrow().html();

        String summary = html.lines().filter(line -> line.startsWith("<i>")).findFirst().orElseThrow();
        assertThat(summary).hasSize("<i></i>".length() + DigestBuilder.MAX_SUMMARY).endsWith("…</i>");
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }
}
