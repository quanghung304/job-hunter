package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.enrich.Eligibility;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.source.CollectorRegistry;
import io.github.quanghung304.jobhunter.tracking.TrackingStatus;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Values for the UI filter controls ({@code GET /api/meta}) and the app header's status line
 * ({@code GET /api/status}), api-spec.md.
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class MetaController {

    private final ProfileProvider profileProvider;
    private final CollectorRegistry collectors;
    private final AdminViews views;

    public MetaController(ProfileProvider profileProvider, CollectorRegistry collectors, AdminViews views) {
        this.profileProvider = profileProvider;
        this.collectors = collectors;
        this.views = views;
    }

    @GetMapping("/meta")
    public MetaResponse meta() {
        var profile = profileProvider.current();
        return new MetaResponse(
                collectors.allCodes().stream().map(SourceSummaryResponse::of).toList(),
                List.of(TrackingStatus.values()),
                List.of(EmploymentType.values()),
                List.of(Eligibility.values()),
                profile.id(), profile.summaryLanguage().code());
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return views.status();
    }
}
