package com.fredlin.trading.engine;

import java.util.LinkedHashMap;

final class PriceLevel {
    final LinkedHashMap<Long, Order> queue = new LinkedHashMap<>();
    long total;

    void add(Order order) {
        queue.put(order.id, order);
        total += order.remaining;
    }
    void remove(Order order) {
        queue.remove(order.id);
        total -= order.remaining;
    }
    void onReduced(long n) { total -= n; }
    Order first() { return queue.firstEntry().getValue(); }
}
