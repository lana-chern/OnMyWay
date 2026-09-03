package com.onmyway.auth.api;

public record RegisterRequest(
        String email,
        String password,
        String displayName
) {
}
