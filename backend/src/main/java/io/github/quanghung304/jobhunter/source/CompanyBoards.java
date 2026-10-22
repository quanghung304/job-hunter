package io.github.quanghung304.jobhunter.source;

import io.github.quanghung304.jobhunter.source.persistence.Company;
import io.github.quanghung304.jobhunter.source.persistence.CompanyRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company boards of the ATS collectors and their check status (docs/sources/ats.md): a
 * successful fetch sets OK (and enables the board again), a 404 sets NOT_FOUND and disables
 * the board after {@value #MAX_NOT_FOUND} in a row, other errors set ERROR.
 */
@Service
public class CompanyBoards {

    public static final int MAX_NOT_FOUND = 3;

    private static final Logger log = LoggerFactory.getLogger(CompanyBoards.class);

    private final CompanyRepository companies;
    private final Clock clock;

    public CompanyBoards(CompanyRepository companies, Clock clock) {
        this.companies = companies;
        this.clock = clock;
    }

    /** Enabled boards of these ATS types, by name. */
    @Transactional(readOnly = true)
    public List<CompanyBoard> enabled(Set<AtsType> ats) {
        return companies.findByAtsInAndEnabledTrueOrderByNameAscIdAsc(ats).stream().map(CompanyBoards::board).toList();
    }

    /** All boards of these ATS types, enabled or not, by name. */
    @Transactional(readOnly = true)
    public List<CompanyBoard> all(Set<AtsType> ats) {
        return companies.findByAtsInOrderByNameAscIdAsc(ats).stream().map(CompanyBoards::board).toList();
    }

    @Transactional
    public void recordOk(long id) {
        Company company = find(id);
        if (!company.isEnabled()) {
            log.info("company board {} {} is reachable again and enabled", company.getAts(), company.getBoardSlug());
        }
        company.setEnabled(true);
        company.setNotFoundCount(0);
        mark(company, CompanyCheckStatus.OK);
    }

    /** @return the consecutive 404 count, including this one; the board is disabled at {@value #MAX_NOT_FOUND} */
    @Transactional
    public int recordNotFound(long id) {
        Company company = find(id);
        int count = company.getNotFoundCount() + 1;
        company.setNotFoundCount(count);
        if (count >= MAX_NOT_FOUND && company.isEnabled()) {
            company.setEnabled(false);
            log.warn("company board {} {} disabled after {} consecutive 404s", company.getAts(),
                    company.getBoardSlug(), count);
        }
        mark(company, CompanyCheckStatus.NOT_FOUND);
        return count;
    }

    @Transactional
    public void recordError(long id) {
        mark(find(id), CompanyCheckStatus.ERROR);
    }

    private void mark(Company company, CompanyCheckStatus status) {
        company.setLastCheckStatus(status);
        company.setLastCheckedAt(clock.instant());
    }

    private Company find(long id) {
        return companies.findById(id).orElseThrow(() -> new IllegalArgumentException("No company with id " + id));
    }

    private static CompanyBoard board(Company company) {
        return new CompanyBoard(company.getId(), company.getName(), company.getAts(), company.getBoardSlug());
    }
}
