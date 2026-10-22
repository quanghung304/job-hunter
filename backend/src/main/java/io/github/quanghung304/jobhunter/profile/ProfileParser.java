package io.github.quanghung304.jobhunter.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Reads a profile from YAML bytes or a JSON tree with the rules of config-spec §1 (unknown keys
 * rejected, regexes compiled) and validates it; every problem carries its field path. Shared by
 * startup and the Profile page (ADR-0012), so both apply the same rules.
 */
final class ProfileParser {

    static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    private ProfileParser() {
    }

    /** The profile could not be read; the problems name the fields. */
    static final class ProblemsException extends RuntimeException {

        private final transient List<ProfileProblem> problems;

        ProblemsException(List<ProfileProblem> problems, Throwable cause) {
            super(problems.toString(), cause);
            this.problems = List.copyOf(problems);
        }

        List<ProfileProblem> problems() {
            return problems;
        }
    }

    static Profile fromYaml(byte[] bytes) {
        JsonNode tree;
        try {
            // Parse to a tree first: reports syntax errors and empty (comment-only) files.
            tree = YAML.readTree(bytes);
        } catch (JsonProcessingException e) {
            var location = e.getLocation();
            String where = location != null ? "line " + location.getLineNr() + ": " : "";
            throw problem("", "YAML syntax error at " + where + firstLine(e.getOriginalMessage()), e);
        } catch (IOException e) {
            throw problem("", "cannot parse file: " + e.getMessage(), e);
        }
        if (tree == null || tree.isMissingNode() || tree.isNull()) {
            throw problem("", "file is empty", null);
        }
        return fromTree(tree);
    }

    static Profile fromTree(JsonNode tree) {
        if (tree == null || !tree.isObject()) {
            throw problem("", "the profile must be an object", null);
        }
        try {
            return YAML.treeToValue(tree, Profile.class);
        } catch (JsonMappingException e) {
            throw problem(fieldPath(e), firstLine(e.getOriginalMessage()), e);
        } catch (JsonProcessingException e) {
            throw problem("", "cannot read the profile: " + firstLine(e.getOriginalMessage()), e);
        }
    }

    /** Bean Validation problems by field path, sorted; empty when valid. */
    static List<ProfileProblem> validate(Profile profile, Validator validator) {
        return validator.validate(profile).stream()
                .map(ProfileParser::describe)
                .sorted(Comparator.comparing(ProfileProblem::toString))
                .toList();
    }

    private static ProfileProblem describe(ConstraintViolation<Profile> violation) {
        // Hibernate Validator renders list elements as e.g. "acceptRegions[2].<list element>".
        String field = violation.getPropertyPath().toString()
                .replace(".<list element>", "")
                .replaceFirst("^notifySettings\\b", "notify");
        return new ProfileProblem(field, violation.getMessage());
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

    private static ProblemsException problem(String path, String message, Throwable cause) {
        return new ProblemsException(List.of(new ProfileProblem(path, message)), cause);
    }
}
