package io.github.quanghung304.jobhunter.tracking.persistence;

import static io.github.quanghung304.jobhunter.PersistenceFixtures.T0;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.assertRejected;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.insertJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.tracking.TrackingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresJpaTest
class JobTrackingRepositoryIT {

    @Autowired
    JobTrackingRepository trackings;

    @Autowired
    JobTrackingEventRepository events;

    @Autowired
    TestEntityManager em;

    @Test
    void roundTripByCompositeKey() {
        long jobId = insertJob(em.getEntityManager(), "fp-track");
        var id = new JobTrackingId(jobId, "hugh");
        var tracking = new JobTracking(id, TrackingStatus.SHORTLISTED, T0);
        tracking.setNotes("Ask about contract length.\nRate: 45 €/h");
        trackings.saveAndFlush(tracking);
        em.clear();

        var loaded = trackings.findById(id).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getStatus()).isEqualTo(TrackingStatus.SHORTLISTED);
        assertThat(loaded.getNotes()).isEqualTo("Ask about contract length.\nRate: 45 €/h");
        assertThat(loaded.getUpdatedAt()).isEqualTo(T0);
        assertThat(trackings.findById(new JobTrackingId(jobId, "other"))).isEmpty();
    }

    @Test
    void statusUpdatesArePersisted() {
        long jobId = insertJob(em.getEntityManager(), "fp-update");
        var id = new JobTrackingId(jobId, "hugh");
        trackings.saveAndFlush(new JobTracking(id, TrackingStatus.NEW, T0));
        em.clear();
        var tracking = trackings.findById(id).orElseThrow();
        tracking.setStatus(TrackingStatus.APPLIED);
        tracking.setUpdatedAt(T0.plusSeconds(10));
        trackings.saveAndFlush(tracking);
        em.clear();

        assertThat(trackings.findById(id).orElseThrow().getStatus()).isEqualTo(TrackingStatus.APPLIED);
        assertThat(trackings.count()).isEqualTo(1);
    }

    @Test
    void jobAndProfileArePrimaryKey() {
        long jobId = insertJob(em.getEntityManager(), "fp-pk");
        trackings.saveAndFlush(new JobTracking(new JobTrackingId(jobId, "hugh"), TrackingStatus.NEW, T0));

        // save() would merge into the existing row, so the duplicate is inserted with SQL.
        assertRejected(em.getEntityManager(), "unique",
                "insert into job_tracking (job_id, profile_id, status, updated_at) values ("
                        + jobId + ", 'hugh', 'APPLIED', now())");
    }

    @Test
    void statusIsCheckedByTheDatabase() {
        long jobId = insertJob(em.getEntityManager(), "fp-check");

        assertRejected(em.getEntityManager(), "check",
                "insert into job_tracking (job_id, profile_id, status, updated_at) values ("
                        + jobId + ", 'hugh', 'ARCHIVED', now())");
    }

    @Test
    void eventsRoundTripInOrder() {
        long jobId = insertJob(em.getEntityManager(), "fp-events");
        events.save(new JobTrackingEvent(jobId, "hugh", TrackingStatus.SHORTLISTED, TrackingStatus.APPLIED,
                "Sent CV", T0.plusSeconds(60)));
        events.save(new JobTrackingEvent(jobId, "hugh", TrackingStatus.NEW, TrackingStatus.SHORTLISTED,
                null, T0));
        events.save(new JobTrackingEvent(jobId, "other", TrackingStatus.NEW, TrackingStatus.DISMISSED,
                null, T0));
        events.flush();
        em.clear();

        var history = events.findByJobIdAndProfileIdOrderByCreatedAtAscIdAsc(jobId, "hugh");

        assertThat(history).extracting(JobTrackingEvent::getToValue).containsExactly("SHORTLISTED", "APPLIED");
        var applied = history.get(1);
        assertThat(applied.getKind()).isEqualTo(io.github.quanghung304.jobhunter.tracking.TrackingEventKind.STATUS);
        assertThat(applied.getFromValue()).isEqualTo("SHORTLISTED");
        assertThat(applied.getNote()).isEqualTo("Sent CV");
        assertThat(applied.getCreatedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(history.get(0).getNote()).isNull();
    }
}
