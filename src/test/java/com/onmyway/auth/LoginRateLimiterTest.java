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
    void concurrentInFlightAttemptsCannotExceedIpLimit() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        java.util.List<LoginRateLimiter.Attempt> attempts = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            attempts.add(limiter.reserveAttempt("192.0.2.12", "person" + i + "@example.com"));
        }

        assertThatThrownBy(() -> limiter.reserveAttempt("192.0.2.12", "another@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        attempts.forEach(limiter::recordSuccess);
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

        assertThatCode(() -> {
            LoginRateLimiter.Attempt third = limiter.reserveAttempt("192.0.2.22", "third@example.com");
            limiter.recordFailure(third);
        }).doesNotThrowAnyException();

        for (int i = 0; i < 19; i++) {
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.20", "another" + i + "@example.com");
            limiter.recordFailure(attempt);
        }
        assertThatThrownBy(() -> limiter.reserveAttempt("192.0.2.20", "blocked@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void fullStoreUsesBoundedOverflowBucketsInsteadOfRejectingEveryNewIp() {
        LoginRateLimiter limiter = new LoginRateLimiter(2);

        LoginRateLimiter.Attempt first = limiter.reserveAttempt("192.0.2.30", "first@example.com");
        limiter.recordFailure(first);
        LoginRateLimiter.Attempt second = limiter.reserveAttempt("192.0.2.31", "second@example.com");
        limiter.recordFailure(second);

        LoginRateLimiter.Attempt overflow = limiter.reserveAttempt("192.0.2.32", "third@example.com");
        limiter.recordFailure(overflow);

        // The same overflow IP continues using its bucket, so failures cannot be reset
        // by asking for a new email while the primary store is full.
        for (int i = 1; i < 20; i++) {
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.32", "other" + i + "@example.com");
            limiter.recordFailure(attempt);
        }
        assertThatThrownBy(() -> limiter.reserveAttempt("192.0.2.32", "blocked@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void concurrentAttemptsForSameEmailTriggerCaptchaAfterSuspiciousThreshold() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        java.util.List<LoginRateLimiter.Attempt> inFlight = new java.util.ArrayList<>();

        for (int i = 0; i < 3; i++) {
            LoginRateLimiter.Attempt attempt = limiter.reserveAttempt("192.0.2.40", "person@example.com");
            assertThat(attempt.requiresAdditionalVerification()).isFalse();
            inFlight.add(attempt);
        }

        LoginRateLimiter.Attempt thresholdAttempt =
                limiter.reserveAttempt("192.0.2.40", "person@example.com");
        assertThat(thresholdAttempt.requiresAdditionalVerification()).isTrue();

        inFlight.forEach(limiter::recordSuccess);
        limiter.recordSuccess(thresholdAttempt);
    }

    @Test
    void concurrentAttemptsFromSameIpTriggerCaptchaAfterSuspiciousThreshold() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        java.util.List<LoginRateLimiter.Attempt> inFlight = new java.util.ArrayList<>();

        for (int i = 0; i < 10; i++) {
            LoginRateLimiter.Attempt attempt =
                    limiter.reserveAttempt("192.0.2.41", "person" + i + "@example.com");
            assertThat(attempt.requiresAdditionalVerification()).isFalse();
            inFlight.add(attempt);
        }

        LoginRateLimiter.Attempt thresholdAttempt =
                limiter.reserveAttempt("192.0.2.41", "another@example.com");
        assertThat(thresholdAttempt.requiresAdditionalVerification()).isTrue();

        inFlight.forEach(limiter::recordSuccess);
        limiter.recordSuccess(thresholdAttempt);
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
