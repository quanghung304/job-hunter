package io.github.quanghung304.jobhunter.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MetaController.class)
class MetaControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProfileProvider profileProvider;

    @Test
    void returnsMetaForTheCurrentProfile() throws Exception {
        given(profileProvider.current()).willReturn(ProfileFixtures.example());

        mvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.statuses").isArray())
                .andExpect(jsonPath("$.employmentTypes").isArray())
                .andExpect(jsonPath("$.eligibilities").isArray())
                .andExpect(jsonPath("$.profileId").value("hugh"))
                .andExpect(jsonPath("$.summaryLanguage").value("vi"));
    }
}
