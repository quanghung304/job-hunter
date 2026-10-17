package io.github.quanghung304.jobhunter.llm.persistence;

import static io.github.quanghung304.jobhunter.PersistenceFixtures.T0;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.assertRejected;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.insertJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
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
        long id = calls.saveAndFlush(new LlmCall(LlmPurpose.SCORE, jobId, "claude-haiku-5-5", "score-v1",
                3_210, 412, new BigDecimal("0.004862"), 1_830, true, null, T0)).getId();
        em.clear();

        var loaded = calls.findById(id).orElseThrow();

        assertThat(loaded.getPurpose()).isEqualTo(LlmPurpose.SCORE);
        assertThat(loaded.getJobId()).isEqualTo(jobId);
        assertThat(loaded.getModel()).isEqualTo("claude-haiku-5-5");
        assertThat(loaded.getPromptVersion()).isEqualTo("score-v1");
        assertThat(loaded.getInputTokens()).isEqualTo(3_210);
        assertThat(loaded.getOutputTokens()).isEqualTo(412);
        assertThat(loaded.getCostUsd()).isEqualByComparingTo("0.004862");
        assertThat(loaded.getLatencyMs()).isEqualTo(1_830);
        assertThat(loaded.isSuccess()).isTrue();
        assertThat(loaded.getError()).isNull();
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void evalCallWithoutJobAndFailure() {
        long id = calls.saveAndFlush(new LlmCall(LlmPurpose.EVAL, null, "claude-haiku-5-5", "extract-v1",
                0, 0, BigDecimal.ZERO, null, false, "timeout after 60s", T0)).getId();
        em.clear();

        var loaded = calls.findById(id).orElseThrow();

        assertThat(loaded.getJobId()).isNull();
        assertThat(loaded.getLatencyMs()).isNull();
        assertThat(loaded.isSuccess()).isFalse();
        assertThat(loaded.getError()).isEqualTo("timeout after 60s");
    }

    @Test
    void costIsRoundedToSixDecimals() {
        long id = calls.saveAndFlush(new LlmCall(LlmPurpose.EXTRACT, null, "m", "extract-v1",
                1, 1, new BigDecimal("0.0000014"), 1, true, null, T0)).getId();
        em.clear();

        assertThat(calls.findById(id).orElseThrow().getCostUsd()).isEqualByComparingTo("0.000001");
    }

    @Test
    void purposeIsCheckedByTheDatabase() {
        assertRejected(em.getEntityManager(), "check", """
                insert into llm_call (purpose, model, prompt_version, success, created_at)
                values ('CHAT', 'm', 'p', true, now())""");
    }
}
