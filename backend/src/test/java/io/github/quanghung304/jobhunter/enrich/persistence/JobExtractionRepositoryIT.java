package io.github.quanghung304.jobhunter.enrich.persistence;

import static io.github.quanghung304.jobhunter.PersistenceFixtures.T0;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.assertRejected;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.insertJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.enrich.ExtractionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresJpaTest
class JobExtractionRepositoryIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    JobExtractionRepository extractions;

    @Autowired
    TestEntityManager em;

    @Test
    void roundTripKeepsTheAttributesIntact() throws Exception {
        long jobId = insertJob(em.getEntityManager(), "fp-ext");
        JsonNode attributes = JSON.readTree("""
                {"remoteType": "REMOTE_RESTRICTED", "allowedRegions": ["EU", "IT"],
                 "timezoneRequirement": {"minUtcOffset": 0, "maxUtcOffset": 4, "overlapHours": null},
                 "employmentTypes": ["CONTRACT"], "requiredLanguages": [{"code": "de", "level": "C1"}],
                 "isScamSignal": false, "confidence": "HIGH"}""");
        var extraction = new JobExtraction(jobId, "extract-v1", ExtractionStatus.OK, T0);
        extraction.setModel("claude-haiku-5-5");
        extraction.setAttributes(attributes);
        extraction.setAttempts(1);
        long id = extractions.saveAndFlush(extraction).getId();
        em.clear();

        var loaded = extractions.findById(id).orElseThrow();

        assertThat(loaded.getJobId()).isEqualTo(jobId);
        assertThat(loaded.getPromptVersion()).isEqualTo("extract-v1");
        assertThat(loaded.getModel()).isEqualTo("claude-haiku-5-5");
        assertThat(loaded.getAttributes()).isEqualTo(attributes);
        assertThat(loaded.getStatus()).isEqualTo(ExtractionStatus.OK);
        assertThat(loaded.getAttempts()).isEqualTo(1);
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
        assertThat(extractions.findByJobIdAndPromptVersion(jobId, "extract-v1")).isPresent();
    }

    @Test
    void failedExtractionHasNoAttributes() {
        long jobId = insertJob(em.getEntityManager(), "fp-failed");
        var failed = new JobExtraction(jobId, "extract-v1", ExtractionStatus.FAILED, T0);
        failed.setAttempts(3);
        long id = extractions.saveAndFlush(failed).getId();
        em.clear();

        assertThat(extractions.findById(id).orElseThrow().getAttributes()).isNull();
    }

    @Test
    void jobAndPromptVersionAreUnique() {
        long jobId = insertJob(em.getEntityManager(), "fp-dup");
        extractions.saveAndFlush(new JobExtraction(jobId, "extract-v1", ExtractionStatus.OK, T0));
        extractions.saveAndFlush(new JobExtraction(jobId, "extract-v2", ExtractionStatus.OK, T0));

        assertRejected("unique",
                () -> extractions.saveAndFlush(new JobExtraction(jobId, "extract-v1", ExtractionStatus.FAILED, T0)));
    }

    @Test
    void jobMustExist() {
        assertRejected("foreign key",
                () -> extractions.saveAndFlush(new JobExtraction(999_999L, "extract-v1", ExtractionStatus.OK, T0)));
    }
}
