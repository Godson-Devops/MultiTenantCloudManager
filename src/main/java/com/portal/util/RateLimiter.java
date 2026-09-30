package com.portal.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small fixed-window rate limiter for the login and register endpoints.
 *
 * In-process and per-node: it slows casual brute-force against a single
 * instance but is not a substitute for a shared store if the portal is
 * deployed behind more than one Tomcat.
 */
public final class RateLimiter {

    private static final Map<String, Window> WINDOWS = new ConcurrentHashMap<>();

    private RateLimiter() {
    }

    private record Window(long windowStart, int count) {
    }

    /**
     * Records a hit against {@code key} and reports whether it is allowed.
     *
     * @param limit  max hits permitted per window
     * @param windowMillis window length in milliseconds
     */
    public static boolean tryAcquire(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        Window updated = WINDOWS.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart() >= windowMillis) {
                return new Window(now, 1);
            }
            return new Window(existing.windowStart(), existing.count() + 1);
        });
        return updated.count() <= limit;
    }

    /** Test/diagnostic helper: drops all recorded state. */
    public static void reset() {
        WINDOWS.clear();
    }
}
