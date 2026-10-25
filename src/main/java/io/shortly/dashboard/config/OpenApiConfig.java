package io.shortly.dashboard.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Shortly dashboard API",
                        version = "v1",
                        description =
                                "Create, manage and inspect your short links. Every endpoint"
                                        + " requires a Keycloak-issued token."),
        security = @SecurityRequirement(name = OpenApiConfig.KEYCLOAK))
@SecurityScheme(
        name = OpenApiConfig.KEYCLOAK,
        type = SecuritySchemeType.OAUTH2,
        flows =
                @OAuthFlows(
                        authorizationCode =
                                @OAuthFlow(
                                        authorizationUrl =
                                                OpenApiConfig.ISSUER
                                                        + "/protocol/openid-connect/auth",
                                        tokenUrl =
                                                OpenApiConfig.ISSUER
                                                        + "/protocol/openid-connect/token")))
public class OpenApiConfig {

    static final String KEYCLOAK = "keycloak";
    static final String ISSUER = "${spring.security.oauth2.resourceserver.jwt.issuer-uri}";

    /** Adds the 401 that Spring Security answers without a valid token to every operation. */
    @Bean
    OpenApiCustomizer unauthorizedOnEveryOperation() {
        ApiResponse unauthorized = new ApiResponse().description("Missing or invalid bearer token");
        return openApi -> {
            for (PathItem path : openApi.getPaths().values()) {
                for (Operation operation : path.readOperations()) {
                    operation.getResponses().addApiResponse("401", unauthorized);
                }
            }
        };
    }
}
