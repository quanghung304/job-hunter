package io.github.quanghung304.jobhunter.source;

import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.companies.*} (config-spec.md §2). Relative paths resolve against the working
 * directory ({@code backend/}); bound as a String like {@code app.profile.path}.
 */
@Validated
@ConfigurationProperties("app.companies")
public record CompaniesProperties(@NotBlank @DefaultValue("../config/companies.yaml") String path) {

    public Path file() {
        return Path.of(path);
    }
}
