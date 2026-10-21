package com.example.shop.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Teste unitario puro do conversor de token do Keycloak. */
class KeycloakJwtConverterTest {

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t").header("alg", "none").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
    }

    @Test
    void mapsRealmRolesToRoleAuthoritiesAndUsesPreferredUsername() {
        Jwt jwt = token().subject("uuid-123")
                .claim("preferred_username", "alice")
                .claim("realm_access", Map.of("roles", List.of("user", "admin")))
                .build();

        var auth = KeycloakJwtConverter.create().convert(jwt);

        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_user", "ROLE_admin");
    }

    @Test
    void tokenWithoutRealmAccessHasNoAuthorities() {
        Jwt jwt = token().subject("uuid-123").claim("preferred_username", "ghost").build();

        assertThat(KeycloakJwtConverter.create().convert(jwt).getAuthorities()).isEmpty();
    }
}
