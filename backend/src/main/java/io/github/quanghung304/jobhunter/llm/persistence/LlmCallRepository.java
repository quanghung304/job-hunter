package io.github.quanghung304.jobhunter.llm.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {
}
