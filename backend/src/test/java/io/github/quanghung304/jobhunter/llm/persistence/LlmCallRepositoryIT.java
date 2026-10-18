package io.github.quanghung304.jobhunter.llm.persistence;

import static io.github.quanghung304.jobhunter.PersistenceFixtures.T0;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.assertRejected;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.insertJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresJpaTest
class LlmCallRepositoryIT {

    @Autowired
    LlmCallRepository calls;

    @Autowired
    TestEntityManager em;

    @Test
    void roundTrip() {
        long jobId = insertJob(em.getEntityManager(), "fp-llm");
        long id = calls.saveAndFlush(LlmCall.builder()
                .purpose(LlmPurpose.SCORE)
                .jobId(jobId)
                .model("claude-haiku", "claude-haiku-5-5", ModelTier.PAID)
                .promptVersion("score-v1")
                .tokens(3_210, 412)
                .costUsd(new BigDecimal("0.004862"))
                .latencyMs(1_830)
                .httpStatus(200)
                .success(true)
                .createdAt(T0)
                .build()).getId();
        em.clear();

        var loaded = calls.findById(id).orElseThrow();

        assertThat(loaded.getPurpose()).isEqualTo(LlmPurpose.SCORE);
        assertThat(loaded.getJobId()).isEqualTo(jobId);
        assertThat(loaded.getModelId()).isEqualTo("claude-haiku");
        assertThat(loaded.getProviderModel()).isEqualTo("claude-haiku-5-5");
        assertThat(loaded.getTier()).isEqualTo(ModelTier.PAID);
        assertThat(loaded.getPromptVersion()).isEqualTo("score-v1");
        assertThat(loaded.getInputTokens()).isEqualTo(3_210);
        assertThat(loaded.getOutputTokens()).isEqualTo(412);
        assertThat(loaded.getCostUsd()).isEqualByComparingTo("0.004862");
        assertThat(loaded.getLatencyMs()).isEqualTo(1_830);
        assertThat(loaded.getHttpStatus()).isEqualTo(200);
        assertThat(loaded.isSuccess()).isTrue();
        assertThat(loaded.getErrorKind()).isNull();
        assertThat(loaded.getError()).isNull();
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void failedFreeCallWithoutJobHasNoCost() {
        long id = calls.saveAndFlush(LlmCall.builder()
                .purpose(LlmPurpose.TEST)
                .model("groq-gpt-oss-120b", "openai/gpt-oss-120b", ModelTier.FREE)
                .promptVersion("extract-v1")
                .failure(429, LlmErrorKind.QUOTA_EXHAUSTED, "HTTP 429: requests per day (RPD)")
                .createdAt(T0)
                .build()).getId();
        em.clear();

        var loaded = calls.findById(id).orElseThrow();

        assertThat(loaded.getJobId()).isNull();
        assertThat(loaded.getLatencyMs()).isNull();
        assertThat(loaded.getCostUsd()).isNull();
        assertThat(loaded.isSuccess()).isFalse();
        assertThat(loaded.getHttpStatus()).isEqualTo(429);
        assertThat(loaded.getErrorKind()).isEqualTo(LlmErrorKind.QUOTA_EXHAUSTED);
        assertThat(loaded.getError()).isEqualTo("HTTP 429: requests per day (RPD)");
    }

    @Test
    void costIsRoundedToSixDecimals() {
        long id = calls.saveAndFlush(LlmCall.builder()
                .purpose(LlmPurpose.EXTRACT)
                .model("m", "m", ModelTier.PAID)
                .promptVersion("extract-v1")
                .tokens(1, 1)
                .costUsd(new BigDecimal("0.0000014"))
                .success(true)
                .createdAt(T0)
                .build()).getId();
        em.clear();

        assertThat(calls.findById(id).orElseThrow().getCostUsd()).isEqualByComparingTo("0.000001");
    }

    @Test
    void countsAndSumsForQuotaAndBudget() {
        for (int i = 0; i < 3; i++) {
            calls.save(LlmCall.builder().purpose(LlmPurpose.EXTRACT).model("paid", "p", ModelTier.PAID)
                    .promptVersion("extract-v1").tokens(100, 20).costUsd(new BigDecimal("0.010000")).success(true)
                    .createdAt(T0.plusSeconds(i)).build());
        }
        calls.save(LlmCall.builder().purpose(LlmPurpose.EXTRACT).model("free", "f", ModelTier.FREE)
                .promptVersion("extract-v1").tokens(500, 50).success(true).createdAt(T0).build());
        calls.saveAndFlush(LlmCall.builder().purpose(LlmPurpose.EXTRACT).model("paid", "p", ModelTier.PAID)
                .promptVersion("extract-v1").tokens(100, 20).costUsd(new BigDecimal("0.500000")).success(true)
                .createdAt(T0.minusSeconds(1)).build());

        assertThat(calls.countByModelIdAndCreatedAtGreaterThanEqual("paid", T0)).isEqualTo(3);
        assertThat(calls.sumTokens("paid", T0)).isEqualTo(360);
        assertThat(calls.sumTokens("unknown", T0)).isZero();
        assertThat(calls.sumCost(ModelTier.PAID, T0)).isEqualByComparingTo("0.03");
        assertThat(calls.sumCost(ModelTier.FREE, T0)).isEqualByComparingTo("0");
    }

    @Test
    void purposeIsCheckedByTheDatabase() {
        assertRejected(em.getEntityManager(), "check", """
                insert into llm_call (purpose, model_id, provider_model, prompt_version, success, created_at)
                values ('CHAT', 'm', 'm', 'p', true, now())""");
    }

    @Test
    void tierIsCheckedByTheDatabase() {
        assertRejected(em.getEntityManager(), "check", """
                insert into llm_call (purpose, model_id, provider_model, tier, prompt_version, success, created_at)
                values ('TEST', 'm', 'm', 'GOLD', 'p', true, now())""");
    }

    @Test
    void errorKindIsCheckedByTheDatabase() {
        assertRejected(em.getEntityManager(), "check", """
                insert into llm_call (purpose, model_id, provider_model, prompt_version, success, error_kind, created_at)
                values ('TEST', 'm', 'm', 'p', false, 'OOPS', now())""");
    }
}
