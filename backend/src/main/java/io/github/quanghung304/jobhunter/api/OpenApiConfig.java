package io.github.quanghung304.jobhunter.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String API_VERSION = "0.1.0";
    private static final String NULL = "null";

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

    /**
     * A nullable object property ({@code @Schema(nullable = true)} on a record type) comes out as
     * {@code {$ref, type: "null"}}, which means "both" in OpenAPI 3.1 and loses the null in the
     * generated TypeScript types. It is rewritten to {@code anyOf: [{$ref}, {type: "null"}]}.
     */
    @Bean
    @SuppressWarnings("rawtypes") // swagger-models' property map is raw
    public OpenApiCustomizer nullableObjectReferences() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            for (Schema<?> schema : openApi.getComponents().getSchemas().values()) {
                Map<String, Schema> properties = schema.getProperties();
                if (properties != null) {
                    properties.replaceAll((name, property) -> nullableReference(property));
                }
            }
        };
    }

    private static Schema<?> nullableReference(Schema<?> property) {
        if (property.get$ref() == null || property.getTypes() == null || !property.getTypes().contains(NULL)) {
            return property;
        }
        Schema<?> nullable = new JsonSchema().anyOf(List.of(
                new JsonSchema().$ref(property.get$ref()),
                new JsonSchema().types(Set.of(NULL))));
        nullable.setDescription(property.getDescription());
        return nullable;
    }
}
