package com.onmyway.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationRateLimiterTest {

    @Test
    void allowsFiveAttemptsFromSameIpAndRejectsTheSixth() {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(5);

        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecord("192.0.2.10");
        }

        assertThatThrownBy(() -> limiter.checkAndRecord("192.0.2.10"))
                .isInstanceOf(TooManyRegistrationAttemptsException.class)
                .hasMessageContaining("Too many registration attempts");
    }

    @Test
    void tracksDifferentIpsIndependently() {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(5);

        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecord("192.0.2.10");
        }

        assertThatCode(() -> limiter.checkAndRecord("192.0.2.11"))
                .doesNotThrowAnyException();
    }

    @Test
    void treatsMissingIpAsOneSharedClientKey() {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(5);

        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecord(null);
        }

        assertThatThrownBy(() -> limiter.checkAndRecord(" "))
                .isInstanceOf(TooManyRegistrationAttemptsException.class);
    }
}
