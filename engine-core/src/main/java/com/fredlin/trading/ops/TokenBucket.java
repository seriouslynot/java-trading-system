package com.fredlin.trading.ops;

import java.util.function.LongSupplier;

public final class TokenBucket {
    private final LongSupplier clock;
    private long capacity, refill, last;
    private double tokens;

    public TokenBucket(long capacity, long refillPerSecond) { this(capacity, refillPerSecond, System::nanoTime); }
    public TokenBucket(long capacity, long refillPerSecond, LongSupplier clock) {
        validate(capacity, refillPerSecond);
        this.clock = clock;
        this.capacity = capacity;
        this.refill = refillPerSecond;
        this.tokens = capacity;
        this.last = clock.getAsLong();
    }
    private static void validate(long cap, long rate) {
        if (cap <= 0 || rate <= 0) throw new IllegalArgumentException("令牌桶容量和速率必须大于 0");
    }
    private void refill() {
        long now = clock.getAsLong();
        if (now - last <= 0) return;
        tokens = Math.min(capacity, tokens + (now - last) / 1e9 * refill);
        last = now;
    }
    public synchronized boolean tryAcquire() {
        refill();
        if (tokens < 1) return false;
        tokens--;
        return true;
    }
    public synchronized void setRate(long capacity, long refillPerSecond) {
        validate(capacity, refillPerSecond);
        refill();
        this.capacity = capacity;
        this.refill = refillPerSecond;
        tokens = Math.min(tokens, capacity);
    }
    public synchronized double availableTokens() { refill(); return tokens; }
    public synchronized long capacity() { return capacity; }
    public synchronized long refillPerSecond() { return refill; }
}
