package io.github.quanghung304.jobhunter.llm.persistence;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {

    /** HTTP attempts sent to a model since {@code since} (quota counting). */
    long countByModelIdAndCreatedAtGreaterThanEqual(String modelId, Instant since);

    long countByCreatedAtGreaterThanEqual(Instant since);

    @Query("""
            select coalesce(sum(c.inputTokens + c.outputTokens), 0) from LlmCall c
            where c.modelId = :modelId and c.createdAt >= :since""")
    long sumTokens(@Param("modelId") String modelId, @Param("since") Instant since);

    /** Average cost of successful calls of a model and purpose since {@code since}; null without history. */
    @Query("""
            select avg(c.costUsd) from LlmCall c
            where c.modelId = :modelId and c.purpose = :purpose and c.success = true
              and c.costUsd is not null and c.createdAt >= :since""")
    BigDecimal averageCost(@Param("modelId") String modelId, @Param("purpose") LlmPurpose purpose,
            @Param("since") Instant since);

    @Query("""
            select coalesce(sum(c.costUsd), 0) from LlmCall c
            where c.tier = :tier and c.createdAt >= :since""")
    BigDecimal sumCost(@Param("tier") ModelTier tier, @Param("since") Instant since);
}
