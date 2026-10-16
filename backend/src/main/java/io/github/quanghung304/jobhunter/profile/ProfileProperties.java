package io.github.quanghung304.jobhunter.profile;

import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.profile.*} (config-spec.md §2). Relative paths resolve against the working
 * directory ({@code backend/}). Bound as a String: Spring's String-to-Path conversion treats
 * the value as a resource location and rejects {@code ../} paths.
 */
@Validated
@ConfigurationProperties("app.profile")
public record ProfileProperties(@NotBlank @DefaultValue("../config/profile.yaml") String path) {

    public Path file() {
        return Path.of(path);
    }
}
