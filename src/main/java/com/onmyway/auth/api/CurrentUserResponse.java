package com.onmyway.auth.api;

import com.onmyway.data.entities.Role;

import java.util.Set;

public record CurrentUserResponse(
        Long id,
        String email,
        String displayName,
        Set<Role> roles
) {
}
