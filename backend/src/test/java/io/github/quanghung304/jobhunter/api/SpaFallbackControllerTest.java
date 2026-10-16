package io.github.quanghung304.jobhunter.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpaFallbackController.class)
class SpaFallbackControllerTest {

    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/jobs", "/jobs/1", "/admin", "/jobs/42/notes", "/a/b/c/d"})
    void clientRoutesForwardToIndexHtml(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api", "/api/jobs", "/api/jobs/1", "/actuator", "/actuator/health", "/v3/api-docs",
            "/swagger-ui/index.html", "/error", "/favicon.ico", "/assets/index-abc.js",
            "/jobs/1/report.pdf"
    })
    void reservedPathsAndFilesAreNotForwarded(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(forwardedUrl(null));
    }
}
