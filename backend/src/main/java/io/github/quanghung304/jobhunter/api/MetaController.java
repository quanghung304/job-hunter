package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Values for the UI filter controls (api-spec.md, {@code GET /api/meta}). */
@RestController
@RequestMapping(path = "/api/meta", produces = MediaType.APPLICATION_JSON_VALUE)
public class MetaController {

    private final ProfileProvider profileProvider;

    public MetaController(ProfileProvider profileProvider) {
        this.profileProvider = profileProvider;
    }

    // Sources, statuses, employment types and eligibilities are filled in T-014.
    @GetMapping
    public MetaResponse meta() {
        var profile = profileProvider.current();
        return new MetaResponse(List.of(), List.of(), List.of(), List.of(),
                profile.id(), profile.summaryLanguage().code());
    }
}
