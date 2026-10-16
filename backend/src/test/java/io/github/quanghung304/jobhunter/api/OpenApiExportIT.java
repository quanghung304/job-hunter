package io.github.quanghung304.jobhunter.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.quanghung304.jobhunter.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Keeps {@code docs/architecture/openapi.json} in sync with the code. Fails when the committed
 * file is stale; {@code -Dapi.export=true} (used by {@code make api}) rewrites it instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiExportIT {

    private static final Path CONTRACT = Path.of("..", "docs", "architecture", "openapi.json");

    @Autowired
    MockMvc mvc;

    @Test
    void contractFileMatchesTheRunningApi() throws Exception {
        String raw = mvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String actual = canonical(raw);

        if (Boolean.getBoolean("api.export")) {
            Files.writeString(CONTRACT, actual, StandardCharsets.UTF_8);
            return;
        }
        assertThat(CONTRACT)
                .as("%s is missing; run `make api`", CONTRACT)
                .exists();
        String committed = Files.readString(CONTRACT, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(committed)
                .as("%s is out of date; run `make api` and commit the result", CONTRACT)
                .isEqualTo(actual);
    }

    /** Sorted keys, pretty-printed, LF line endings, trailing newline: stable diffs. */
    private static String canonical(String json) throws Exception {
        var mapper = new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        Object tree = mapper.readValue(json, Object.class);
        return mapper.writeValueAsString(tree).replace("\r\n", "\n") + "\n";
    }
}
