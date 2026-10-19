package io.github.quanghung304.jobhunter.tracking;

import static io.github.quanghung304.jobhunter.tracking.BoardFixtures.HUGH;
import static io.github.quanghung304.jobhunter.tracking.BoardFixtures.OTHER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.ingest.JobNotFoundException;
import io.github.quanghung304.jobhunter.tracking.persistence.JobTrackingEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Status changes and notes per job and profile, against Postgres. */
@PostgresJpaTest
@Import(TrackingTestConfig.class)
class TrackingServiceIT {

    @Autowired
    TrackingService tracking;

    @Autowired
    JobTrackingEventRepository events;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    BoardFixtures db;
    long jobId;

    @BeforeEach
    void setUp() {
        db = new BoardFixtures(jdbc);
        jobId = db.job("Java Developer", "Acme", 1);
    }

    @Test
    void untrackedJobIsNewAndTheFirstChangeCreatesTheRowAndAnEvent() {
        assertThat(tracking.status(jobId, HUGH)).isEqualTo(TrackingStatus.NEW);

        tracking.changeStatus(jobId, HUGH, TrackingStatus.SHORTLISTED, "looks good");
        tracking.changeStatus(jobId, HUGH, TrackingStatus.APPLIED, "  ");

        assertThat(tracking.status(jobId, HUGH)).isEqualTo(TrackingStatus.APPLIED);
        assertThat(tracking.history(jobId, HUGH))
                .extracting(StatusChange::from, StatusChange::to, StatusChange::note)
                .containsExactly(
                        tuple("NEW", "SHORTLISTED", "looks good"),
                        tuple("SHORTLISTED", "APPLIED", null));
        assertThat(tracking.history(jobId, HUGH)).allSatisfy(change -> assertThat(change.at()).isEqualTo(BoardFixtures.NOW));
    }

    @Test
    void illegalTransitionChangesNothing() {
        tracking.changeStatus(jobId, HUGH, TrackingStatus.DISMISSED, null);

        assertThatThrownBy(() -> tracking.changeStatus(jobId, HUGH, TrackingStatus.APPLIED, null))
                .isInstanceOf(IllegalStatusTransitionException.class)
                .hasMessage("Cannot change status from DISMISSED to APPLIED");
        assertThatThrownBy(() -> tracking.changeStatus(jobId, HUGH, TrackingStatus.DISMISSED, null))
                .as("same status is not a transition")
                .isInstanceOf(IllegalStatusTransitionException.class);

        assertThat(tracking.status(jobId, HUGH)).isEqualTo(TrackingStatus.DISMISSED);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void unknownJobIsNotFound() {
        assertThatThrownBy(() -> tracking.changeStatus(424_242L, HUGH, TrackingStatus.SHORTLISTED, null))
                .isInstanceOf(JobNotFoundException.class);
        assertThatThrownBy(() -> tracking.updateNotes(424_242L, HUGH, "x"))
                .isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void notesAreKeptPerProfileAndEditableInATerminalStatus() {
        tracking.updateNotes(jobId, HUGH, "Call recruiter");
        assertThat(tracking.status(jobId, HUGH)).as("notes do not change the status").isEqualTo(TrackingStatus.NEW);
        assertThat(tracking.notes(jobId, HUGH)).contains("Call recruiter");
        assertThat(tracking.notes(jobId, OTHER)).isEmpty();

        tracking.changeStatus(jobId, HUGH, TrackingStatus.APPLIED, null);
        tracking.changeStatus(jobId, HUGH, TrackingStatus.REJECTED, null);
        tracking.updateNotes(jobId, HUGH, "Rejected after the first call");
        assertThat(tracking.notes(jobId, HUGH)).contains("Rejected after the first call");

        tracking.updateNotes(jobId, HUGH, "");
        assertThat(tracking.notes(jobId, HUGH)).isEmpty();
    }

    @Test
    void statusesAreIndependentPerProfile() {
        tracking.changeStatus(jobId, OTHER, TrackingStatus.DISMISSED, null);

        assertThat(tracking.status(jobId, HUGH)).isEqualTo(TrackingStatus.NEW);
        tracking.changeStatus(jobId, HUGH, TrackingStatus.SHORTLISTED, null);
        assertThat(tracking.history(jobId, HUGH)).hasSize(1)
                .allSatisfy(change -> assertThat(change.kind()).isEqualTo(TrackingEventKind.STATUS));
        assertThat(tracking.history(jobId, OTHER)).extracting(StatusChange::to).containsExactly("DISMISSED");
    }

    @Test
    void eligibilityDecisionIsSetChangedAndClearedWithOneEventEach() {
        assertThat(tracking.decision(jobId, HUGH)).isEmpty();

        assertThat(tracking.setEligibilityDecision(jobId, HUGH, EligibilityDecision.ELIGIBLE, "fits")).isTrue();
        assertThat(tracking.setEligibilityDecision(jobId, HUGH, EligibilityDecision.ELIGIBLE, null))
                .as("same value: no-op").isFalse();
        assertThat(tracking.decidedAt(jobId, HUGH)).contains(BoardFixtures.NOW);
        tracking.setEligibilityDecision(jobId, HUGH, EligibilityDecision.INELIGIBLE, null);
        tracking.setEligibilityDecision(jobId, HUGH, null, null);

        assertThat(tracking.decision(jobId, HUGH)).isEmpty();
        assertThat(tracking.decidedAt(jobId, HUGH)).isEmpty();
        assertThat(tracking.history(jobId, HUGH))
                .extracting(StatusChange::kind, StatusChange::from, StatusChange::to, StatusChange::note)
                .containsExactly(
                        tuple(TrackingEventKind.ELIGIBILITY, "UNDECIDED", "ELIGIBLE", "fits"),
                        tuple(TrackingEventKind.ELIGIBILITY, "ELIGIBLE", "INELIGIBLE", null),
                        tuple(TrackingEventKind.ELIGIBILITY, "INELIGIBLE", "UNDECIDED", null));
        assertThat(tracking.status(jobId, HUGH)).as("independent of the status").isEqualTo(TrackingStatus.NEW);
    }

    @Test
    void dismissingKeepsTheDecisionAndDecisionsArePerProfile() {
        tracking.setEligibilityDecision(jobId, HUGH, EligibilityDecision.INELIGIBLE, null);
        tracking.changeStatus(jobId, HUGH, TrackingStatus.DISMISSED, null);

        assertThat(tracking.decision(jobId, HUGH)).contains(EligibilityDecision.INELIGIBLE);
        assertThat(tracking.decision(jobId, OTHER)).isEmpty();
        assertThat(tracking.decisions(java.util.List.of(jobId, 424_242L), HUGH))
                .containsExactly(java.util.Map.entry(jobId, EligibilityDecision.INELIGIBLE));
        assertThatThrownBy(() -> tracking.setEligibilityDecision(424_242L, HUGH, EligibilityDecision.ELIGIBLE, null))
                .isInstanceOf(JobNotFoundException.class);
    }
}
