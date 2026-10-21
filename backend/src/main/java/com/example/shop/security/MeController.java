package com.example.shop.security;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mostra o que o backend enxergou no token: util para depurar o login. */
@RestController
@RequestMapping("/api/me")
class MeController {

    record Me(String username, String email, List<String> roles) {
    }

    @GetMapping
    Me me(Authentication authentication, @AuthenticationPrincipal Jwt jwt) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        return new Me(authentication.getName(), jwt.getClaimAsString("email"), roles);
    }
}
