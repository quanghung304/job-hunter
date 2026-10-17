package io.github.quanghung304.jobhunter.ingest.persistence;

import static io.github.quanghung304.jobhunter.PersistenceFixtures.T0;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresJpaTest
class JobRepositoryIT {

    @Autowired
    JobRepository jobs;

    @Autowired
    TestEntityManager em;

    @Test
    void roundTrip() {
        var job = newJob("fp-1");
        job.setLocationText("Remote (EU)");
        job.setRemoteHint(RemoteHint.REMOTE);
        job.setEmploymentHint("full_time");
        job.setDescriptionHash("abc123");
        job.setApplyUrl("https://boards.greenhouse.io/acme/jobs/1");
        job.setSalaryMin(new BigDecimal("50000.50"));
        job.setSalaryMax(new BigDecimal("70000"));
        job.setSalaryCurrency("EUR");
        job.setSalaryPeriod(SalaryPeriod.YEAR);
        job.setTags(List.of("java", "spring boot", "café"));
        job.setPostedAt(T0.minusSeconds(86_400));
        long id = jobs.saveAndFlush(job).getId();
        em.clear();

        var loaded = jobs.findById(id).orElseThrow();

        assertThat(loaded.getFingerprint()).isEqualTo("fp-1");
        assertThat(loaded.getTitle()).isEqualTo("Backend Engineer");
        assertThat(loaded.getCompanyName()).isEqualTo("Acme, Inc.");
        assertThat(loaded.getCompanyKey()).isEqualTo("acme");
        assertThat(loaded.getLocationText()).isEqualTo("Remote (EU)");
        assertThat(loaded.getRemoteHint()).isEqualTo(RemoteHint.REMOTE);
        assertThat(loaded.getEmploymentHint()).isEqualTo("full_time");
        assertThat(loaded.getDescriptionText()).isEqualTo("We build things.\nWith Java.");
        assertThat(loaded.getDescriptionHash()).isEqualTo("abc123");
        assertThat(loaded.getApplyUrl()).isEqualTo("https://boards.greenhouse.io/acme/jobs/1");
        assertThat(loaded.getSalaryMin()).isEqualByComparingTo("50000.50");
        assertThat(loaded.getSalaryMax()).isEqualByComparingTo("70000");
        assertThat(loaded.getSalaryCurrency()).isEqualTo("EUR");
        assertThat(loaded.getSalaryPeriod()).isEqualTo(SalaryPeriod.YEAR);
        assertThat(loaded.getTags()).containsExactly("java", "spring boot", "café");
        assertThat(loaded.getPostedAt()).isEqualTo(T0.minusSeconds(86_400));
        assertThat(loaded.getFirstSeenAt()).isEqualTo(T0);
        assertThat(loaded.getLastSeenAt()).isEqualTo(T0);
        assertThat(loaded.getStatus()).isEqualTo(JobStatus.ACTIVE);
    }

    @Test
    void optionalFieldsMayBeEmpty() {
        long id = jobs.saveAndFlush(newJob("fp-min")).getId();
        em.clear();

        var loaded = jobs.findById(id).orElseThrow();

        assertThat(loaded.getTags()).isEmpty();
        assertThat(loaded.getRemoteHint()).isNull();
        assertThat(loaded.getSalaryPeriod()).isNull();
    }

    @Test
    void updatesArePersisted() {
        long id = jobs.saveAndFlush(newJob("fp-upd")).getId();
        em.clear();
        var job = jobs.findById(id).orElseThrow();
        job.setStatus(JobStatus.EXPIRED);
        job.setTags(List.of("kotlin"));
        jobs.saveAndFlush(job);
        em.clear();

        var loaded = jobs.findById(id).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(JobStatus.EXPIRED);
        assertThat(loaded.getTags()).containsExactly("kotlin");
    }

    @Test
    void findsByFingerprint() {
        jobs.saveAndFlush(newJob("fp-find"));

        assertThat(jobs.findByFingerprint("fp-find")).isPresent();
        assertThat(jobs.findByFingerprint("other")).isEmpty();
    }

    @Test
    void fingerprintIsUnique() {
        jobs.saveAndFlush(newJob("fp-dup"));

        assertRejected("unique", () -> jobs.saveAndFlush(newJob("fp-dup")));
    }

    @Test
    void enumColumnsAreCheckedByTheDatabase() {
        assertRejected(em.getEntityManager(), "check", """
                insert into job (fingerprint, title, company_name, company_key, description_text,
                                 first_seen_at, last_seen_at, status)
                values ('x', 't', 'c', 'c', 'd', now(), now(), 'DELETED')""");
        assertRejected(em.getEntityManager(), "check", """
                insert into job (fingerprint, title, company_name, company_key, description_text,
                                 first_seen_at, last_seen_at, salary_period)
                values ('y', 't', 'c', 'c', 'd', now(), now(), 'WEEK')""");
    }

    private static Job newJob(String fingerprint) {
        return new Job(fingerprint, "Backend Engineer", "Acme, Inc.", "acme", "We build things.\nWith Java.", T0, T0);
    }
}
