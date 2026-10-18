package io.github.quanghung304.jobhunter.llm.persistence;

import static io.github.quanghung304.jobhunter.PersistenceFixtures.T0;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.AssignmentChangeReason;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/** Round trips and constraints of llm_assignment, llm_assignment_event and llm_model_state (V6). */
@PostgresJpaTest
class LlmAssignmentRepositoryIT {

    @Autowired
    LlmAssignmentRepository assignments;

    @Autowired
    LlmAssignmentEventRepository events;

    @Autowired
    LlmModelStateRepository states;

    @Autowired
    TestEntityManager em;

    @Test
    void assignmentRoundTrip() {
        assignments.saveAndFlush(new LlmAssignment(LlmPurpose.EXTRACT, "groq-gpt-oss-120b",
                List.of("openrouter-nemotron-super", "claude-haiku"), true, false, T0, LlmAssignment.BY_USER));
        em.clear();

        LlmAssignment loaded = assignments.findById(LlmPurpose.EXTRACT).orElseThrow();

        assertThat(loaded.getModelId()).isEqualTo("groq-gpt-oss-120b");
        assertThat(loaded.getFallbacks()).containsExactly("openrouter-nemotron-super", "claude-haiku");
        assertThat(loaded.isAutoFallback()).isTrue();
        assertThat(loaded.isAllowPaid()).isFalse();
        assertThat(loaded.getUpdatedAt()).isEqualTo(T0);
        assertThat(loaded.getUpdatedBy()).isEqualTo("user");
    }

    @Test
    void eventsAreReadInOrder() {
        events.save(new LlmAssignmentEvent(LlmPurpose.SCORE, "a", "b", AssignmentChangeReason.AUTO_FALLBACK, T0));
        events.save(new LlmAssignmentEvent(LlmPurpose.SCORE, "b", "a", AssignmentChangeReason.RESTORED, T0.plusSeconds(1)));
        events.saveAndFlush(new LlmAssignmentEvent(LlmPurpose.EXTRACT, null, "a", AssignmentChangeReason.USER, T0));
        em.clear();

        assertThat(events.findByPurposeOrderByIdAsc(LlmPurpose.SCORE))
                .extracting(LlmAssignmentEvent::getReason)
                .containsExactly(AssignmentChangeReason.AUTO_FALLBACK, AssignmentChangeReason.RESTORED);
        assertThat(events.findByPurposeOrderByIdAsc(LlmPurpose.EXTRACT).get(0).getFromModelId()).isNull();
    }

    @Test
    void modelStateRoundTrip() {
        LlmModelState state = new LlmModelState("groq-gpt-oss-120b", ModelStatus.EXHAUSTED, T0);
        state.setUntil(T0.plusSeconds(3600));
        state.setDetail("Daily request limit 1000/1000");
        state.setRemainingRequests(0);
        state.setRemainingTokens(1200);
        states.saveAndFlush(state);
        em.clear();

        LlmModelState loaded = states.findById("groq-gpt-oss-120b").orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(ModelStatus.EXHAUSTED);
        assertThat(loaded.getUntil()).isEqualTo(T0.plusSeconds(3600));
        assertThat(loaded.getDetail()).isEqualTo("Daily request limit 1000/1000");
        assertThat(loaded.getRemainingRequests()).isZero();
        assertThat(loaded.getRemainingTokens()).isEqualTo(1200);
    }

    @Test
    void assignmentPurposeIsChecked() {
        assertRejected(em.getEntityManager(), "check", """
                insert into llm_assignment (purpose, model_id, updated_at, updated_by)
                values ('EVAL', 'm', now(), 'user')""");
    }

    @Test
    void assignmentUpdatedByIsChecked() {
        assertRejected(em.getEntityManager(), "check", """
                insert into llm_assignment (purpose, model_id, updated_at, updated_by)
                values ('SCORE', 'm', now(), 'robot')""");
    }

    @Test
    void eventReasonIsChecked() {
        assertRejected(em.getEntityManager(), "check", """
                insert into llm_assignment_event (purpose, to_model_id, reason, created_at)
                values ('SCORE', 'm', 'WHIM', now())""");
    }

    @Test
    void modelStatusIsChecked() {
        assertRejected(em.getEntityManager(), "check", """
                insert into llm_model_state (model_id, status, updated_at) values ('m', 'BROKEN', now())""");
    }
}
