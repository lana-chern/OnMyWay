package com.onmyway.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Process-local login throttle. For multi-instance deployments, use a shared store such as Redis.
 */
@Component
public class LoginRateLimiter {
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final int MAX_EMAIL_FAILURES = 5;
    private static final int MAX_IP_FAILURES = 20;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, AttemptWindow> attempts = new HashMap<>();
    private final int maxTrackedKeys;

    public LoginRateLimiter() {
        this(MAX_TRACKED_KEYS);
    }

    LoginRateLimiter(int maxTrackedKeys) {
        if (maxTrackedKeys < 2) {
            throw new IllegalArgumentException("maxTrackedKeys must be at least 2");
        }
        this.maxTrackedKeys = maxTrackedKeys;
    }

    public synchronized Attempt reserveAttempt(String ipAddress, String email) {
        long now = System.currentTimeMillis();
        removeExpired(now);
        String emailKey = emailKey(email);
        String ipKey = ipKey(ipAddress);
        if (isLimited(emailKey, MAX_EMAIL_FAILURES, now)
                || isLimited(ipKey, MAX_IP_FAILURES, now)) {
            throw new TooManyLoginAttemptsException();
        }
        ensureCapacityFor(emailKey, ipKey);
        AttemptWindow emailWindow = getOrCreate(emailKey, now);
        AttemptWindow ipWindow = getOrCreate(ipKey, now);
        emailWindow.inFlight++;
        ipWindow.inFlight++;
        return new Attempt(emailKey, ipKey, emailWindow.startedAt, ipWindow.startedAt);
    }

    public synchronized void recordFailure(Attempt attempt) {
        finish(attempt, true);
    }

    public synchronized void recordSuccess(Attempt attempt) {
        finish(attempt, false);
    }

    private void finish(Attempt attempt, boolean failed) {
        long now = System.currentTimeMillis();
        finishKey(attempt.emailKey, attempt.emailWindowStartedAt, failed, MAX_EMAIL_FAILURES, now);
        finishKey(attempt.ipKey, attempt.ipWindowStartedAt, failed, MAX_IP_FAILURES, now);
    }

    private void finishKey(String key, long startedAt, boolean failed, int limit, long now) {
        AttemptWindow window = attempts.get(key);
        if (window == null || window.startedAt != startedAt || now - startedAt >= WINDOW_MILLIS) {
            return;
        }
        if (window.inFlight > 0) {
            window.inFlight--;
        }
        if (failed && window.failures < limit) {
            window.failures++;
        }
        if (window.inFlight == 0 && window.failures == 0) {
            attempts.remove(key);
        }
    }

    private boolean isLimited(String key, int limit, long now) {
        AttemptWindow window = attempts.get(key);
        if (window == null) {
            return false;
        }
        if (now - window.startedAt >= WINDOW_MILLIS) {
            attempts.remove(key);
            return false;
        }
        return window.failures + window.inFlight >= limit;
    }

    private void ensureCapacityFor(String emailKey, String ipKey) {
        int newKeys = (attempts.containsKey(emailKey) ? 0 : 1)
                + (attempts.containsKey(ipKey) ? 0 : 1);
        while (attempts.size() + newKeys > maxTrackedKeys) {
            String removableKey = attempts.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(emailKey) && !entry.getKey().equals(ipKey))
                    .filter(entry -> entry.getValue().inFlight == 0 && entry.getValue().failures == 0)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (removableKey == null) {
                throw new TooManyLoginAttemptsException();
            }
            attempts.remove(removableKey);
        }
    }

    private AttemptWindow getOrCreate(String key, long now) {
        AttemptWindow window = attempts.get(key);
        if (window == null || now - window.startedAt >= WINDOW_MILLIS) {
            window = new AttemptWindow(now);
            attempts.put(key, window);
        }
        return window;
    }

    private void removeExpired(long now) {
        Iterator<Map.Entry<String, AttemptWindow>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().startedAt >= WINDOW_MILLIS) {
                iterator.remove();
            }
        }
    }

    private String emailKey(String email) {
        return "email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    private String ipKey(String ipAddress) {
        return "ip:" + (ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress);
    }

    public static final class Attempt {
        private final String emailKey;
        private final String ipKey;
        private final long emailWindowStartedAt;
        private final long ipWindowStartedAt;

        private Attempt(String emailKey, String ipKey, long emailWindowStartedAt, long ipWindowStartedAt) {
            this.emailKey = emailKey;
            this.ipKey = ipKey;
            this.emailWindowStartedAt = emailWindowStartedAt;
            this.ipWindowStartedAt = ipWindowStartedAt;
        }
    }

    private static final class AttemptWindow {
        private final long startedAt;
        private int failures;
        private int inFlight;

        private AttemptWindow(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
