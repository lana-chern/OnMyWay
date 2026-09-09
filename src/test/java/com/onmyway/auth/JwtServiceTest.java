package com.onmyway.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class JwtServiceTest {
    private static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Test
    void generatesTokenAndExtractsEmail() {
        JwtService jwtService = new JwtService(SECRET, 900_000);

        String token = jwtService.generateToken("test@example.com");

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo("test@example.com");
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtService jwtService = new JwtService(SECRET, 1);

        String token = jwtService.generateToken("test@example.com");
        Thread.sleep(10);

        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService issuer = new JwtService(SECRET, 900_000);
        JwtService verifier = new JwtService(
                "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo0NTY3ODkwMTIzNDU2Nzg=",
                900_000
        );

        String token = issuer.generateToken("test@example.com");

        assertThat(verifier.isValid(token)).isFalse();
    }

    @Test
    void rejectsMissingSecret() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new JwtService("", 900_000))
                .withMessage("OMW_JWT_SECRET is not configured");
    }

    @Test
    void rejectsInvalidBase64Secret() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new JwtService("not-base64", 900_000))
                .withMessage("OMW_JWT_SECRET must be a valid Base64-encoded key");
    }

    @Test
    void rejectsTooShortSecret() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new JwtService("YWJjZA==", 900_000))
                .withMessage("OMW_JWT_SECRET must be a valid Base64-encoded key");
    }

    @Test
    void rejectsNonPositiveExpiration() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new JwtService(SECRET, 0))
                .withMessage("OMW_JWT_EXPIRATION_MS must be greater than 0");

        assertThatIllegalStateException()
                .isThrownBy(() -> new JwtService(SECRET, -1))
                .withMessage("OMW_JWT_EXPIRATION_MS must be greater than 0");
    }
}
