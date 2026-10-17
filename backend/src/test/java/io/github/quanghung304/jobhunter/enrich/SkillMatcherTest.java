package io.github.quanghung304.jobhunter.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SkillMatcherTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" in \"{1}\" → {2}")
    @CsvSource(delimiter = ';', textBlock = """
            Java;            We use Java 21.;                            true
            Java;            we use JAVA.;                               true
            Java;            Java/Kotlin backend;                        true
            Java;            JavaScript only;                            false
            Java;            Javanese culture;                           false
            Java;            (java);                                     true
            Spring Boot;     Spring Boot 3;                              true
            Spring Boot;     SpringBoot;                                 true
            Spring Boot;     spring-boot starter;                        true
            Spring Boot;     spring_boot;                                true
            Spring Boot;     Spring and later Boot;                      false
            REST API;        REST API design;                            true
            REST API;        RESTful services;                           true
            REST API;        REST APIs;                                  true
            REST API;        rest-api;                                   true
            REST API;        take a rest;                                false
            OAuth 2.0;       OAuth2 and friends;                         true
            OAuth 2.0;       OAuth 2.0;                                  true
            OpenID Connect;  OIDC;                                       true
            Hibernate/JPA;   Hibernate ORM;                              true
            Hibernate/JPA;   Spring Data JPA;                            true
            Hibernate/JPA;   Hibernate/JPA;                              true
            IAM/SSO;         single sign-on;                             true
            IAM/SSO;         SSO with Keycloak;                          true
            IAM/SSO;         Siam;                                       false
            CI/CD;           CI/CD pipelines;                            true
            CI/CD;           a CD player;                                false
            Microservices;   a microservice architecture;                true
            SQL;             PostgreSQL;                                 false
            SQL;             SQL Server;                                 true
            C#;              C# and .NET;                                true
            C#;              C and C++;                                  false
            .NET;            C# and .NET;                                true
            Node.js;         NodeJS;                                     true
            Kubernetes;      k8s clusters;                               true
            """)
    void matchesOneSkill(String skill, String text, boolean expected) {
        assertThat(SkillMatcher.of(List.of(skill)).hits(text)).hasSize(expected ? 1 : 0);
    }

    @Test
    void hitsAreDistinctSkillsInListOrder() {
        var matcher = SkillMatcher.of(List.of("Java", "Spring Boot", "Hibernate/JPA", "Oracle"));

        Set<String> hits = matcher.hits("Hibernate, JPA, spring-boot, Java and more Java.");

        assertThat(hits).containsExactly("Java", "Spring Boot", "Hibernate/JPA");
    }

    @Test
    void blankAndDuplicateSkillsAreIgnored() {
        var matcher = SkillMatcher.of(Arrays.asList("Java", " ", null, "java"));

        assertThat(matcher.hits("java")).containsExactly("Java");
    }

    @Test
    void nullOrBlankTextHasNoHits() {
        var matcher = SkillMatcher.of(List.of("Java"));

        assertThat(matcher.hits(null)).isEmpty();
        assertThat(matcher.hits("  ")).isEmpty();
    }

    @Test
    void variantsSplitSlashNamesOnlyWhenEveryPartIsLongEnough() {
        assertThat(SkillMatcher.variants("Hibernate/JPA")).startsWith("Hibernate/JPA", "Hibernate", "JPA");
        assertThat(SkillMatcher.variants("UI/UX")).containsExactly("UI/UX");
    }
}
