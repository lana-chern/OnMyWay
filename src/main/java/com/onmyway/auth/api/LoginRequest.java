package com.onmyway.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credentials used to obtain a JWT access token.")
public record LoginRequest(
        @Schema(description = "Email address associated with the account.", example = "organizer@example.com")
        String email,
        @Schema(description = "Account password.", format = "password", example = "your-password")
        String password,
        @Schema(description = "Cloudflare Turnstile response token, required after suspicious login activity.", example = "0.xxxxx")
        String captchaToken
) {
}
