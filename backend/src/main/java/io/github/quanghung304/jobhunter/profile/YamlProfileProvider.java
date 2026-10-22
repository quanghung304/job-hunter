package io.github.quanghung304.jobhunter.profile;

import io.github.quanghung304.jobhunter.common.Hashing;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Loads {@code app.profile.path} at construction, so an invalid profile fails startup. Unknown
 * keys are rejected (typos), except inside {@code tracks}. {@link #reload()} reads the file again
 * after a save from the Profile page and swaps the immutable snapshot (ADR-0012).
 */
@Component
class YamlProfileProvider implements ProfileProvider {

    private static final Logger log = LoggerFactory.getLogger(YamlProfileProvider.class);

    private final Path path;
    private final Validator validator;
    private volatile ProfileSnapshot snapshot;

    YamlProfileProvider(ProfileProperties properties, Validator validator) {
        this.path = properties.file();
        this.validator = validator;
        this.snapshot = load();
        warnAboutUnsupportedTracks(snapshot.profile());
        log.info("Loaded profile id={} hash={} from {}", snapshot.profile().id(), snapshot.hash().substring(0, 12),
                path.toAbsolutePath().normalize());
    }

    @Override
    public ProfileSnapshot snapshot() {
        return snapshot;
    }

    Path path() {
        return path;
    }

    Validator validator() {
        return validator;
    }

    /** Reads and validates the file again and makes it the current profile. */
    ProfileSnapshot reload() {
        ProfileSnapshot loaded = load();
        snapshot = loaded;
        log.info("Reloaded profile id={} hash={}", loaded.profile().id(), loaded.hash().substring(0, 12));
        return loaded;
    }

    private ProfileSnapshot load() {
        byte[] bytes = read(path);
        Profile profile;
        try {
            profile = ProfileParser.fromYaml(bytes);
        } catch (ProfileParser.ProblemsException e) {
            throw invalid(e.problems(), e.getCause());
        }
        List<ProfileProblem> problems = ProfileParser.validate(profile, validator);
        if (!problems.isEmpty()) {
            throw invalid(problems, null);
        }
        return new ProfileSnapshot(profile, Hashing.sha256Hex(bytes), lastModified(path));
    }

    private InvalidProfileException invalid(List<ProfileProblem> problems, Throwable cause) {
        return new InvalidProfileException(path, problems.stream().map(ProfileProblem::toString).toList(), cause);
    }

    static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            throw new InvalidProfileException(path, List.of(
                    "file not found at " + path.toAbsolutePath().normalize()
                            + "; copy config/profile.example.yaml to config/profile.yaml"), e);
        } catch (IOException e) {
            throw new InvalidProfileException(path, List.of("cannot read file: " + e.getMessage()), e);
        }
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            throw new InvalidProfileException(path, List.of("cannot read file: " + e.getMessage()), e);
        }
    }

    private static void warnAboutUnsupportedTracks(Profile profile) {
        profile.tracks().stream()
                .filter(track -> !Profile.SOFTWARE_TRACK.equals(track.name()))
                .forEach(track -> log.warn(
                        "Profile track '{}' is not implemented in Phase 1 and is ignored", track.name()));
    }
}
