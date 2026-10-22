package io.github.quanghung304.jobhunter.notify;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelStatus;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Telegram texts of the LLM quota alerts (T-022) in the profile's summary language, Vietnamese or
 * English; both templates live here. Telegram HTML; emoji only here, never in the web UI.
 */
final class LlmQuotaMessages {

    private static final Pattern COUNTS = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

    private LlmQuotaMessages() {
    }

    /** What to say about a model that serves {@code purposes}. */
    record Context(boolean vietnamese, String label, String detail, List<LlmPurpose> purposes, boolean paused,
            String resetsAt, String adminUrl) {
    }

    static String exhausted(Context c) {
        String counts = counts(c.detail());
        boolean daily = c.detail() != null && c.detail().toLowerCase(Locale.ROOT).contains("daily");
        var text = new StringBuilder("⚠️ <b>").append(esc(c.label())).append("</b> ");
        if (c.vietnamese()) {
            text.append("hết hạn mức").append(daily ? " hôm nay" : "").append(counts.isEmpty() ? "" : " (" + counts + ")")
                    .append('.');
            if (c.paused()) {
                text.append(" Nút ").append(actions(c.purposes(), true)).append(" tạm khoá.");
            }
            if (c.resetsAt() != null) {
                text.append(" Mở lại lúc ").append(c.resetsAt()).append('.');
            }
            text.append(" Đổi model: ").append(link(c.adminUrl(), "Admin"));
        } else {
            text.append("reached its limit").append(daily ? " today" : "")
                    .append(counts.isEmpty() ? "" : " (" + counts + ")").append('.');
            if (c.paused()) {
                text.append(' ').append(actions(c.purposes(), false)).append(" is paused.");
            }
            if (c.resetsAt() != null) {
                text.append(" Resets at ").append(c.resetsAt()).append('.');
            }
            text.append(" Switch model: ").append(link(c.adminUrl(), "Admin"));
        }
        return text.toString();
    }

    static String nearLimit(Context c) {
        Matcher m = c.detail() == null ? null : COUNTS.matcher(c.detail());
        String label = "⚠️ <b>" + esc(c.label()) + "</b> ";
        if (m != null && m.find() && Long.parseLong(m.group(2)) > 0) {
            long used = Long.parseLong(m.group(1));
            long limit = Long.parseLong(m.group(2));
            long percent = used * 100 / limit;
            return c.vietnamese()
                    ? label + "đã dùng " + percent + "% hạn mức ngày (" + used + "/" + limit + ")."
                    : label + "used " + percent + "% of its daily limit (" + used + "/" + limit + ").";
        }
        return c.vietnamese()
                ? label + "sắp hết hạn mức: " + esc(nullToEmpty(c.detail())) + "."
                : label + "is near its limit: " + esc(nullToEmpty(c.detail())) + ".";
    }

    static String error(Context c) {
        String label = "⚠️ <b>" + esc(c.label()) + "</b> ";
        String detail = esc(nullToEmpty(c.detail()));
        return c.vietnamese()
                ? label + "lỗi: " + detail + " (kiểm tra API key). Đổi model: " + link(c.adminUrl(), "Admin")
                : label + "error: " + detail + " (check the API key). Switch model: " + link(c.adminUrl(), "Admin");
    }

    static String recovered(Context c) {
        return c.vietnamese()
                ? "✅ <b>" + esc(c.label()) + "</b> dùng được trở lại."
                : "✅ <b>" + esc(c.label()) + "</b> works again.";
    }

    /** The router moved a purpose to a fallback model because the assigned one stopped working. */
    static String autoFallback(boolean vietnamese, LlmPurpose purpose, String fromLabel, ModelStatus fromStatus,
            String toLabel, String adminUrl) {
        String step = purpose == LlmPurpose.EXTRACT ? "Extraction" : "Scoring";
        String why = reason(fromStatus, vietnamese);
        return vietnamese
                ? "↪️ Đã tự chuyển " + step + " sang <b>" + esc(toLabel) + "</b> vì <b>" + esc(fromLabel) + "</b> "
                        + why + ". Chuyển lại: " + link(adminUrl, "Admin")
                : "↪️ Switched " + step + " to <b>" + esc(toLabel) + "</b> because <b>" + esc(fromLabel) + "</b> "
                        + why + ". Switch back: " + link(adminUrl, "Admin");
    }

    private static String reason(ModelStatus status, boolean vietnamese) {
        if (status == null) {
            return vietnamese ? "không dùng được" : "could not be used";
        }
        return switch (status) {
            case EXHAUSTED -> vietnamese ? "hết hạn mức" : "reached its limit";
            case RATE_LIMITED -> vietnamese ? "bị giới hạn tốc độ" : "is rate limited";
            case ERROR -> vietnamese ? "bị lỗi" : "has an error";
            default -> vietnamese ? "không dùng được" : "could not be used";
        };
    }

    /** "Analyze", "Score" or "Analyze và Score" / "Analyze and Score". */
    private static String actions(List<LlmPurpose> purposes, boolean vietnamese) {
        List<String> names = purposes.stream().map(p -> p == LlmPurpose.EXTRACT ? "Analyze" : "Score").toList();
        return String.join(vietnamese ? " và " : " and ", names);
    }

    /** "1000/1000 requests" from "Daily request limit 1000/1000"; the escaped detail when there are no counts. */
    private static String counts(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        Matcher m = COUNTS.matcher(detail);
        if (!m.find()) {
            return esc(detail);
        }
        String lower = detail.toLowerCase(Locale.ROOT);
        String unit = lower.contains("token") ? " tokens" : lower.contains("request") ? " requests" : "";
        return m.group(1) + "/" + m.group(2) + unit;
    }

    private static String link(String url, String text) {
        return "<a href=\"" + esc(url).replace("\"", "&quot;") + "\">" + esc(text) + "</a>";
    }

    private static String esc(String text) {
        return DigestBuilder.escape(text);
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
