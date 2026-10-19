package com.onmyway.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Process-local login throttle. For multi-instance deployments, replace this with a
 * shared store (for example Redis) so attempts are counted across all instances.
 */
@Component
public class LoginRateLimiter {
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final int MAX_EMAIL_FAILURES = 5;
    private static final int MAX_IP_FAILURES = 20;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, AttemptWindow> attempts = new HashMap<>();

    public synchronized void checkAllowed(String ipAddress, String email) {
        long now = System.currentTimeMillis();
        removeExpired(now);

        String emailKey = emailKey(email);
        String ipKey = ipKey(ipAddress);
        if (isLimited(emailKey, MAX_EMAIL_FAILURES, now)
                || isLimited(ipKey, MAX_IP_FAILURES, now)
                || attempts.size() >= MAX_TRACKED_KEYS) {
            throw new TooManyLoginAttemptsException();
        }
    }

    public synchronized void recordFailure(String ipAddress, String email) {
        long now = System.currentTimeMillis();
        record(emailKey(email), MAX_EMAIL_FAILURES, now);
        record(ipKey(ipAddress), MAX_IP_FAILURES, now);
    }

    public synchronized void recordSuccess(String email) {
        attempts.remove(emailKey(email));
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
        return window.failures >= limit;
    }

    private void record(String key, int limit, long now) {
        AttemptWindow window = attempts.get(key);
        if (window == null || now - window.startedAt >= WINDOW_MILLIS) {
            attempts.put(key, new AttemptWindow(now, 1));
        } else if (window.failures < limit) {
            window.failures++;
        }
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

    private static final class AttemptWindow {
        private final long startedAt;
        private int failures;

        private AttemptWindow(long startedAt, int failures) {
            this.startedAt = startedAt;
            this.failures = failures;
        }
    }
}
