package io.github.quanghung304.jobhunter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

class PromptsOnClasspathTest {

    @ParameterizedTest
    @ValueSource(strings = {"extract-v1", "score-v1"})
    void promptFileIsPackagedFromDocs(String id) throws Exception {
        var resource = new ClassPathResource("prompts/" + id + ".md");

        assertThat(resource.exists()).isTrue();
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        assertThat(content).startsWith("---").contains("id: " + id);
    }

    @Test
    void promptsReadmeIsNotPackaged() {
        assertThat(new ClassPathResource("prompts/README.md").exists()).isFalse();
    }
}
