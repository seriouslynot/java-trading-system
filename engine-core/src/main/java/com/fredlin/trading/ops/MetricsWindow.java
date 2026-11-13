package com.fredlin.trading.ops;

import java.util.*;
import java.util.function.LongSupplier;

public final class MetricsWindow {
    private final Bucket[] ring;
    private final LongSupplier clock;
    private int depth, capacity, anomalies;
    private static final class Bucket {
        final long second;
        final long[] counts = new long[Outcome.values().length];
        long[] latencies = new long[128];
        int length, depth, capacity, anomalies;
        SecondStats cached;
        Bucket(long second) { this.second = second; }
        void add(Outcome outcome, long nanos) {
            counts[outcome.ordinal()]++;
            if (outcome != Outcome.RATE_LIMITED && outcome != Outcome.QUEUE_FULL) {
                if (length == latencies.length) latencies = Arrays.copyOf(latencies, Math.max(128, length * 2));
                latencies[length++] = Math.max(0, nanos);
            }
            cached = null;
        }
        SecondStats stats() {
            if (cached == null) {
                Arrays.sort(latencies, 0, length);
                double p99 = length == 0 ? 0 : latencies[(int)Math.ceil(length * 0.99) - 1] / 1e6;
                cached = new SecondStats(second, Arrays.stream(counts).sum(), counts[0], counts[1],
                        counts[2], counts[3], counts[4], p99, depth, capacity, anomalies);
                latencies = Arrays.copyOf(latencies, length);
            }
            return cached;
        }
    }
    public MetricsWindow(int seconds) { this(seconds, System::currentTimeMillis); }
    public MetricsWindow(int seconds, LongSupplier clock) {
        if (seconds <= 0) throw new IllegalArgumentException("窗口必须大于 0");
        ring = new Bucket[seconds + 1];
        this.clock = clock;
    }
    public synchronized void record(Outcome outcome, long latencyNanos) {
        current().add(outcome, latencyNanos);
    }
    public synchronized void sampleGauges(int depth, int capacity, int anomalies) {
        this.depth = depth; this.capacity = capacity; this.anomalies = anomalies;
        setGauges(current());
    }
    private void setGauges(Bucket b) {
        b.depth = depth; b.capacity = capacity; b.anomalies = anomalies;
        b.cached = null;
    }
    private Bucket current() {
        long sec = Math.floorDiv(clock.getAsLong(), 1000);
        int slot = Math.floorMod(sec, ring.length);
        var b = ring[slot];
        if (b == null || b.second != sec) {
            b = new Bucket(sec);
            setGauges(b);
            ring[slot] = b;
        }
        return b;
    }
    public synchronized List<SecondStats> lastCompletedSeconds(int seconds) {
        if (seconds < 0) throw new IllegalArgumentException("秒数不能为负");
        long current = Math.floorDiv(clock.getAsLong(), 1000);
        var list = new ArrayList<SecondStats>();
        for (long i = current - Math.min(seconds, ring.length - 1); i < current; i++) {
            var b = ring[Math.floorMod(i, ring.length)];
            list.add(b != null && b.second == i ? b.stats() : new SecondStats(i, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        }
        return List.copyOf(list);
    }
}
