package com.portal.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {

    private static final Map<String, Window> WINDOWS = new ConcurrentHashMap<>();

    private RateLimiter() {
    }

    private record Window(long windowStart, int count) {
    }

    public static boolean tryAcquire(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        Window current = WINDOWS.get(key);
        Window next = current == null || now - current.windowStart() >= windowMillis
                ? new Window(now, 1)
                : new Window(current.windowStart(), current.count() + 1);
        WINDOWS.put(key, next);
        return next.count() <= limit;
    }
}
