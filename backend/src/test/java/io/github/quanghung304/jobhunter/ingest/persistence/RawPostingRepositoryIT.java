package io.github.quanghung304.jobhunter.ingest.persistence;

import static io.github.quanghung304.jobhunter.PersistenceFixtures.T0;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresJpaTest
class RawPostingRepositoryIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    RawPostingRepository postings;

    @Autowired
    JobRepository jobs;

    @Autowired
    TestEntityManager em;

    @Test
    void roundTripKeepsTheJsonPayloadIntact() throws Exception {
        JsonNode payload = JSON.readTree("""
                {"slug": "java-dev-123", "title": "Java Developer – Rome", "remote": true,
                 "tags": ["java", "spring"], "salary": {"min": 50000, "max": null},
                 "description": "<p>Ciao &amp; welcome</p>", "created_at": 1730000000}""");
        var job = jobs.saveAndFlush(new Job("fp", "Java Developer", "Acme", "acme", "text", T0, T0));
        var posting = new RawPosting("arbeitnow", "java-dev-123", "https://www.arbeitnow.com/jobs/java-dev-123",
                payload, "hash-1", T0, T0.plusSeconds(60));
        posting.setJobId(job.getId());
        long id = postings.saveAndFlush(posting).getId();
        em.clear();

        var loaded = postings.findById(id).orElseThrow();

        assertThat(loaded.getSourceCode()).isEqualTo("arbeitnow");
        assertThat(loaded.getExternalId()).isEqualTo("java-dev-123");
        assertThat(loaded.getUrl()).isEqualTo("https://www.arbeitnow.com/jobs/java-dev-123");
        assertThat(loaded.getPayload()).isEqualTo(payload);
        assertThat(loaded.getPayload().at("/salary/max").isNull()).isTrue();
        assertThat(loaded.getPayloadHash()).isEqualTo("hash-1");
        assertThat(loaded.getFirstSeenAt()).isEqualTo(T0);
        assertThat(loaded.getLastSeenAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(loaded.getJobId()).isEqualTo(job.getId());
        assertThat(postings.findByJobId(job.getId())).hasSize(1);
        assertThat(postings.findBySourceCodeAndExternalId("arbeitnow", "java-dev-123")).isPresent();
    }

    @Test
    void storesPayloadAsJsonb() {
        postings.saveAndFlush(posting("himalayas", "1"));

        Object type = em.getEntityManager()
                .createNativeQuery("select jsonb_typeof(payload -> 'n') from raw_posting where external_id = '1'")
                .getSingleResult();

        assertThat(type).isEqualTo("number");
    }

    @Test
    void sourceAndExternalIdAreUnique() {
        postings.saveAndFlush(posting("jobicy", "42"));

        assertRejected("unique", () -> postings.saveAndFlush(posting("jobicy", "42")));
    }

    @Test
    void sameExternalIdFromAnotherSourceIsAllowed() {
        postings.saveAndFlush(posting("jobicy", "42"));
        postings.saveAndFlush(posting("himalayas", "42"));

        assertThat(postings.count()).isEqualTo(2);
    }

    @Test
    void jobIdMustReferenceAJob() {
        var posting = posting("jobicy", "orphan");
        posting.setJobId(999_999L);

        assertRejected("foreign key", () -> postings.saveAndFlush(posting));
    }

    private static RawPosting posting(String source, String externalId) {
        return new RawPosting(source, externalId, "https://example.com/" + externalId,
                JSON.createObjectNode().put("n", 1), "h", T0, T0);
    }
}
