package com.fredlin.trading.ops;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class OpsComponentsTest {
    @Test void bucketRefillsAndClamps() {
        var now = new AtomicLong();
        var bucket = new TokenBucket(2, 10, now::get);
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
        now.addAndGet(100_000_000);
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
        now.addAndGet(10_000_000_000L);
        assertEquals(2, bucket.availableTokens());
    }
    @Test void dedupeCoalescesAndFailedReservationCanRetry() {
        var ids = new IdempotencyStore(1);
        assertTrue(ids.reserve("a").isEmpty());
        var waiter = ids.reserve("a").orElseThrow();
        ids.release("a", "rate limit");
        assertTrue(waiter.isCompletedExceptionally());
        assertTrue(ids.reserve("a").isEmpty());
        var r = ids.reserve("a").orElseThrow();
        ids.complete("a", 5);
        assertEquals(5L, r.join());
        ids.restore("b", 6);
        assertTrue(ids.reserve("a").isEmpty());
    }
    @Test void latencyExcludesFastRejectsAndAlertsRequireConsecutiveSeconds() {
        var now = new AtomicLong();
        var m = new MetricsWindow(5, now::get);
        var a = new AlertEvaluator(List.of(new AlertRule("slow", "", 2, s -> s.p99Millis() > 50)));
        for (int i = 0; i < 2; i++) {
            m.record(Outcome.SUCCESS, 60_000_000);
            m.record(Outcome.RATE_LIMITED, 99_000_000);
            now.addAndGet(1000);
            a.evaluate(m.lastCompletedSeconds(5));
        }
        assertEquals(60, m.lastCompletedSeconds(1).getFirst().p99Millis());
        assertEquals(1, m.lastCompletedSeconds(1).getFirst().admittedSuccessRate());
        assertEquals(AlertEvaluator.State.FIRING, a.states().get("slow"));
        now.addAndGet(2000);
        a.evaluate(m.lastCompletedSeconds(5));
        assertEquals(AlertEvaluator.State.OK, a.states().get("slow"));
        assertEquals(2, a.events().size());
    }
}
