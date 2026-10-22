package io.github.quanghung304.jobhunter.notify;

import io.github.quanghung304.jobhunter.tracking.DigestQuery.ScoredJob;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The daily digest as Telegram HTML (PRD F2.5): jobs waiting for analysis, then the scored jobs
 * since the last digest, best first, at most {@link #MAX_LISTED} with "and N more". Every line is
 * valid HTML on its own, so {@link TelegramClient} can split at line breaks.
 */
public final class DigestBuilder {

    public static final int MAX_LISTED = 15;
    static final int MAX_SUMMARY = 200;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private DigestBuilder() {
    }

    /**
     * @param jobIds every scored job the digest covers (listed and "more"): none of them is sent
     *               again
     */
    public record Digest(String html, List<Long> jobIds) {
    }

    /** Empty when nothing waits and nothing was scored: no message is sent then. */
    public static Optional<Digest> build(LocalDate day, long awaitingAnalysis, List<ScoredJob> scored, int minScore,
            String publicBaseUrl) {
        if (awaitingAnalysis == 0 && scored.isEmpty()) {
            return Optional.empty();
        }
        String base = stripSlash(publicBaseUrl);
        var html = new StringBuilder();
        html.append("<b>Job Hunter · ").append(escape(DATE.format(day))).append("</b>\n");
        if (awaitingAnalysis > 0) {
            html.append(awaitingAnalysis).append(awaitingAnalysis == 1 ? " new job waits" : " new jobs wait")
                    .append(" for Analyze · ")
                    .append(link(base + "/?analysisStage=NOT_ANALYZED&status=NEW", "Open board")).append('\n');
        }
        if (!scored.isEmpty()) {
            html.append('\n').append("<b>Scored ").append(minScore).append("+</b>\n");
            for (ScoredJob job : scored.subList(0, Math.min(MAX_LISTED, scored.size()))) {
                html.append("<b>").append(job.score()).append("</b> ")
                        .append(link(base + "/jobs/" + job.id(), job.title()))
                        .append(" · ").append(escape(job.company()));
                if (job.postingUrl() != null && !job.postingUrl().isBlank()) {
                    html.append(" · ").append(link(job.postingUrl(), "Posting"));
                }
                html.append('\n');
                if (job.summary() != null && !job.summary().isBlank()) {
                    html.append("<i>").append(escape(shorten(oneLine(job.summary())))).append("</i>\n");
                }
            }
            int more = scored.size() - MAX_LISTED;
            if (more > 0) {
                html.append("and ").append(more).append(" more · ")
                        .append(link(base + "/?status=NEW&minScore=" + minScore, "Open board")).append('\n');
            }
        }
        return Optional.of(new Digest(html.toString().strip(), scored.stream().map(ScoredJob::id).toList()));
    }

    /** Telegram HTML escaping: {@code &}, {@code <}, {@code >} (and {@code "} inside attributes). */
    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String link(String url, String text) {
        return "<a href=\"" + escape(url).replace("\"", "&quot;") + "\">" + escape(oneLine(text)) + "</a>";
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    private static String shorten(String text) {
        return text.length() <= MAX_SUMMARY ? text : text.substring(0, MAX_SUMMARY - 1).stripTrailing() + "…";
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
