package io.github.quanghung304.jobhunter.common.model;

/** CEFR level of a spoken language, declared from lowest to highest. */
public enum LanguageLevel {
    A1,
    A2,
    B1,
    B2,
    C1,
    C2,
    NATIVE;

    public boolean isAtLeast(LanguageLevel required) {
        return compareTo(required) >= 0;
    }
}
