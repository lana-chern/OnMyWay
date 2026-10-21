package com.onmyway.auth.api;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        CurrentUserResponse user
) {
}
