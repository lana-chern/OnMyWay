package com.onmyway.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    @Test
    void limitsRepeatedFailuresForSameEmail() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < 5; i++) {
            limiter.checkAllowed("192.0.2.10", "person@example.com");
            limiter.recordFailure("192.0.2.10", "person@example.com");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("192.0.2.10", "person@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void limitsRepeatedFailuresFromSameIpAcrossDifferentEmails() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < 20; i++) {
            String email = "person" + i + "@example.com";
            limiter.checkAllowed("192.0.2.11", email);
            limiter.recordFailure("192.0.2.11", email);
        }

        assertThatThrownBy(() -> limiter.checkAllowed("192.0.2.11", "another@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void successfulLoginClearsEmailFailuresButNotIpFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("192.0.2.12", "person@example.com");
        }

        limiter.recordSuccess("PERSON@example.com");

        assertThatCode(() -> limiter.checkAllowed("192.0.2.12", "person@example.com"))
                .doesNotThrowAnyException();
    }
}
