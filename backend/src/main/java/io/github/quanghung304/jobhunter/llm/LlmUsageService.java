package io.github.quanghung304.jobhunter.llm;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only statistics over {@code llm_call} for the admin page; makes no LLM call. */
@Service
@Transactional(readOnly = true)
public class LlmUsageService {

    private final NamedParameterJdbcTemplate jdbc;
    private final BudgetGuard budget;
    private final LlmProperties properties;
    private final Clock clock;

    public LlmUsageService(NamedParameterJdbcTemplate jdbc, BudgetGuard budget, LlmProperties properties,
            Clock clock) {
        this.jdbc = jdbc;
        this.budget = budget;
        this.properties = properties;
        this.clock = clock;
    }

    /** Usage of the last {@code days} local days (today included) in {@code zone}. */
    public LlmUsage usage(ZoneId zone, int days) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        LocalDate first = today.minusDays(days - 1L);
        Instant since = first.atStartOfDay(zone).toInstant();
        Map<LocalDate, LlmUsage.Day> byDate = new HashMap<>();
        jdbc.query("""
                        select cast(c.created_at at time zone :zone as date) as day, count(*) as calls,
                               coalesce(sum(c.input_tokens), 0) as input_tokens,
                               coalesce(sum(c.output_tokens), 0) as output_tokens,
                               coalesce(sum(c.cost_usd), 0) as cost_usd
                        from llm_call c
                        where c.created_at >= :since
                        group by 1""",
                new MapSqlParameterSource().addValue("zone", zone.getId()).addValue("since", Timestamp.from(since)),
                rs -> {
                    LocalDate date = rs.getObject("day", LocalDate.class);
                    byDate.put(date, new LlmUsage.Day(date, rs.getLong("calls"), rs.getLong("input_tokens"),
                            rs.getLong("output_tokens"), rs.getBigDecimal("cost_usd")));
                });
        List<LlmUsage.Day> daily = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(today); date = date.plusDays(1)) {
            daily.add(byDate.getOrDefault(date, new LlmUsage.Day(date, 0, 0, 0, BigDecimal.ZERO)));
        }
        return new LlmUsage(budget.monthToDatePaidCost(), properties.monthlyBudgetUsd(),
                byDate.containsKey(today) ? byDate.get(today).calls() : 0, daily);
    }

    /** The error of the newest failed call of this purpose per job (admin "Failed LLM work"). */
    public Map<Long, String> lastErrors(LlmPurpose purpose, Collection<Long> jobIds) {
        Map<Long, String> result = new HashMap<>();
        if (jobIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                        select distinct on (c.job_id) c.job_id, c.error_kind, c.error
                        from llm_call c
                        where c.purpose = :purpose and c.success = false and c.job_id in (:jobIds)
                        order by c.job_id, c.created_at desc, c.id desc""",
                new MapSqlParameterSource().addValue("purpose", purpose.name()).addValue("jobIds", jobIds),
                rs -> {
                    String kind = rs.getString("error_kind");
                    String error = rs.getString("error");
                    result.put(rs.getLong("job_id"), kind == null ? error : error == null ? kind : kind + ": " + error);
                });
        return result;
    }
}
