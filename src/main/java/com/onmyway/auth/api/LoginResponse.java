package com.onmyway.auth.api;

public record LoginResponse(
        String accessToken,
        CurrentUserResponse user
) {
}
