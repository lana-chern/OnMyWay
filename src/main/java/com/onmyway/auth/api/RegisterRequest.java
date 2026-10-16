package com.onmyway.auth.api;

public record RegisterRequest(
        String email,
        String password,
        String displayName,
        String accountType
) {
    public RegisterRequest(String email, String password, String displayName) {
        this(email, password, displayName, null);
    }
}
