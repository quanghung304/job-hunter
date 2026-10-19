package io.github.quanghung304.jobhunter.llm;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PersistenceFixtures;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCall;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** Admin LLM usage statistics over {@code llm_call}, grouped by local day. */
@PostgresJpaTest
@Import({LlmRouterTestConfig.class, LlmUsageService.class})
class LlmUsageServiceIT {

    static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Autowired
    LlmUsageService usage;

    @Autowired
    LlmCallRepository calls;

    @Autowired
    MutableClock clock;

    @Autowired
    TestEntityManager em;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-10-17T12:00:00Z"));
    }

    @Test
    void groupsCallsByLocalDayAndFillsEmptyDays() {
        // 22:30 UTC on the 16th is 00:30 on the 17th in Rome (CEST, UTC+2)
        call(Instant.parse("2026-10-16T22:30:00Z"), ModelTier.FREE, null, 100, 10);
        call(Instant.parse("2026-10-17T08:00:00Z"), ModelTier.PAID, new BigDecimal("0.0100"), 200, 20);
        call(Instant.parse("2026-10-16T21:30:00Z"), ModelTier.FREE, null, 50, 5);
        call(Instant.parse("2026-10-01T08:00:00Z"), ModelTier.PAID, new BigDecimal("0.5000"), 1, 1);

        LlmUsage result = usage.usage(ROME, 3);

        assertThat(result.daily()).extracting(LlmUsage.Day::date).containsExactly(
                LocalDate.parse("2026-10-15"), LocalDate.parse("2026-10-16"), LocalDate.parse("2026-10-17"));
        assertThat(result.daily().get(0).calls()).isZero();
        assertThat(result.daily().get(1).calls()).isEqualTo(1);
        LlmUsage.Day today = result.daily().get(2);
        assertThat(today.calls()).isEqualTo(2);
        assertThat(today.inputTokens()).isEqualTo(300);
        assertThat(today.outputTokens()).isEqualTo(30);
        assertThat(today.costUsd()).isEqualByComparingTo("0.01");
        assertThat(result.callsToday()).isEqualTo(2);
        assertThat(result.monthToDateUsd()).as("PAID cost of the UTC month").isEqualByComparingTo("0.51");
        assertThat(result.monthlyBudgetUsd()).isEqualByComparingTo(LlmRouterTestConfig.MONTHLY_BUDGET);
    }

    @Test
    void lastErrorPerJobIsTheNewestFailedCallOfThePurpose() {
        long a = PersistenceFixtures.insertJob(em.getEntityManager(), "fp-a");
        long b = PersistenceFixtures.insertJob(em.getEntityManager(), "fp-b");
        failed(a, LlmPurpose.EXTRACT, Instant.parse("2026-10-17T08:00:00Z"), "old");
        failed(a, LlmPurpose.EXTRACT, Instant.parse("2026-10-17T09:00:00Z"), "new");
        failed(a, LlmPurpose.SCORE, Instant.parse("2026-10-17T10:00:00Z"), "score error");
        failed(b, LlmPurpose.SCORE, Instant.parse("2026-10-17T10:00:00Z"), "other job");

        assertThat(usage.lastErrors(LlmPurpose.EXTRACT, List.of(a, b)))
                .containsExactlyEntriesOf(java.util.Map.of(a, "INVALID_OUTPUT: new"));
        assertThat(usage.lastErrors(LlmPurpose.SCORE, List.of())).isEmpty();
    }

    private void call(Instant at, ModelTier tier, BigDecimal cost, int in, int out) {
        calls.save(LlmCall.builder().purpose(LlmPurpose.EXTRACT).model("primary", "p", tier)
                .promptVersion("extract-v1").tokens(in, out).costUsd(cost).success(true).createdAt(at).build());
    }

    private void failed(long jobId, LlmPurpose purpose, Instant at, String error) {
        calls.save(LlmCall.builder().purpose(purpose).jobId(jobId).model("primary", "p", ModelTier.FREE)
                .promptVersion("v").tokens(1, 1).failure(200, LlmErrorKind.INVALID_OUTPUT, error).createdAt(at)
                .build());
    }
}
