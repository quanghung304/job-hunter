package io.github.quanghung304.jobhunter.profile;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

/** Loads profiles for tests through the real {@link YamlProfileProvider}. */
public final class ProfileFixtures {

    /** The committed template; must always be a valid profile. */
    public static final Path EXAMPLE = Path.of("..", "config", "profile.example.yaml");

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private ProfileFixtures() {
    }

    public static ProfileProvider load(Path path) {
        return new YamlProfileProvider(new ProfileProperties(path.toString()), VALIDATOR);
    }

    /** A {@link ProfileService} over the file at {@code path}, publishing to {@code events}. */
    public static ProfileService service(Path path, org.springframework.context.ApplicationEventPublisher events) {
        var provider = new YamlProfileProvider(new ProfileProperties(path.toString()), VALIDATOR);
        return new ProfileService(provider, events);
    }

    public static Profile example() {
        return load(EXAMPLE).current();
    }

    /** A file under {@code src/test/resources/profile/}. */
    public static Profile resource(String name) {
        try {
            var url = Objects.requireNonNull(ProfileFixtures.class.getResource("/profile/" + name), name);
            return load(Path.of(url.toURI())).current();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(name, e);
        }
    }
}
