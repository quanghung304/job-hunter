package io.github.quanghung304.jobhunter.source;

import io.github.quanghung304.jobhunter.source.persistence.Company;
import io.github.quanghung304.jobhunter.source.persistence.CompanyRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to the company boards of the ATS collectors (admin "Companies" table). */
@Service
@Transactional(readOnly = true)
public class CompanyService {

    private final CompanyRepository companies;

    public CompanyService(CompanyRepository companies) {
        this.companies = companies;
    }

    /** All companies, by name. */
    public List<CompanyView> all() {
        return companies.findAll().stream()
                .map(CompanyService::view)
                .sorted(Comparator.comparing(CompanyView::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CompanyView::id))
                .toList();
    }

    private static CompanyView view(Company company) {
        return new CompanyView(company.getId(), company.getName(), company.getAts(), company.getBoardSlug(),
                company.isEnabled(), company.getLastCheckStatus(), company.getLastCheckedAt());
    }
}
