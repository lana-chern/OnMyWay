package com.onmyway.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    @Test
    void limitsRepeatedFailuresForSameEmail() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < 5; i++) {
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.10", "person@example.com");
            limiter.recordFailure(attempt);
        }

        assertThatThrownBy(() -> limiter.reserveAttempt("192.0.2.10", "person@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void limitsRepeatedFailuresFromSameIpAcrossDifferentEmails() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < 20; i++) {
            String email = "person" + i + "@example.com";
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.11", email);
            limiter.recordFailure(attempt);
        }

        assertThatThrownBy(() -> limiter.reserveAttempt("192.0.2.11", "another@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void concurrentInFlightAttemptsCannotExceedEmailLimit() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 4; i++) {
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.12", "person@example.com");
            limiter.recordFailure(attempt);
        }

        LoginRateLimiter.Attempt fifth = limiter.reserveAttempt("192.0.2.12", "person@example.com");
        assertThatThrownBy(() -> limiter.reserveAttempt("192.0.2.12", "person@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        limiter.recordSuccess(fifth);
        assertThatCode(() -> {
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.12", "person@example.com");
            limiter.recordSuccess(attempt);
        }).doesNotThrowAnyException();
    }

    @Test
    void fullStoreDoesNotBlockRequestsForAlreadyTrackedKeys() {
        LoginRateLimiter limiter = new LoginRateLimiter(4);

        LoginRateLimiter.Attempt first = limiter.reserveAttempt("192.0.2.20", "first@example.com");
        limiter.recordFailure(first);
        LoginRateLimiter.Attempt second = limiter.reserveAttempt("192.0.2.21", "second@example.com");
        limiter.recordFailure(second);

        assertThatCode(() -> {
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.20", "first@example.com");
            limiter.recordSuccess(attempt);
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> limiter.reserveAttempt("192.0.2.22", "third@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void successfulLoginDoesNotCountAsFailure() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.13", "person@example.com");
            limiter.recordSuccess(attempt);
        }

        assertThatCode(() -> {
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.13", "person@example.com");
            limiter.recordSuccess(attempt);
        }).doesNotThrowAnyException();
    }
}
