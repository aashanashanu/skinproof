package com.skinproof.api.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Configuration
public class SecurityConfiguration {
    private static final OAuth2Error INVALID_CLIENT = new OAuth2Error("invalid_token", "Token client is not allowed.", null);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/api/ingredient-check", "/api/analysis/skin", "/api/shared-reports/**", "/swagger-ui.html", "/swagger-ui/**", "/api-docs/**")
                        .permitAll()
                        .anyRequest().access((authentication, context) -> {
                            if (authentication.get() instanceof JwtAuthenticationToken) {
                                return new AuthorizationDecision(true);
                            }
                            String demoWorkspace = context.getRequest().getHeader("X-Demo-Workspace");
                            try {
                                UUID.fromString(demoWorkspace);
                                return new AuthorizationDecision(true);
                            } catch (IllegalArgumentException | NullPointerException exception) {
                                return new AuthorizationDecision(false);
                            }
                        }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED)))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${skinproof.auth.issuer-uri:}") String issuer,
            @Value("${skinproof.auth.client-id:}") String clientId) {
        if (issuer.isBlank() || clientId.isBlank()) {
            return token -> {
                throw new org.springframework.security.oauth2.jwt.JwtException("Cognito authentication is not configured.");
            };
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> clientValidator = token -> {
            String tokenClientId = token.getClaimAsString("client_id");
            String tokenUse = token.getClaimAsString("token_use");
            List<String> audience = token.getAudience();
            boolean allowed = "access".equals(tokenUse)
                    && (clientId.equals(tokenClientId) || (audience != null && audience.contains(clientId)));
            return allowed ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID_CLIENT);
        };
        decoder.setJwtValidator(token -> {
            OAuth2TokenValidatorResult issuerResult = issuerValidator.validate(token);
            if (issuerResult.hasErrors()) return issuerResult;
            return clientValidator.validate(token);
        });
        return decoder;
    }
}
