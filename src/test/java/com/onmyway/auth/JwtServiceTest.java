package com.onmyway.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    void rejectsExpiredToken() {
        JwtService jwtService = new JwtService(SECRET, -1);

        String token = jwtService.generateToken("test@example.com");

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
}
