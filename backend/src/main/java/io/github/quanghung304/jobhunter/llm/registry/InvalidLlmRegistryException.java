package io.github.quanghung304.jobhunter.llm.registry;

import io.github.quanghung304.jobhunter.common.JobHunterException;
import java.nio.file.Path;
import java.util.List;

/** {@code llm.yaml} is missing, unreadable or invalid; the message names each bad field. */
public class InvalidLlmRegistryException extends JobHunterException {

    public static final String CODE = "LLM_REGISTRY_INVALID";

    private final Path path;
    private final List<String> problems;

    public InvalidLlmRegistryException(Path path, List<String> problems, Throwable cause) {
        super(CODE, "Invalid LLM registry " + path + ":\n  - " + String.join("\n  - ", problems), cause);
        this.path = path;
        this.problems = List.copyOf(problems);
    }

    public Path path() {
        return path;
    }

    public List<String> problems() {
        return problems;
    }

    @Override
    public int httpStatus() {
        return 500;
    }
}
