package io.github.quanghung304.jobhunter.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.quanghung304.jobhunter.profile.ProfileChanged;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.profile.ProfileService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** {@code GET/PUT /api/profile} over a real {@link ProfileService} on a copy of the example profile. */
class ProfileControllerTest {

    @TempDir
    Path dir;

    Path file;
    MockMvc mvc;
    ObjectMapper json;

    @BeforeEach
    void setUp() throws Exception {
        file = dir.resolve("profile.yaml");
        Files.copy(ProfileFixtures.EXAMPLE, file);
        ProfileService service = ProfileFixtures.service(file, event -> {
            if (event instanceof ProfileChanged changed) {
                changed.reassessed(new ProfileChanged.Reassessment(30, 2, 13));
            }
        });
        // As Spring Boot configures it: ISO-8601 dates.
        json = Jackson2ObjectMapperBuilder.json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mvc = MockMvcBuilders.standaloneSetup(new ProfileController(service, json))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    /** The GET answer: {@code hash} and the editable {@code profile}. */
    private ObjectNode loaded() throws Exception {
        String body = mvc.perform(get("/api/profile")).andReturn().getResponse().getContentAsString();
        return (ObjectNode) json.readTree(body);
    }

    private String saveBody(ObjectNode loaded, Consumer<ObjectNode> change) throws Exception {
        ObjectNode profile = (ObjectNode) loaded.get("profile").deepCopy();
        change.accept(profile);
        ObjectNode body = json.createObjectNode();
        body.put("baseHash", loaded.get("hash").asText());
        body.set("profile", profile);
        return json.writeValueAsString(body);
    }

    @Test
    void getReturnsTheProfileWithTheKeysOfProfileYaml() throws Exception {
        mvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("hugh"))
                .andExpect(jsonPath("$.hash").value(ProfileFixtures.load(ProfileFixtures.EXAMPLE).profileHash()))
                .andExpect(jsonPath("$.savedAt").isString())
                .andExpect(jsonPath("$.profile.summaryLanguage").value("vi"))
                .andExpect(jsonPath("$.profile.candidate.timezone").value("Europe/Rome"))
                .andExpect(jsonPath("$.profile.candidate.languages[0].level").value("NATIVE"))
                .andExpect(jsonPath("$.profile.preferences.maxHoursPerWeek").isEmpty())
                .andExpect(jsonPath("$.profile.preferences.timezone.maxOffsetDiffHours").value(6))
                .andExpect(jsonPath("$.profile.preferences.minSalary.period").value("HOUR"))
                .andExpect(jsonPath("$.profile.prefilter.titleInclude[1]").value("\\bjava\\b"))
                .andExpect(jsonPath("$.profile.notify.digestMinScore").isNumber())
                .andExpect(jsonPath("$.profile.tracks").doesNotExist());
    }

    @Test
    void putSavesAndAnswersWithTheNewHashAndTheReassessedCounts() throws Exception {
        ObjectNode loaded = loaded();
        String body = saveBody(loaded, p -> ((ObjectNode) p.get("preferences")).put("maxHoursPerWeek", 30));

        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("hugh"))
                .andExpect(jsonPath("$.hash").value(org.hamcrest.Matchers.not(loaded.get("hash").asText())))
                .andExpect(jsonPath("$.reassessed.jobs").value(30))
                .andExpect(jsonPath("$.reassessed.prefilterChanged").value(2))
                .andExpect(jsonPath("$.reassessed.checksUpdated").value(13));

        assertThat(Files.readString(file)).contains("maxHoursPerWeek: 30", "subjects:");
        assertThat(dir.resolve("profile.yaml.bak")).exists();
        mvc.perform(get("/api/profile")).andExpect(jsonPath("$.profile.preferences.maxHoursPerWeek").value(30));
    }

    @Test
    void anInvalidProfileIs400WithEveryProblemByPath() throws Exception {
        String before = Files.readString(file);
        String body = saveBody(loaded(), p -> {
            ((ObjectNode) p.get("candidate")).put("headline", "");
            ((ObjectNode) p.get("preferences")).putArray("seniority");
        });

        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].path").value("candidate.headline"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[1].path").value("preferences.seniority"));
        assertThat(Files.readString(file)).isEqualTo(before);
    }

    @Test
    void anInvalidRegexIs400WithItsPath() throws Exception {
        String body = saveBody(loaded(), p -> ((ArrayNode) p.get("prefilter").get("titleInclude")).set(0, "(oops"));

        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].path").value("prefilter.titleInclude[0]"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("invalid regex")));
    }

    @Test
    void changingTheIdIs400() throws Exception {
        String body = saveBody(loaded(), p -> p.put("id", "other"));

        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE_ID_IMMUTABLE"));
    }

    @Test
    void aStaleBaseHashIs409AndTheFileStays() throws Exception {
        String before = Files.readString(file);
        ObjectNode loaded = loaded();
        loaded.put("hash", "0".repeat(64));

        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content(saveBody(loaded, p -> { })))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_CHANGED"));
        assertThat(Files.readString(file)).isEqualTo(before);
    }

    @Test
    void aMissingBaseHashIs400() throws Exception {
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\": " + json.writeValueAsString(loaded().get("profile")) + "}"))
                .andExpect(status().isBadRequest());
    }
}
