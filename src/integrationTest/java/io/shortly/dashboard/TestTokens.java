package io.shortly.dashboard;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import io.shortly.dashboard.config.SecurityConfig;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class TestTokens {

    private TestTokens() {}

    public static RequestPostProcessor userToken(UUID id) {
        Jwt jwt =
                Jwt.withTokenValue("test-token")
                        .header("alg", "none")
                        .subject(id.toString())
                        .build();
        return authentication(SecurityConfig.authenticate(jwt));
    }
}
