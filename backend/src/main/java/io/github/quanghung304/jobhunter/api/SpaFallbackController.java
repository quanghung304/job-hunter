package io.github.quanghung304.jobhunter.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards client-side routes (e.g. {@code /jobs/42}) to {@code /index.html} so React Router
 * deep links survive a reload. Paths under {@code /api}, {@code /actuator}, {@code /v3},
 * {@code /swagger-ui}, {@code /error} and any path whose last segment has a file extension
 * are not matched, so they reach their own handlers or the static resource handler.
 */
@Controller
public class SpaFallbackController {

    /** First segment: not a reserved prefix and no dot. */
    private static final String FIRST = "{s1:(?!(?:api|actuator|v3|swagger-ui|error)$)[^.]+}";

    // Further segments have no dot either, so asset files are never matched.
    @GetMapping({
            "/" + FIRST,
            "/" + FIRST + "/{s2:[^.]+}",
            "/" + FIRST + "/{s2:[^.]+}/{s3:[^.]+}",
            "/" + FIRST + "/{s2:[^.]+}/{s3:[^.]+}/{s4:[^.]+}"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
