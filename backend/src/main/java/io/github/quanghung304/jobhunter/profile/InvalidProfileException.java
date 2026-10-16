package io.github.quanghung304.jobhunter.profile;

import io.github.quanghung304.jobhunter.common.JobHunterException;
import java.nio.file.Path;
import java.util.List;

/** {@code profile.yaml} is missing, unreadable or invalid; the message names each bad field. */
public class InvalidProfileException extends JobHunterException {

    public static final String CODE = "PROFILE_INVALID";

    private final Path path;
    private final List<String> problems;

    public InvalidProfileException(Path path, List<String> problems) {
        this(path, problems, null);
    }

    public InvalidProfileException(Path path, List<String> problems, Throwable cause) {
        super(CODE, "Invalid profile " + path + ":\n  - " + String.join("\n  - ", problems), cause);
        this.path = path;
        this.problems = List.copyOf(problems);
    }

    public Path path() {
        return path;
    }

    /** One entry per problem, each starting with the field path when there is one. */
    public List<String> problems() {
        return problems;
    }

    @Override
    public int httpStatus() {
        return 500;
    }
}
