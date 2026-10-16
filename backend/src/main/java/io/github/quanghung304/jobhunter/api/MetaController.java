package io.github.quanghung304.jobhunter.api;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Values for the UI filter controls (api-spec.md, {@code GET /api/meta}). */
@RestController
@RequestMapping(path = "/api/meta", produces = MediaType.APPLICATION_JSON_VALUE)
public class MetaController {

    // Stub until the profile (T-002) and the enums/sources (T-003, T-014) exist.
    @GetMapping
    public MetaResponse meta() {
        return new MetaResponse(List.of(), List.of(), List.of(), List.of(), null, null);
    }
}
