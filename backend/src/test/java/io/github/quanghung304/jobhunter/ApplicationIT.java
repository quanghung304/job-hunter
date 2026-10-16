package io.github.quanghung304.jobhunter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApplicationIT {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayAppliesTheBaselineMigration() {
        var applied = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank",
                String.class);

        assertThat(applied).contains("1");
    }

    @Test
    void healthIsUp() {
        var response = http.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void metaReturnsJson() {
        var response = http.getForEntity("/api/meta", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).contains("\"profileId\":null");
    }

    @Test
    void swaggerUiLoads() {
        var redirect = http.getForEntity("/swagger-ui.html", String.class);
        var page = http.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(redirect.getStatusCode().isError()).isFalse();
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Swagger UI");
    }

    @Test
    void clientRouteIsServedByIndexHtml() {
        var response = http.getForEntity("/jobs/1", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("test-only index.html");
    }

    @Test
    void unknownApiPathIsAProblemNotTheSpa() {
        var response = http.getForEntity("/api/does-not-exist", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("\"code\":\"NOT_FOUND\"");
    }
}
