package io.github.quanghung304.jobhunter.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.quanghung304.jobhunter.common.JobHunterException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(GlobalExceptionHandlerTest.ThrowingController.class)
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void jobHunterExceptionUsesItsStatusAndCode() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("THING_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Thing 7 does not exist"));
    }

    @Test
    void jobHunterExceptionDefaultsToBadRequest() throws Exception {
        mvc.perform(get("/test/domain-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SOMETHING_WRONG"));
    }

    @Test
    void invalidBodyIsValidationFailed() throws Exception {
        mvc.perform(post("/test/things")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unreadableBodyGetsAStatusCode() throws Exception {
        mvc.perform(post("/test/things")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unknownApiPathIsNotFound() throws Exception {
        mvc.perform(get("/api/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unexpectedExceptionIsInternalErrorWithoutLeakingDetails() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("secret"))));
    }

    @RestController
    static class ThrowingController {

        record ThingRequest(@NotBlank String name) {
        }

        static class ThingNotFoundException extends JobHunterException {
            ThingNotFoundException(long id) {
                super("THING_NOT_FOUND", "Thing " + id + " does not exist");
            }

            @Override
            public int httpStatus() {
                return 404;
            }
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw new ThingNotFoundException(7);
        }

        @GetMapping("/test/domain-error")
        String domainError() {
            throw new JobHunterException("SOMETHING_WRONG", "Something is wrong");
        }

        @PostMapping("/test/things")
        String create(@Valid @RequestBody ThingRequest request) {
            return request.name();
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("secret internal state");
        }
    }
}
