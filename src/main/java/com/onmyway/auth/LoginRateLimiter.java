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
 * Per-IP counters are retained for the full window. When the bounded primary store is full,
 * untracked IPs use a fixed number of overflow buckets; hash collisions may conservatively
 * rate-limit unrelated IPs, but do not allow the IP limit to be bypassed by store exhaustion.
 */
@Component
public class LoginRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final int MAX_IP_FAILURES = 20;
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final int OVERFLOW_IP_BUCKET_COUNT = 256;
    private static final int SUSPICIOUS_EMAIL_FAILURES = 3;
    private static final int SUSPICIOUS_IP_FAILURES = 10;
    private static final long MAX_DELAY_MILLIS = 1_600;

    private final Map<String, AttemptWindow> attempts = new LinkedHashMap<>(128, 0.75f, true);
    private final AttemptWindow[] overflowIpAttempts = new AttemptWindow[OVERFLOW_IP_BUCKET_COUNT];
    private final int maxTrackedKeys;

    public LoginRateLimiter() { this(MAX_TRACKED_KEYS); }

    LoginRateLimiter(int maxTrackedKeys) {
        if (maxTrackedKeys < 2) throw new IllegalArgumentException("maxTrackedKeys must be at least 2");
        this.maxTrackedKeys = maxTrackedKeys;
    }

    public synchronized Attempt reserveAttempt(String ipAddress, String email) {
        long now = System.currentTimeMillis();
        removeExpired(now);
        String requestedEmailKey = emailKey(email);
        String requestedIpKey = ipKey(ipAddress);
        int overflowBucket = overflowBucketFor(requestedIpKey);

        // Once a bucket is active, route all IPs hashing to it through that bucket until its
        // window expires. This prevents an overflowed IP from switching back to a fresh counter.
        boolean useOverflow = isActive(overflowIpAttempts[overflowBucket], now);
        AttemptWindow knownIpWindow = attempts.get(requestedIpKey);
        if (!useOverflow && knownIpWindow != null && isLimited(requestedIpKey, MAX_IP_FAILURES, now)) {
            log.warn("Login rate limit reached for hashed IP key {}", safeKey(requestedIpKey));
            throw new TooManyLoginAttemptsException();
        }
        if (useOverflow && isLimited(overflowBucket, MAX_IP_FAILURES, now)) {
            log.warn("Login rate limit reached for overflow IP bucket {}", overflowBucket);
            throw new TooManyLoginAttemptsException();
        }

        // Free email-only entries first. IP counters are never evicted to make room.
        while (requiredSlots(requestedEmailKey, requestedIpKey) > maxTrackedKeys - attempts.size()) {
            String removableKey = attempts.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("email:"))
                    .filter(entry -> !entry.getKey().equals(requestedEmailKey))
                    .filter(entry -> entry.getValue().inFlight == 0)
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (removableKey == null) break;
            attempts.remove(removableKey);
        }

        boolean trackIp = !useOverflow && attempts.containsKey(requestedIpKey);
        if (!useOverflow && !trackIp && attempts.size() < maxTrackedKeys) {
            attempts.put(requestedIpKey, new AttemptWindow(now));
            trackIp = true;
        }
        if (!trackIp) useOverflow = true;

        // IP tracking has priority over per-email tracking when the store is full.
        String trackedEmailKey = requestedEmailKey;
        if (!attempts.containsKey(trackedEmailKey) && attempts.size() >= maxTrackedKeys) {
            trackedEmailKey = null;
        }
        AttemptWindow emailWindow = trackedEmailKey == null ? null : getOrCreate(trackedEmailKey, now);
        AttemptWindow ipWindow;
        if (useOverflow) {
            overflowBucket = overflowBucketFor(requestedIpKey);
            ipWindow = getOrCreateOverflow(overflowBucket, now);
        } else {
            ipWindow = attempts.get(requestedIpKey);
        }

        long emailFailures = emailWindow == null ? 0 : emailWindow.failures;
        long ipFailures = ipWindow.failures;
        long delayMillis = progressiveDelay((int) emailFailures);
        if (emailFailures >= SUSPICIOUS_EMAIL_FAILURES || ipFailures >= SUSPICIOUS_IP_FAILURES) {
            log.warn("Suspicious login activity detected: emailFailures={}, ipFailures={}; step-up verification recommended",
                    emailFailures, ipFailures);
        }
        if (emailWindow != null) emailWindow.inFlight++;
        ipWindow.inFlight++;
        boolean requiresAdditionalVerification = emailFailures >= SUSPICIOUS_EMAIL_FAILURES
                || ipFailures >= SUSPICIOUS_IP_FAILURES;
        return new Attempt(trackedEmailKey, useOverflow ? null : requestedIpKey,
                trackedEmailKey == null ? -1 : emailWindow.startedAt, ipWindow.startedAt,
                useOverflow ? overflowBucket : -1, delayMillis, requiresAdditionalVerification);
    }

    private int requiredSlots(String emailKey, String ipKey) {
        int slots = 0;
        if (!attempts.containsKey(ipKey)) slots++;
        if (!attempts.containsKey(emailKey) && !emailKey.equals(ipKey)) slots++;
        return slots;
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
        if (attempt.emailKey != null) finishKey(attempt.emailKey, attempt.emailWindowStartedAt, failed, now);
        if (attempt.overflowBucket >= 0) {
            finishOverflow(attempt.overflowBucket, attempt.ipWindowStartedAt, failed, now);
        } else {
            finishKey(attempt.ipKey, attempt.ipWindowStartedAt, failed, now);
        }
    }

    private void finishKey(String key, long startedAt, boolean failed, long now) {
        AttemptWindow window = attempts.get(key);
        if (window == null || window.startedAt != startedAt || now - startedAt >= WINDOW_MILLIS) return;
        finishWindow(window, failed);
        if (window.inFlight == 0 && window.failures == 0) attempts.remove(key);
    }

    private void finishOverflow(int bucket, long startedAt, boolean failed, long now) {
        AttemptWindow window = overflowIpAttempts[bucket];
        if (window == null || window.startedAt != startedAt || now - startedAt >= WINDOW_MILLIS) return;
        finishWindow(window, failed);
        if (window.inFlight == 0 && window.failures == 0) overflowIpAttempts[bucket] = null;
    }

    private void finishWindow(AttemptWindow window, boolean failed) {
        if (window.inFlight > 0) window.inFlight--;
        if (failed) window.failures++;
    }

    private boolean isLimited(String key, int limit, long now) {
        AttemptWindow window = attempts.get(key);
        if (window == null) return false;
        if (now - window.startedAt >= WINDOW_MILLIS) { attempts.remove(key); return false; }
        return window.failures + window.inFlight >= limit;
    }

    private boolean isLimited(int bucket, int limit, long now) {
        AttemptWindow window = overflowIpAttempts[bucket];
        if (window == null) return false;
        if (now - window.startedAt >= WINDOW_MILLIS) { overflowIpAttempts[bucket] = null; return false; }
        return window.failures + window.inFlight >= limit;
    }

    private boolean isActive(AttemptWindow window, long now) {
        return window != null && now - window.startedAt < WINDOW_MILLIS;
    }

    private long progressiveDelay(int priorEmailFailures) {
        if (priorEmailFailures <= 0) return 0;
        int shift = Math.min(priorEmailFailures - 1, 4);
        return Math.min(100L << shift, MAX_DELAY_MILLIS);
    }

    private int overflowBucketFor(String ipKey) {
        return (ipKey.hashCode() & 0x7fffffff) % OVERFLOW_IP_BUCKET_COUNT;
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

    private AttemptWindow getOrCreateOverflow(int bucket, long now) {
        AttemptWindow window = overflowIpAttempts[bucket];
        if (window == null || now - window.startedAt >= WINDOW_MILLIS) {
            window = new AttemptWindow(now);
            overflowIpAttempts[bucket] = window;
        }
        return window;
    }

    private void removeExpired(long now) {
        Iterator<Map.Entry<String, AttemptWindow>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) if (now - iterator.next().getValue().startedAt >= WINDOW_MILLIS) iterator.remove();
        for (int i = 0; i < overflowIpAttempts.length; i++) {
            if (overflowIpAttempts[i] != null && now - overflowIpAttempts[i].startedAt >= WINDOW_MILLIS) {
                overflowIpAttempts[i] = null;
            }
        }
    }

    private String emailKey(String email) { return "email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)); }
    private String ipKey(String ipAddress) { return "ip:" + (ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress); }

    public static final class Attempt {
        private final String emailKey, ipKey;
        private final long emailWindowStartedAt, ipWindowStartedAt, delayMillis;
        private final int overflowBucket;
        private final boolean requiresAdditionalVerification;
        private Attempt(String emailKey, String ipKey, long emailWindowStartedAt, long ipWindowStartedAt,
                        int overflowBucket, long delayMillis, boolean requiresAdditionalVerification) {
            this.emailKey = emailKey; this.ipKey = ipKey;
            this.emailWindowStartedAt = emailWindowStartedAt; this.ipWindowStartedAt = ipWindowStartedAt;
            this.overflowBucket = overflowBucket; this.delayMillis = delayMillis;
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