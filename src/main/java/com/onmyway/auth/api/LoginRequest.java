package com.onmyway.auth.api;

public record LoginRequest(
        String email,
        String password
) {
}
