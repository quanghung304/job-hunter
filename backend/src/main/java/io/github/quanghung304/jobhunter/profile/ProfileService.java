package io.github.quanghung304.jobhunter.profile;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.quanghung304.jobhunter.common.Hashing;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Reads and saves the owner's profile (ADR-0012). {@code profile.yaml} stays the source of truth:
 * a save validates with the startup rules, writes a temp file and moves it over the original
 * (the previous file is kept as {@code profile.yaml.bak}), reloads the provider and publishes
 * {@link ProfileChanged}, whose synchronous listener re-checks the jobs without LLM calls.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final YamlProfileProvider provider;
    private final ApplicationEventPublisher events;

    ProfileService(YamlProfileProvider provider, ApplicationEventPublisher events) {
        this.provider = provider;
        this.events = events;
    }

    /** The result of a save: the new snapshot and the jobs re-checked for it. */
    public record SaveResult(ProfileSnapshot snapshot, ProfileChanged.Reassessment reassessed) {
    }

    public ProfileSnapshot current() {
        return provider.snapshot();
    }

    /**
     * Reads a profile sent as a JSON tree (keys of {@code profile.yaml}) with the startup rules.
     *
     * @throws ProfileValidationException listing the problem (mapping errors stop at the first)
     */
    public Profile parse(JsonNode tree) {
        try {
            return ProfileParser.fromTree(tree);
        } catch (ProfileParser.ProblemsException e) {
            throw new ProfileValidationException(e.problems());
        }
    }

    /**
     * Saves the profile when {@code baseHash} is still the current hash.
     *
     * @throws ProfileChangedException     the client's hash is stale, or the file was edited by hand
     * @throws ProfileIdImmutableException the id differs from the current one
     * @throws ProfileValidationException  with every Bean Validation problem by path
     */
    public synchronized SaveResult save(Profile profile, String baseHash) {
        ProfileSnapshot current = provider.snapshot();
        if (!current.hash().equals(baseHash)) {
            throw new ProfileChangedException("The profile was saved since you loaded it; reload the page");
        }
        Path path = provider.path();
        byte[] onDisk = YamlProfileProvider.read(path);
        if (!current.hash().equals(Hashing.sha256Hex(onDisk))) {
            throw new ProfileChangedException(
                    "profile.yaml was edited outside the app; restart the app to load it, then reload the page");
        }
        if (!current.profile().id().equals(profile.id())) {
            throw new ProfileIdImmutableException(current.profile().id(), profile.id());
        }
        List<ProfileProblem> problems = ProfileParser.validate(profile, provider.validator());
        if (!problems.isEmpty()) {
            throw new ProfileValidationException(problems);
        }

        byte[] yaml = ProfileYamlWriter.write(profile, tracksOf(onDisk)).getBytes(StandardCharsets.UTF_8);
        checkReadable(yaml);
        replace(path, yaml);
        ProfileSnapshot saved = provider.reload();

        var changed = new ProfileChanged(current.hash(), saved.hash(), saved.profile());
        events.publishEvent(changed);
        ProfileChanged.Reassessment reassessed = changed.reassessment().orElse(ProfileChanged.Reassessment.NONE);
        log.info("profile saved id={} hash={} → {} reassessed={}", saved.profile().id(),
                current.hash().substring(0, 12), saved.hash().substring(0, 12), reassessed);
        return new SaveResult(saved, reassessed);
    }

    /** {@code tracks} of the file on disk, kept as written (track-specific keys are not modelled). */
    private static JsonNode tracksOf(byte[] onDisk) {
        try {
            JsonNode tree = ProfileParser.YAML.readTree(onDisk);
            return tree == null ? null : tree.get("tracks");
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the current profile file", e);
        }
    }

    /** The written YAML must load with the startup rules, or nothing is written. */
    private void checkReadable(byte[] yaml) {
        try {
            Profile reread = ProfileParser.fromYaml(yaml);
            List<ProfileProblem> problems = ProfileParser.validate(reread, provider.validator());
            if (!problems.isEmpty()) {
                throw new IllegalStateException("the written profile does not validate: " + problems);
            }
        } catch (ProfileParser.ProblemsException e) {
            throw new IllegalStateException("the written profile does not load: " + e.problems(), e);
        }
    }

    /** Temp file next to the target, previous file to {@code .bak}, then an atomic move over the target. */
    static void replace(Path target, byte[] content) {
        Path dir = target.toAbsolutePath().getParent();
        Path temp = null;
        try {
            temp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
            Files.write(temp, content);
            Files.copy(target, target.resolveSibling(target.getFileName() + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + target, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort; the target is unchanged
                }
            }
        }
    }
}
