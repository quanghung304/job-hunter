/**
 * LLM scoring of analyzed, eligible jobs per profile, only on an owner action (ADR-0010);
 * writes scores through {@code AssessmentService}.
 * May depend on: common, profile, ingest (read), llm, enrich.
 */
package io.github.quanghung304.jobhunter.scoring;
