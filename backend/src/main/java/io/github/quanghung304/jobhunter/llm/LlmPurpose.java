package io.github.quanghung304.jobhunter.llm;

/** Why an LLM call was made. Only EXTRACT and SCORE have an assignment (llm_assignment). */
public enum LlmPurpose {
    EXTRACT,
    SCORE,
    EVAL,
    /** Admin "Test" call of one model (T-022). */
    TEST
}
