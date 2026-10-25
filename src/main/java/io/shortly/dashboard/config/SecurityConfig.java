package io.shortly.dashboard.config;

import io.shortly.dashboard.model.CurrentUser;
import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain managementSecurity(HttpSecurity http) {
        return http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(EndpointRequest.to("health", "prometheus"))
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .build();
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll()
                                        .requestMatchers("/api/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        SecurityConfig::authenticate)))
                .build();
    }

    /** Turns a verified token into an authentication whose principal is the {@link CurrentUser}. */
    public static AbstractAuthenticationToken authenticate(Jwt jwt) {
        return new PreAuthenticatedAuthenticationToken(
                new CurrentUser(userIdOf(jwt)), jwt, List.of());
    }

    private static UUID userIdOf(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new InvalidBearerTokenException("subject is not a user id");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("subject is not a user id");
        }
    }
}
