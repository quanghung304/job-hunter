package io.github.quanghung304.jobhunter.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.source.persistence.Company;
import io.github.quanghung304.jobhunter.source.persistence.CompanyRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresJpaTest
class CompanySeederIT {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    @Autowired
    CompanyRepository companies;

    @Autowired
    TestEntityManager em;

    @TempDir
    Path dir;

    @Test
    void seedsTheCommittedCompanyList() {
        int seeded = seeder().seed(Path.of("../config/companies.yaml"));

        assertThat(seeded).isPositive();
        assertThat(companies.findByAtsAndBoardSlug(AtsType.GREENHOUSE, "gitlab")).get()
                .extracting(Company::getName).isEqualTo("GitLab");
    }

    @Test
    void insertsNewBoardsAndUpdatesNamesWithoutTouchingTheCheckColumns() throws IOException {
        Company existing = companies.saveAndFlush(new Company("Old name", AtsType.LEVER, "spotify"));
        existing.setLastCheckStatus(CompanyCheckStatus.ERROR);
        existing.setLastCheckedAt(NOW);
        existing.setNotFoundCount(1);
        companies.saveAndFlush(existing);

        seeder().seed(file("""
                companies:
                  - { name: Spotify, ats: LEVER, slug: spotify, notes: smoke test }
                  - { name: Linear,  ats: ASHBY, slug: linear }
                """));
        seeder().seed(file("""
                companies:
                  - { name: Spotify, ats: LEVER, slug: spotify }
                  - { name: Linear,  ats: ASHBY, slug: linear }
                """));
        em.clear();

        assertThat(companies.count()).isEqualTo(2);
        Company spotify = companies.findByAtsAndBoardSlug(AtsType.LEVER, "spotify").orElseThrow();
        assertThat(spotify.getName()).isEqualTo("Spotify");
        assertThat(spotify.getLastCheckStatus()).isEqualTo(CompanyCheckStatus.ERROR);
        assertThat(spotify.getLastCheckedAt()).isEqualTo(NOW);
        assertThat(spotify.getNotFoundCount()).isEqualTo(1);
        assertThat(spotify.isEnabled()).isTrue();
        assertThat(companies.findByAtsAndBoardSlug(AtsType.ASHBY, "linear")).get()
                .satisfies(c -> assertThat(c.getLastCheckStatus()).isNull());
    }

    @Test
    void boardsRemovedFromTheFileAreDisabledAndComeBackWhenAddedAgain() throws IOException {
        seeder().seed(file("""
                companies:
                  - { name: Acme, ats: GREENHOUSE, slug: acme }
                  - { name: Beta, ats: GREENHOUSE, slug: beta }
                """));
        seeder().seed(file("""
                companies:
                  - { name: Acme, ats: GREENHOUSE, slug: acme }
                """));
        assertThat(enabled("beta")).isFalse();

        seeder().seed(file("""
                companies:
                  - { name: Acme, ats: GREENHOUSE, slug: acme }
                  - { name: Beta, ats: GREENHOUSE, slug: beta }
                """));
        assertThat(enabled("beta")).isTrue();
    }

    @Test
    void aBoardDisabledByThreeNotFoundsStaysDisabled() throws IOException {
        String yaml = """
                companies:
                  - { name: Gone, ats: ASHBY, slug: gone }
                """;
        seeder().seed(file(yaml));
        long id = companies.findByAtsAndBoardSlug(AtsType.ASHBY, "gone").orElseThrow().getId();
        CompanyBoards boards = new CompanyBoards(companies, new MutableClock(NOW));
        assertThat(boards.recordNotFound(id)).isEqualTo(1);
        assertThat(boards.recordNotFound(id)).isEqualTo(2);
        assertThat(boards.enabled(Set.of(AtsType.ASHBY))).hasSize(1);
        assertThat(boards.recordNotFound(id)).isEqualTo(3);
        assertThat(boards.enabled(Set.of(AtsType.ASHBY))).isEmpty();

        seeder().seed(file(yaml));
        assertThat(companies.findById(id).orElseThrow().isEnabled()).isFalse();

        boards.recordOk(id);
        Company back = companies.findById(id).orElseThrow();
        assertThat(back.isEnabled()).isTrue();
        assertThat(back.getNotFoundCount()).isZero();
        assertThat(back.getLastCheckStatus()).isEqualTo(CompanyCheckStatus.OK);
        assertThat(back.getLastCheckedAt()).isEqualTo(NOW);
    }

    @Test
    void invalidEntriesFailWithEveryProblem() throws IOException {
        Path bad = file("""
                companies:
                  - { name: "", ats: GREENHOUSE, slug: acme }
                  - { name: Acme, ats: WORKDAY, slug: acme }
                  - { name: Acme, ats: LEVER, slug: " " }
                  - { name: Acme, ats: ASHBY, slug: acme }
                  - { name: Acme again, ats: ASHBY, slug: acme }
                """);

        assertThatThrownBy(() -> seeder().seed(bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("companies[0].name: must not be blank")
                .hasMessageContaining("companies[1].ats: must be one of GREENHOUSE, LEVER, LEVER_EU, ASHBY")
                .hasMessageContaining("companies[2].slug: must not be blank")
                .hasMessageContaining("companies[4]: duplicate board ASHBY acme");
        assertThat(companies.count()).isZero();
    }

    @Test
    void unknownKeysAreRejected() throws IOException {
        Path typo = file("""
                companies:
                  - { name: Acme, ats: ASHBY, slgu: acme }
                """);

        assertThatThrownBy(() -> seeder().seed(typo)).isInstanceOf(IllegalStateException.class).hasMessageContaining("slgu");
    }

    @Test
    void aMissingFileSeedsNothing() {
        assertThat(seeder().seed(dir.resolve("missing.yaml"))).isZero();
        assertThat(companies.count()).isZero();
    }

    private boolean enabled(String slug) {
        em.flush();
        em.clear();
        return companies.findByAtsAndBoardSlug(AtsType.GREENHOUSE, slug).orElseThrow().isEnabled();
    }

    private CompanySeeder seeder() {
        return new CompanySeeder(companies, new CompaniesProperties("unused"));
    }

    private Path file(String yaml) throws IOException {
        Path file = Files.createTempFile(dir, "companies", ".yaml");
        Files.writeString(file, yaml);
        return file;
    }
}
