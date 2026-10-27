package com.onmyway.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Process-local registration throttle. Five registration attempts per client IP are allowed
 * in a 15-minute window. For multi-instance deployments, use a shared store such as Redis.
 *
 * The primary map is bounded. When it is full, new IPs are routed to fixed overflow buckets;
 * collisions can conservatively throttle unrelated clients, but do not create unlimited state.
 */
@Component
public class RegistrationRateLimiter {
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_TRACKED_IPS = 10_000;
    private static final int OVERFLOW_BUCKET_COUNT = 256;

    private final Map<String, Window> windows = new HashMap<>();
    private final Window[] overflowWindows = new Window[OVERFLOW_BUCKET_COUNT];

    public synchronized void checkAndRecord(String ipAddress) {
        long now = System.currentTimeMillis();
        removeExpired(now);

        String key = normalizeIp(ipAddress);
        Window window = windows.get(key);
        int overflowBucket = bucketFor(key);
        boolean useOverflow = isActive(overflowWindows[overflowBucket], now);

        if (window != null && isLimited(window, now)) {
            throw new TooManyRegistrationAttemptsException();
        }
        if (useOverflow && isLimited(overflowWindows[overflowBucket], now)) {
            throw new TooManyRegistrationAttemptsException();
        }

        if (!useOverflow && (window != null || windows.size() < MAX_TRACKED_IPS)) {
            if (window == null) {
                window = new Window(now);
                windows.put(key, window);
            }
            window.attempts++;
            return;
        }

        Window overflow = overflowWindows[overflowBucket];
        if (overflow == null || now - overflow.startedAt >= WINDOW_MILLIS) {
            overflow = new Window(now);
            overflowWindows[overflowBucket] = overflow;
        }
        if (overflow.attempts >= MAX_ATTEMPTS) {
            throw new TooManyRegistrationAttemptsException();
        }
        overflow.attempts++;
    }

    private boolean isLimited(Window window, long now) {
        if (now - window.startedAt >= WINDOW_MILLIS) {
            return false;
        }
        return window.attempts >= MAX_ATTEMPTS;
    }

    private boolean isActive(Window window, long now) {
        return window != null && now - window.startedAt < WINDOW_MILLIS;
    }

    private int bucketFor(String key) {
        return (key.hashCode() & 0x7fffffff) % OVERFLOW_BUCKET_COUNT;
    }

    private String normalizeIp(String ipAddress) {
        return ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress.trim();
    }

    private void removeExpired(long now) {
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().startedAt >= WINDOW_MILLIS) {
                iterator.remove();
            }
        }
        for (int i = 0; i < overflowWindows.length; i++) {
            if (overflowWindows[i] != null
                    && now - overflowWindows[i].startedAt >= WINDOW_MILLIS) {
                overflowWindows[i] = null;
            }
        }
    }

    private static final class Window {
        private final long startedAt;
        private int attempts;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
