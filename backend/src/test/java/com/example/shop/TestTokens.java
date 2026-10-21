package com.example.shop;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Tokens de teste: simulam o que o Keycloak emitiria, sem precisar subir o Keycloak. */
public final class TestTokens {

    private TestTokens() {
    }

    public static JwtRequestPostProcessor user(String username) {
        return jwt().jwt(j -> j.subject(username).claim("preferred_username", username))
                .authorities(new SimpleGrantedAuthority("ROLE_user"));
    }

    public static JwtRequestPostProcessor admin(String username) {
        return jwt().jwt(j -> j.subject(username).claim("preferred_username", username))
                .authorities(new SimpleGrantedAuthority("ROLE_user"), new SimpleGrantedAuthority("ROLE_admin"));
    }
}
