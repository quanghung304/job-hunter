package io.github.quanghung304.jobhunter.llm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * LLM usage for the admin page (api-spec {@code llmUsage}).
 *
 * @param monthToDateUsd PAID cost of the current UTC month (what the budget guard checks)
 * @param callsToday     {@code llm_call} rows since local midnight
 * @param daily          one entry per local day, oldest first, days without calls included
 */
public record LlmUsage(BigDecimal monthToDateUsd, BigDecimal monthlyBudgetUsd, long callsToday, List<Day> daily) {

    public LlmUsage {
        daily = List.copyOf(daily);
    }

    /** {@code costUsd} sums PAID calls only (FREE/LOCAL calls have no cost). */
    public record Day(LocalDate date, long calls, long inputTokens, long outputTokens, BigDecimal costUsd) {
    }
}
