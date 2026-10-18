package io.github.quanghung304.jobhunter.llm.json;

/**
 * The model answer is not a valid JSON object for the expected type. The message is written
 * for the model: it is appended to the corrective retry.
 */
public class InvalidLlmOutputException extends RuntimeException {

    public InvalidLlmOutputException(String message) {
        super(message);
    }
}
