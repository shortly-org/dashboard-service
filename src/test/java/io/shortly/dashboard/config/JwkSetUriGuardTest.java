package io.shortly.dashboard.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwkSetUriGuardTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(JwkSetUriGuard.class);

    @Test
    void refusesToStartWhenAnHttpsIssuerGetsItsKeysOverHttp() {
        runner.withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.shortly.internal/realms/shortly",
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://keycloak-http.keycloak.svc:8080/realms/shortly/protocol/openid-connect/certs")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasRootCauseMessage(
                                                "jwk-set-uri must start with https:// when the issuer is https, but is"
                                                        + " http://keycloak-http.keycloak.svc:8080/realms/shortly/protocol/openid-connect/certs"));
    }

    @Test
    void startsWhenTheKeysComeOverHttps() {
        runner.withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.shortly.internal/realms/shortly",
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://login.shortly.internal/realms/shortly/protocol/openid-connect/certs")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startsLocallyWithHttpEverywhere() {
        runner.withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8083/realms/shortly",
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://keycloak:8083/realms/shortly/protocol/openid-connect/certs")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
