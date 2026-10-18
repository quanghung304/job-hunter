package io.github.quanghung304.jobhunter.notify.persistence;

import static io.github.quanghung304.jobhunter.PersistenceFixtures.T0;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.notify.NotificationChannel;
import io.github.quanghung304.jobhunter.notify.NotificationKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresJpaTest
class NotificationRepositoryIT {

    @Autowired
    NotificationRepository notifications;

    @Autowired
    TestEntityManager em;

    @Test
    void roundTripKeepsTheJobIdArray() {
        long id = notifications.saveAndFlush(new Notification("hugh", NotificationChannel.TELEGRAM,
                NotificationKind.DIGEST, List.of(3L, 1L, 2L), T0, true, null)).getId();
        em.clear();

        var loaded = notifications.findById(id).orElseThrow();

        assertThat(loaded.getProfileId()).isEqualTo("hugh");
        assertThat(loaded.getChannel()).isEqualTo(NotificationChannel.TELEGRAM);
        assertThat(loaded.getKind()).isEqualTo(NotificationKind.DIGEST);
        assertThat(loaded.getJobIds()).containsExactly(3L, 1L, 2L);
        assertThat(loaded.getSentAt()).isEqualTo(T0);
        assertThat(loaded.isSuccess()).isTrue();
        assertThat(loaded.getError()).isNull();
    }

    @Test
    void failedSendKeepsTheError() {
        long id = notifications.saveAndFlush(new Notification("hugh", NotificationChannel.TELEGRAM,
                NotificationKind.DIGEST, List.of(7L), T0, false, "429 Too Many Requests")).getId();
        em.clear();

        var loaded = notifications.findById(id).orElseThrow();

        assertThat(loaded.isSuccess()).isFalse();
        assertThat(loaded.getError()).isEqualTo("429 Too Many Requests");
    }

    @Test
    void findsNotificationsContainingAJobPerProfile() {
        notifications.save(new Notification("hugh", NotificationChannel.TELEGRAM, NotificationKind.DIGEST,
                List.of(10L, 11L), T0, true, null));
        notifications.save(new Notification("other", NotificationChannel.TELEGRAM, NotificationKind.DIGEST,
                List.of(12L), T0, true, null));
        notifications.flush();

        assertThat(notifications.findByProfileIdContainingJob("hugh", 11L)).hasSize(1);
        assertThat(notifications.findByProfileIdContainingJob("hugh", 12L)).isEmpty();
        assertThat(notifications.findByProfileIdContainingJob("other", 12L)).hasSize(1);
    }

    @Test
    void kindIsCheckedByTheDatabase() {
        assertRejected(em.getEntityManager(), "check", """
                insert into notification (profile_id, channel, kind, sent_at, success)
                values ('hugh', 'TELEGRAM', 'WEEKLY', now(), true)""");
    }

    @Test
    void instantAlertKindWasRemoved() {
        assertRejected(em.getEntityManager(), "check", """
                insert into notification (profile_id, channel, kind, sent_at, success)
                values ('hugh', 'TELEGRAM', 'ALERT', now(), true)""");
    }
}
