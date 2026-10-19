package com.onmyway.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Refresh token used to obtain a new access token.")
public record RefreshTokenRequest(
        @Schema(description = "Opaque refresh token returned by login or refresh.", example = "opaque-refresh-token")
        String refreshToken
) {
}
