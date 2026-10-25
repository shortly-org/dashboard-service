package io.shortly.dashboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** Refuses to start when the signing keys of an https issuer would be fetched without TLS. */
@Configuration(proxyBeanMethods = false)
final class JwkSetUriGuard {

    JwkSetUriGuard(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        if (issuerUri.startsWith("https://") && !jwkSetUri.startsWith("https://")) {
            throw new IllegalStateException(
                    "jwk-set-uri must start with https:// when the issuer is https, but is "
                            + jwkSetUri);
        }
    }
}
