package com.example.shop.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Traduz o token do Keycloak para o modelo do Spring Security:
 * - roles de realm (claim realm_access.roles) viram authorities ROLE_<role>;
 * - o nome do usuario passa a ser o claim preferred_username (em vez do UUID "sub").
 */
public final class KeycloakJwtConverter {

    private KeycloakJwtConverter() {
    }

    public static Converter<Jwt, AbstractAuthenticationToken> create() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(KeycloakJwtConverter::authorities);
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map) || !(map.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
