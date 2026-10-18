package io.github.quanghung304.jobhunter.llm;

/** Billing tier of a registry model (config-spec §5). Only PAID calls cost money. */
public enum ModelTier {
    FREE,
    PAID,
    LOCAL
}
