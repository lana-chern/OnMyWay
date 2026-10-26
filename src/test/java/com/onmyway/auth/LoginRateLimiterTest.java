package com.onmyway.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    @Test
    void progressivelyDelaysRepeatedFailuresForSameEmailWithoutHardLockout() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < 5; i++) {
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.10", "person@example.com");
            limiter.recordFailure(attempt);
        }

        LoginRateLimiter.Attempt sixth = limiter.reserveAttempt("192.0.2.10", "person@example.com");
        org.assertj.core.api.Assertions.assertThat(sixth.delayMillis()).isEqualTo(1_600L);
        limiter.recordSuccess(sixth);
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
        limiter.recordSuccess(first);
        LoginRateLimiter.Attempt second = limiter.reserveAttempt("192.0.2.21", "second@example.com");
        limiter.recordSuccess(second);

        assertThatCode(() -> {
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.20", "first@example.com");
            limiter.recordSuccess(attempt);
        }).doesNotThrowAnyException();

        assertThatCode(() -> {
            LoginRateLimiter.Attempt third = limiter.reserveAttempt("192.0.2.22", "third@example.com");
            limiter.recordSuccess(third);
        }).doesNotThrowAnyException();
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
