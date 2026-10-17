package io.github.quanghung304.jobhunter.source.persistence;

import static io.github.quanghung304.jobhunter.PersistenceFixtures.T0;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresJpaTest
class CollectorRunRepositoryIT {

    @Autowired
    CollectorRunRepository runs;

    @Autowired
    TestEntityManager em;

    @Test
    void roundTrip() {
        var run = new CollectorRun("arbeitnow", T0);
        run.setFinishedAt(T0.plusSeconds(42));
        run.setStatus(CollectStatus.PARTIAL);
        run.setFetchedCount(120);
        run.setNewRawCount(15);
        run.setNewJobCount(9);
        run.setErrorMessage("2 items skipped");
        long id = runs.saveAndFlush(run).getId();
        em.clear();

        var loaded = runs.findById(id).orElseThrow();

        assertThat(loaded.getSourceCode()).isEqualTo("arbeitnow");
        assertThat(loaded.getStartedAt()).isEqualTo(T0);
        assertThat(loaded.getFinishedAt()).isEqualTo(T0.plusSeconds(42));
        assertThat(loaded.getStatus()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(loaded.getFetchedCount()).isEqualTo(120);
        assertThat(loaded.getNewRawCount()).isEqualTo(15);
        assertThat(loaded.getNewJobCount()).isEqualTo(9);
        assertThat(loaded.getErrorMessage()).isEqualTo("2 items skipped");
    }

    @Test
    void runningRunHasNoStatusYet() {
        long id = runs.saveAndFlush(new CollectorRun("jobicy", T0)).getId();
        em.clear();

        var loaded = runs.findById(id).orElseThrow();

        assertThat(loaded.getStatus()).isNull();
        assertThat(loaded.getFinishedAt()).isNull();
        assertThat(loaded.getFetchedCount()).isZero();
    }

    @Test
    void findsTheLatestRunOfASource() {
        runs.save(new CollectorRun("himalayas", T0));
        runs.save(new CollectorRun("himalayas", T0.plusSeconds(3600)));
        runs.save(new CollectorRun("jobicy", T0.plusSeconds(7200)));
        runs.flush();

        assertThat(runs.findFirstBySourceCodeOrderByStartedAtDesc("himalayas"))
                .get().extracting(CollectorRun::getStartedAt).isEqualTo(T0.plusSeconds(3600));
    }

    @Test
    void statusIsCheckedByTheDatabase() {
        assertRejected(em.getEntityManager(), "check",
                "insert into collector_run (source_code, started_at, status) values ('x', now(), 'DONE')");
    }
}
