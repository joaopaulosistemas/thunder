package com.example.shop.common.security;

import org.springframework.security.core.Authentication;

/** Atalhos sobre o usuario autenticado. */
public final class Identity {

    private Identity() {
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_admin"));
    }
}
