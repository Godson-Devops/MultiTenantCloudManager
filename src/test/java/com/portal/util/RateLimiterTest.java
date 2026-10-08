package com.portal.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private static final long WINDOW = 60_000;

    @Test
    @DisplayName("permits exactly the limit, then refuses")
    void refusesOnceTheLimitIsReached() {
        String key = "test:refusesOnceTheLimitIsReached";

        for (int i = 1; i <= 10; i++) {
            assertTrue(RateLimiter.tryAcquire(key, 10, WINDOW), "attempt " + i + " should be allowed");
        }
        assertFalse(RateLimiter.tryAcquire(key, 10, WINDOW), "the eleventh attempt should be refused");
    }

    @Test
    @DisplayName("counts callers separately, so one client cannot lock out another")
    void keysAreIndependent() {
        String noisy = "test:keysAreIndependent:noisy";
        String quiet = "test:keysAreIndependent:quiet";

        for (int i = 0; i < 20; i++) {
            RateLimiter.tryAcquire(noisy, 5, WINDOW);
        }

        assertFalse(RateLimiter.tryAcquire(noisy, 5, WINDOW));
        assertTrue(RateLimiter.tryAcquire(quiet, 5, WINDOW));
    }

    @Test
    @DisplayName("a zero-length window never blocks, which keeps a misconfigured limit from locking everyone out")
    void zeroWindowNeverBlocks() {
        String key = "test:zeroWindowNeverBlocks";

        for (int i = 0; i < 50; i++) {
            assertTrue(RateLimiter.tryAcquire(key, 1, 0));
        }
    }
}
