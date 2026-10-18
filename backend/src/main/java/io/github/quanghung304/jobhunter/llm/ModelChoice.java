package io.github.quanghung304.jobhunter.llm;

/**
 * The model that would serve a purpose now (assignment or {@code app.llm.force-model}), for
 * dry runs and confirmations (ADR-0009: a PAID model must be named before the owner confirms).
 *
 * @param paidCallsAllowed the owner's standing consent ({@code app.llm.allow-paid-calls})
 */
public record ModelChoice(String modelId, String label, ModelTier tier, boolean paidCallsAllowed) {

    public boolean paid() {
        return tier == ModelTier.PAID;
    }

    /** A PAID model without consent: actions using it must be refused (409 {@code PAID_CALLS_DISABLED}). */
    public boolean blockedByConsent() {
        return paid() && !paidCallsAllowed;
    }
}
