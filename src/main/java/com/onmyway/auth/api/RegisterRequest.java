package com.onmyway.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Information required to register a new account.")
public record RegisterRequest(
        @Schema(description = "Email address for the new account.", example = "organizer@example.com")
        String email,
        @Schema(description = "Password for the new account.", format = "password", example = "choose-a-password")
        String password,
        @Schema(description = "Name shown to other users.", example = "Alex Morgan")
        String displayName,
        @Schema(description = "Requested account type. Available values depend on the application's registration policy.", example = "ORGANIZER")
        String accountType
) {
    public RegisterRequest(String email, String password, String displayName) {
        this(email, password, displayName, null);
    }
}
