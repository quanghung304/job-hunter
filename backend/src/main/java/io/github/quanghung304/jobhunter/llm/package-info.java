/**
 * LLM access (ADR-0004, ADR-0008): model registry ({@code config/llm.yaml}), clients
 * (Anthropic, OpenAI-compatible, fake), versioned prompts, {@link io.github.quanghung304.jobhunter.llm.LlmRouter}
 * (purpose → assigned model, fallback/pause), quota tracking, {@code llm_call} log and the
 * budget guard. Other modules use {@code LlmRouter}, {@code BudgetGuard} and the records/enums
 * of this package. May depend on: common.
 */
package io.github.quanghung304.jobhunter.llm;
