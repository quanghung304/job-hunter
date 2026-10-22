package io.github.quanghung304.jobhunter.source.persistence;

import io.github.quanghung304.jobhunter.source.AtsType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByAtsAndBoardSlug(AtsType ats, String boardSlug);

    List<Company> findByAtsInOrderByNameAscIdAsc(Collection<AtsType> ats);

    List<Company> findByAtsInAndEnabledTrueOrderByNameAscIdAsc(Collection<AtsType> ats);
}
