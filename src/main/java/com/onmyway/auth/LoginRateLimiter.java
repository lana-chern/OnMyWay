package com.onmyway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Process-local login throttle. For multi-instance deployments, use a shared store such as Redis.
 * IP counters are retained for the full window. When capacity is tight, idle email counters are
 * evicted first, preserving the primary per-IP brute-force limit.
 */
@Component
public class LoginRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final int MAX_IP_FAILURES = 20;
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final int SUSPICIOUS_EMAIL_FAILURES = 3;
    private static final int SUSPICIOUS_IP_FAILURES = 10;
    private static final long MAX_DELAY_MILLIS = 1_600;

    private final Map<String, AttemptWindow> attempts = new LinkedHashMap<>(128, 0.75f, true);
    private final int maxTrackedKeys;

    public LoginRateLimiter() { this(MAX_TRACKED_KEYS); }

    LoginRateLimiter(int maxTrackedKeys) {
        if (maxTrackedKeys < 2) throw new IllegalArgumentException("maxTrackedKeys must be at least 2");
        this.maxTrackedKeys = maxTrackedKeys;
    }

    public synchronized Attempt reserveAttempt(String ipAddress, String email) {
        long now = System.currentTimeMillis();
        removeExpired(now);
        String emailKey = emailKey(email);
        String ipKey = ipKey(ipAddress);
        if (isLimited(ipKey, MAX_IP_FAILURES, now)) {
            log.warn("Login rate limit reached for hashed IP key {}", safeKey(ipKey));
            throw new TooManyLoginAttemptsException();
        }
        ensureCapacityFor(emailKey, ipKey);
        AttemptWindow emailWindow = getOrCreate(emailKey, now);
        AttemptWindow ipWindow = getOrCreate(ipKey, now);
        long delayMillis = progressiveDelay(emailWindow.failures);
        if (emailWindow.failures >= SUSPICIOUS_EMAIL_FAILURES || ipWindow.failures >= SUSPICIOUS_IP_FAILURES) {
            log.warn("Suspicious login activity detected: emailFailures={}, ipFailures={}; step-up verification recommended",
                    emailWindow.failures, ipWindow.failures);
        }
        emailWindow.inFlight++;
        ipWindow.inFlight++;
        boolean requiresAdditionalVerification = emailWindow.failures >= SUSPICIOUS_EMAIL_FAILURES
                || ipWindow.failures >= SUSPICIOUS_IP_FAILURES;
        return new Attempt(emailKey, ipKey, emailWindow.startedAt, ipWindow.startedAt, delayMillis, requiresAdditionalVerification);
    }

    public void applyDelay(Attempt attempt) {
        if (attempt.delayMillis <= 0) return;
        try { Thread.sleep(attempt.delayMillis); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LoginAttemptInterruptedException();
        }
    }

    public synchronized void recordFailure(Attempt attempt) { finish(attempt, true); }
    public synchronized void recordSuccess(Attempt attempt) { finish(attempt, false); }

    private void finish(Attempt attempt, boolean failed) {
        long now = System.currentTimeMillis();
        finishKey(attempt.emailKey, attempt.emailWindowStartedAt, failed, now);
        finishKey(attempt.ipKey, attempt.ipWindowStartedAt, failed, now);
    }

    private void finishKey(String key, long startedAt, boolean failed, long now) {
        AttemptWindow window = attempts.get(key);
        if (window == null || window.startedAt != startedAt || now - startedAt >= WINDOW_MILLIS) return;
        if (window.inFlight > 0) window.inFlight--;
        if (failed) window.failures++;
        if (window.inFlight == 0 && window.failures == 0) attempts.remove(key);
    }

    private boolean isLimited(String key, int limit, long now) {
        AttemptWindow window = attempts.get(key);
        if (window == null) return false;
        if (now - window.startedAt >= WINDOW_MILLIS) { attempts.remove(key); return false; }
        return window.failures + window.inFlight >= limit;
    }

    private void ensureCapacityFor(String emailKey, String ipKey) {
        int newKeys = (attempts.containsKey(emailKey) ? 0 : 1) + (attempts.containsKey(ipKey) ? 0 : 1);
        while (attempts.size() + newKeys > maxTrackedKeys) {
            String removableKey = attempts.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("email:"))
                    .filter(entry -> !entry.getKey().equals(emailKey))
                    .filter(entry -> entry.getValue().inFlight == 0)
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (removableKey == null) {
                log.warn("Login throttle store is full; refusing an untracked IP key");
                throw new TooManyLoginAttemptsException();
            }
            attempts.remove(removableKey);
        }
    }

    private long progressiveDelay(int priorEmailFailures) {
        if (priorEmailFailures <= 0) return 0;
        int shift = Math.min(priorEmailFailures - 1, 4);
        return Math.min(100L << shift, MAX_DELAY_MILLIS);
    }

    private String safeKey(String key) { return Integer.toHexString(key.hashCode()); }

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
        while (iterator.hasNext()) if (now - iterator.next().getValue().startedAt >= WINDOW_MILLIS) iterator.remove();
    }

    private String emailKey(String email) { return "email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)); }
    private String ipKey(String ipAddress) { return "ip:" + (ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress); }

    public static final class Attempt {
        private final String emailKey, ipKey;
        private final long emailWindowStartedAt, ipWindowStartedAt, delayMillis;
        private final boolean requiresAdditionalVerification;
        private Attempt(String emailKey, String ipKey, long emailWindowStartedAt, long ipWindowStartedAt,
                        long delayMillis, boolean requiresAdditionalVerification) {
            this.emailKey = emailKey; this.ipKey = ipKey;
            this.emailWindowStartedAt = emailWindowStartedAt; this.ipWindowStartedAt = ipWindowStartedAt;
            this.delayMillis = delayMillis;
            this.requiresAdditionalVerification = requiresAdditionalVerification;
        }
        public long delayMillis() { return delayMillis; }
        public boolean requiresAdditionalVerification() { return requiresAdditionalVerification; }
    }

    private static final class AttemptWindow {
        private final long startedAt;
        private int failures, inFlight;
        private AttemptWindow(long startedAt) { this.startedAt = startedAt; }
    }
}