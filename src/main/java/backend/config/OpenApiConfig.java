package backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 *
 * java-pro: Bean-driven config — springdoc auto-scans all @RestController + @Operation.
 * To disable Swagger on production, add to application-prod.yml:
 *   springdoc.swagger-ui.enabled: false
 *   springdoc.api-docs.enabled: false
 *
 * Swagger UI : GET /swagger-ui.html   (SecurityConfig.permitAll)
 * OpenAPI JSON: GET /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .addServersItem(new Server().url("http://localhost:8080").description("Local dev"))
                .addServersItem(new Server().url(baseUrl).description("Deployed"))
                .info(new Info()
                        .title("TAPO Store API")
                        .description("REST API cho Tapo E-commerce Platform. " +
                                "Authenticate: POST /api/auth/login → copy accessToken → click Authorize.")
                        .version("1.0")
                        .contact(new Contact()
                                .name("TAPO Team")
                                .email("support@tapo.vn")))
                // Global security requirement — hiện nút "Authorize" trên toàn bộ Swagger UI
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Nhập Access Token (không cần prefix 'Bearer '). " +
                                        "Lấy từ POST /api/auth/login → data.accessToken")));
    }
}
