package io.github.quanghung304.jobhunter.llm.registry;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Turns an invalid {@code llm.yaml} into a short "APPLICATION FAILED TO START" report. */
class InvalidLlmRegistryFailureAnalyzer extends AbstractFailureAnalyzer<InvalidLlmRegistryException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InvalidLlmRegistryException cause) {
        return new FailureAnalysis(
                cause.getMessage(),
                "Fix the fields above in " + cause.path().toAbsolutePath().normalize()
                        + " (reference: docs/architecture/config-spec.md §5), or point app.llm.registry-path"
                        + " to another file.",
                cause);
    }
}
