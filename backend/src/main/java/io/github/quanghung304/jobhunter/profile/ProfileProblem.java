package io.github.quanghung304.jobhunter.profile;

/**
 * One problem of a profile, e.g. {@code candidate.headline} / {@code must not be blank}. The path
 * is empty for problems of the whole file (YAML syntax, empty file).
 */
public record ProfileProblem(String path, String message) {

    /** {@code path: message}, or the message alone without a path (startup report). */
    @Override
    public String toString() {
        return path.isEmpty() ? message : path + ": " + message;
    }
}
