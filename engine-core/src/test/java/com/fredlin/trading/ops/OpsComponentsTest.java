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
    @Test void defaultsSeparateBusinessDecisionsFromTimeouts() {
        var rules = AlertRule.defaults();
        var normal = new SecondStats(1, 25, 2, 18, 5, 0, 0, 500, 8, 10, 0);
        assertEquals(1, normal.admittedSuccessRate());
        assertTrue(rules.stream().noneMatch(r -> r.breached().test(normal)));
        var degraded = new SecondStats(2, 25, 2, 16, 5, 0, 2, 501, 9, 10, 1);
        assertEquals(0.9, degraded.admittedSuccessRate());
        assertEquals(java.util.List.of(10, 10, 5, 1), rules.stream().map(AlertRule::forSeconds).toList());
        assertTrue(rules.stream().allMatch(r -> r.breached().test(degraded)));
        var lowTraffic = new SecondStats(3, 19, 0, 0, 0, 0, 19, 0, 0, 10, 0);
        assertFalse(rules.getFirst().breached().test(lowTraffic));
        assertFalse(rules.get(1).breached().test(new SecondStats(3, 19, 0, 0, 0, 0, 19, 999, 0, 10, 0)));
    }
    @Test void reconciliationAlertFiresOnResultWithoutWaitingForNextSample() {
        var alerts = new AlertEvaluator(AlertRule.defaults());
        alerts.observeImmediate(AlertRule.RECONCILIATION, true, 10);
        assertEquals(AlertEvaluator.State.FIRING, alerts.states().get(AlertRule.RECONCILIATION));
        alerts.evaluate(java.util.List.of(new SecondStats(9, 0, 0, 0, 0, 0, 0, 0, 0, 10, 0)));
        assertEquals(AlertEvaluator.State.FIRING, alerts.states().get(AlertRule.RECONCILIATION));
        alerts.observeImmediate(AlertRule.RECONCILIATION, false, 10);
        assertEquals(AlertEvaluator.State.OK, alerts.states().get(AlertRule.RECONCILIATION));
    }
    @Test void pendingIdempotencyEntrySurvivesCompletedEviction() {
        var store = new IdempotencyStore(1);
        assertTrue(store.reserve("slow").isEmpty());
        var waiter = store.reserve("slow").orElseThrow();
        store.restore("old", 2);
        store.restore("new", 3);
        assertSame(waiter, store.reserve("slow").orElseThrow());
        assertTrue(store.reserve("old").isEmpty());
        store.complete("slow", 1);
        assertEquals(1, waiter.join());
    }

    @Test void cachedRetriesCountAsRequestsButNotQueueLatencySamples() {
        var time = new java.util.concurrent.atomic.AtomicLong(1000);
        var metrics = new MetricsWindow(3, time::get);
        metrics.record(Outcome.SUCCESS, 900_000_000, false);
        metrics.record(Outcome.BUSINESS_REJECTED, 5_000_000, true);
        metrics.record(Outcome.RATE_LIMITED, 100_000_000);
        time.set(2000);
        var stats = metrics.lastCompletedSeconds(1).getFirst();
        assertEquals(3, stats.requests());
        assertEquals(2, stats.admitted());
        assertEquals(1, stats.admittedSuccessRate());
        assertEquals(5, stats.p99Millis());
    }
}
