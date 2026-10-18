package io.github.quanghung304.jobhunter.llm.prompt;

import io.github.quanghung304.jobhunter.common.JobHunterException;
import java.util.List;

/** A prompt was rendered without a value for some placeholders: a programming error. */
public class MissingPlaceholderException extends JobHunterException {

    public static final String CODE = "PROMPT_PLACEHOLDER_MISSING";

    public MissingPlaceholderException(String promptId, List<String> names) {
        super(CODE, "Prompt " + promptId + " has no value for: " + String.join(", ", names));
    }

    @Override
    public int httpStatus() {
        return 500;
    }
}
