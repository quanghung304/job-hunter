package io.github.quanghung304.jobhunter.llm.prompt;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/** A prompt file is missing or malformed. */
public class InvalidPromptException extends JobHunterException {

    public static final String CODE = "PROMPT_INVALID";

    public InvalidPromptException(String promptId, String problem) {
        this(promptId, problem, null);
    }

    public InvalidPromptException(String promptId, String problem, Throwable cause) {
        super(CODE, "Prompt " + promptId + ": " + problem, cause);
    }

    @Override
    public int httpStatus() {
        return 500;
    }
}
