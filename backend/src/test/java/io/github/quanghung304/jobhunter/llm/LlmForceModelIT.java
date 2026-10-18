package io.github.quanghung304.jobhunter.llm;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.TestcontainersConfiguration;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * The whole application with the committed {@code config/llm.yaml} and
 * {@code app.llm.force-model=fake}: starts and answers without any API key (T-010 acceptance).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "app.profile.path=../config/profile.example.yaml",
        "app.llm.force-model=fake"})
@Import(TestcontainersConfiguration.class)
@Transactional
class LlmForceModelIT {

    record Extraction(String remoteType, String confidence) {
    }

    @Autowired
    LlmRouter router;

    @Autowired
    LlmCallRepository calls;

    @Test
    void everyPurposeUsesTheFakeModel() {
        long before = calls.count();

        LlmOutcome<Extraction> outcome = router.call(LlmPurpose.EXTRACT, "extract-v1", Map.of("title", "Java Dev",
                "company", "Acme", "locationText", "Remote", "remoteHint", "REMOTE", "employmentHint", "Contract",
                "description", "Build things."), null, Extraction.class);

        assertThat(outcome).isInstanceOfSatisfying(LlmResult.class, result -> {
            assertThat(result.modelId()).isEqualTo("fake");
            assertThat(result.value()).isEqualTo(new Extraction("FULLY_REMOTE", "HIGH"));
        });
        assertThat(calls.count()).isEqualTo(before + 1);
    }
}
