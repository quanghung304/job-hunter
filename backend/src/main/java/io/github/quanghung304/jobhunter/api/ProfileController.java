package io.github.quanghung304.jobhunter.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.api.ProfileResponses.ProfileResponse;
import io.github.quanghung304.jobhunter.api.ProfileResponses.ProfileSaveRequest;
import io.github.quanghung304.jobhunter.api.ProfileResponses.ProfileSaveResponse;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner's profile (ADR-0012): read it, save it. A save rewrites {@code profile.yaml}, reloads
 * it and re-checks the jobs without LLM calls; there is no list, create or delete.
 */
@RestController
@RequestMapping(path = "/api/profile", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProfileController {

    private final ProfileService profiles;
    /** DTO → YAML-shaped tree without nulls, so an absent optional value gets its default. */
    private final ObjectMapper tree;

    public ProfileController(ProfileService profiles, ObjectMapper json) {
        this.profiles = profiles;
        this.tree = json.copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    @GetMapping
    @Operation(summary = "The current profile with its hash (send it back as baseHash)")
    public ProfileResponse get() {
        return ProfileResponse.of(profiles.current());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Save the profile: validate, rewrite profile.yaml (previous kept as .bak), re-check jobs"
            + " without LLM calls")
    @ApiResponse(responseCode = "200", description = "Saved")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (errors by path) or PROFILE_ID_IMMUTABLE")
    @ApiResponse(responseCode = "409", description = "PROFILE_CHANGED: baseHash is stale; reload")
    public ProfileSaveResponse save(@Valid @RequestBody ProfileSaveRequest request) {
        JsonNode yamlShaped = tree.valueToTree(request.profile());
        Profile profile = profiles.parse(yamlShaped);
        return ProfileSaveResponse.of(profiles.save(profile, request.baseHash()));
    }
}
