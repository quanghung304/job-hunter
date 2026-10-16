package io.github.quanghung304.jobhunter.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String API_VERSION = "0.1.0";

    @Bean
    public OpenAPI jobHunterOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Job Hunter API")
                        .version(API_VERSION)
                        .description("REST API of the Job Hunter backend; see docs/architecture/api-spec.md"))
                // A fixed relative server keeps the exported document independent of the port.
                .addServersItem(new Server().url("/"));
    }
}
