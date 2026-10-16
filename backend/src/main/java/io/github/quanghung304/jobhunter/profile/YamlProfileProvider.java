package io.github.quanghung304.jobhunter.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Loads {@code app.profile.path} once at construction, so an invalid profile fails startup.
 * Unknown keys are rejected (typos), except inside {@code tracks}.
 */
@Component
class YamlProfileProvider implements ProfileProvider {

    private static final Logger log = LoggerFactory.getLogger(YamlProfileProvider.class);

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    private final Profile profile;
    private final String profileHash;

    YamlProfileProvider(ProfileProperties properties, Validator validator) {
        Path path = properties.file();
        byte[] bytes = read(path);
        this.profile = parse(path, bytes);
        validate(path, profile, validator);
        this.profileHash = sha256(bytes);
        warnAboutUnsupportedTracks(profile);
        log.info("Loaded profile id={} hash={} from {}", profile.id(), profileHash.substring(0, 12),
                path.toAbsolutePath().normalize());
    }

    @Override
    public Profile current() {
        return profile;
    }

    @Override
    public String profileHash() {
        return profileHash;
    }

    private static byte[] read(Path path) {
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

    private static Profile parse(Path path, byte[] bytes) {
        try {
            // Parse to a tree first: reports syntax errors and empty (comment-only) files.
            JsonNode tree = YAML.readTree(bytes);
            if (tree == null || tree.isMissingNode() || tree.isNull()) {
                throw new InvalidProfileException(path, List.of("file is empty"));
            }
            return YAML.readValue(bytes, Profile.class);
        } catch (JsonMappingException e) {
            throw new InvalidProfileException(path, List.of(fieldPath(e) + ": " + firstLine(e.getOriginalMessage())), e);
        } catch (JsonProcessingException e) {
            var location = e.getLocation();
            String where = location != null ? "line " + location.getLineNr() + ": " : "";
            throw new InvalidProfileException(path, List.of("YAML syntax error at " + where + firstLine(e.getOriginalMessage())), e);
        } catch (IOException e) {
            throw new InvalidProfileException(path, List.of("cannot parse file: " + e.getMessage()), e);
        }
    }

    private static void validate(Path path, Profile profile, Validator validator) {
        List<String> problems = validator.validate(profile).stream()
                .map(YamlProfileProvider::describe)
                .sorted()
                .toList();
        if (!problems.isEmpty()) {
            throw new InvalidProfileException(path, problems);
        }
    }

    private static String describe(ConstraintViolation<Profile> violation) {
        // Hibernate Validator renders list elements as e.g. "acceptRegions[2].<list element>".
        String field = violation.getPropertyPath().toString()
                .replace(".<list element>", "")
                .replaceFirst("^notifySettings\\b", "notify");
        return field + ": " + violation.getMessage();
    }

    /** E.g. {@code preferences.seniority[1]}. */
    private static String fieldPath(JsonMappingException e) {
        var path = new StringBuilder();
        for (JsonMappingException.Reference ref : e.getPath()) {
            if (ref.getFieldName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? "(root)" : path.toString();
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK spec", e);
        }
    }

    private static void warnAboutUnsupportedTracks(Profile profile) {
        profile.tracks().stream()
                .filter(track -> !Profile.SOFTWARE_TRACK.equals(track.name()))
                .forEach(track -> log.warn(
                        "Profile track '{}' is not implemented in Phase 1 and is ignored", track.name()));
    }
}
