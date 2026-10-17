package io.github.quanghung304.jobhunter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/** Module boundaries from docs/architecture/overview.md ("Modules") and coding rules. */
class ArchitectureTest {

    private static final String BASE = "io.github.quanghung304.jobhunter";

    private static final List<String> MODULES = List.of(
            "common", "profile", "source", "ingest", "llm", "enrich", "scoring", "tracking", "notify",
            "pipeline", "api", "eval");

    private static JavaClasses mainClasses;

    @BeforeAll
    static void importClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    /** The dependency table of overview.md; modules without classes yet are optional. */
    static LayeredArchitecture moduleDependencies(String base) {
        return layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("common").definedBy(base + ".common..")
                .layer("profile").definedBy(base + ".profile..")
                .layer("source").definedBy(base + ".source..")
                .layer("ingest").definedBy(base + ".ingest..")
                .layer("llm").definedBy(base + ".llm..")
                .layer("enrich").definedBy(base + ".enrich..")
                .layer("scoring").definedBy(base + ".scoring..")
                .layer("tracking").definedBy(base + ".tracking..")
                .layer("notify").definedBy(base + ".notify..")
                .layer("pipeline").definedBy(base + ".pipeline..")
                .layer("api").definedBy(base + ".api..")
                .layer("eval").definedBy(base + ".eval..")
                .whereLayer("common").mayNotAccessAnyLayer()
                .whereLayer("profile").mayOnlyAccessLayers("common")
                .whereLayer("source").mayOnlyAccessLayers("common")
                .whereLayer("ingest").mayOnlyAccessLayers("common", "source")
                .whereLayer("llm").mayOnlyAccessLayers("common")
                .whereLayer("enrich").mayOnlyAccessLayers("common", "profile", "llm", "ingest")
                .whereLayer("scoring").mayOnlyAccessLayers("common", "profile", "llm", "enrich")
                .whereLayer("tracking").mayOnlyAccessLayers("common", "profile", "ingest", "enrich")
                .whereLayer("notify").mayOnlyAccessLayers("common", "profile", "tracking")
                .whereLayer("pipeline").mayOnlyAccessLayers(
                        "common", "profile", "source", "ingest", "llm", "enrich", "scoring",
                        "tracking", "notify")
                .whereLayer("api").mayOnlyAccessLayers(
                        "common", "profile", "source", "ingest", "llm", "enrich", "scoring",
                        "tracking", "pipeline")
                .whereLayer("eval").mayOnlyAccessLayers("common", "enrich", "scoring", "llm");
    }

    @Test
    void modulesOnlyDependOnAllowedModules() {
        moduleDependencies(BASE).check(mainClasses);
    }

    @Test
    void moduleRuleDetectsAForbiddenDependency() {
        JavaClasses fixture = new ClassFileImporter()
                .importPackages("io.github.quanghung304.archfixture");

        assertThat(moduleDependencies("io.github.quanghung304.archfixture").evaluate(fixture).hasViolation())
                .as("common -> ingest in the fixture must be reported")
                .isTrue();
    }

    /** Entities and repositories (`<module>.persistence`) are private to their module. */
    static ArchRule persistenceStaysInsideItsModule(String base) {
        return CompositeArchRule.of(MODULES.stream()
                .map(module -> (ArchRule) noClasses()
                        .that().resideOutsideOfPackage(base + "." + module + "..")
                        .should().dependOnClassesThat().resideInAPackage(base + "." + module + ".persistence..")
                        .because("conventions.md: never use another module's repositories or entities"))
                .toList());
    }

    @Test
    void persistenceIsPrivateToItsModule() {
        persistenceStaysInsideItsModule(BASE).check(mainClasses);
    }

    @Test
    void persistenceRuleDetectsAForeignRepositoryUse() {
        JavaClasses fixture = new ClassFileImporter()
                .importPackages("io.github.quanghung304.archfixture");

        assertThat(persistenceStaysInsideItsModule("io.github.quanghung304.archfixture").evaluate(fixture).hasViolation())
                .as("enrich -> ingest.persistence in the fixture must be reported")
                .isTrue();
    }

    @Test
    void controllersLiveInTheApiModule() {
        classes().that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .should().resideInAPackage(BASE + ".api..")
                .check(mainClasses);
    }

    @Test
    void noFieldInjection() {
        noFields().should().beAnnotatedWith(Autowired.class)
                .because("conventions.md: constructor injection only")
                .check(mainClasses);
    }

    @Test
    void timeComesFromTheInjectedClock() {
        noClasses()
                .should().callMethod(Instant.class, "now")
                .orShould().callMethod(LocalDateTime.class, "now")
                .orShould().callMethod(LocalDate.class, "now")
                .orShould().callMethod(OffsetDateTime.class, "now")
                .orShould().callMethod(ZonedDateTime.class, "now")
                .because("conventions.md: inject java.time.Clock")
                .check(mainClasses);
    }

    @Test
    void noLocalDateTimeFields() {
        noFields().should().haveRawType(LocalDateTime.class)
                .because("AGENTS.md: store times as UTC Instant")
                .check(mainClasses);
    }
}
