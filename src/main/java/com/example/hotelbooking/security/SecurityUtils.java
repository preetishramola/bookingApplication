package com.example.hotelbooking.security;

import com.example.hotelbooking.enums.Role;
import org.springframework.security.core.Authentication;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> Role.ROLE_ADMIN.name().equals(authority.getAuthority()));
    }
}
