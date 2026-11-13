package com.fredlin.trading.ops;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class IdempotencyStore {
    private final int limit;
    private final Map<String, CompletableFuture<Long>> pending = new HashMap<>();
    private final LinkedHashMap<String, CompletableFuture<Long>> completed = new LinkedHashMap<>();

    public IdempotencyStore(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("幂等容量必须大于 0");
        limit = maxEntries;
    }
    public static String key(String clientId, String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isEmpty()) return null;
        return clientId.length() + ":" + clientId + clientOrderId;
    }
    public synchronized Optional<CompletableFuture<Long>> reserve(String key) {
        if (key == null) return Optional.empty();
        var prior = completed.get(key);
        if (prior == null) prior = pending.get(key);
        if (prior != null) return Optional.of(prior);
        pending.put(key, new CompletableFuture<>());
        return Optional.empty();
    }
    public void complete(String key, long orderId) {
        if (key == null) return;
        CompletableFuture<Long> future;
        synchronized (this) {
            future = pending.remove(key);
            if (future == null) return;
            completed.put(key, future);
            evict();
        }
        future.complete(orderId);
    }
    public void release(String key, String reason) {
        if (key == null) return;
        CompletableFuture<Long> future;
        synchronized (this) { future = pending.remove(key); }
        if (future != null) future.completeExceptionally(new IllegalStateException(reason));
    }
    public synchronized void restore(String key, long orderId) {
        if (key != null) {
            completed.putLast(key, CompletableFuture.completedFuture(orderId));
            evict();
        }
    }
    private void evict() { if (completed.size() > limit) completed.pollFirstEntry(); }
    public synchronized int size() { return pending.size() + completed.size(); }
}
