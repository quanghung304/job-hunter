package io.github.quanghung304.jobhunter.source.persistence;

import static io.github.quanghung304.jobhunter.PersistenceFixtures.T0;
import static io.github.quanghung304.jobhunter.PersistenceFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.source.AtsType;
import io.github.quanghung304.jobhunter.source.CompanyCheckStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresJpaTest
class CompanyRepositoryIT {

    @Autowired
    CompanyRepository companies;

    @Autowired
    TestEntityManager em;

    @Test
    void roundTrip() {
        var company = new Company("GitLab", AtsType.GREENHOUSE, "gitlab");
        company.setLastCheckStatus(CompanyCheckStatus.NOT_FOUND);
        company.setLastCheckedAt(T0);
        long id = companies.saveAndFlush(company).getId();
        em.clear();

        var loaded = companies.findById(id).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("GitLab");
        assertThat(loaded.getAts()).isEqualTo(AtsType.GREENHOUSE);
        assertThat(loaded.getBoardSlug()).isEqualTo("gitlab");
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getLastCheckStatus()).isEqualTo(CompanyCheckStatus.NOT_FOUND);
        assertThat(loaded.getLastCheckedAt()).isEqualTo(T0);
    }

    @Test
    void findsByAtsAndSlug() {
        companies.saveAndFlush(new Company("Acme EU", AtsType.LEVER_EU, "acme"));

        assertThat(companies.findByAtsAndBoardSlug(AtsType.LEVER_EU, "acme")).isPresent();
        assertThat(companies.findByAtsAndBoardSlug(AtsType.LEVER, "acme")).isEmpty();
    }

    @Test
    void atsAndSlugAreUnique() {
        companies.saveAndFlush(new Company("Acme", AtsType.ASHBY, "acme"));

        assertRejected("unique", () -> companies.saveAndFlush(new Company("Acme again", AtsType.ASHBY, "acme")));
    }

    @Test
    void sameSlugOnAnotherAtsIsAllowed() {
        companies.saveAndFlush(new Company("Acme", AtsType.ASHBY, "acme"));
        companies.saveAndFlush(new Company("Acme", AtsType.GREENHOUSE, "acme"));

        assertThat(companies.count()).isEqualTo(2);
    }

    @Test
    void atsIsCheckedByTheDatabase() {
        assertRejected(em.getEntityManager(), "check",
                "insert into company (name, ats, board_slug) values ('x', 'WORKDAY', 'x')");
    }
}
