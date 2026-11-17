package com.fredlin.trading.engine;

import java.util.*;

final class OrderBook {
    private final NavigableMap<Long, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Long, PriceLevel> asks = new TreeMap<>();
    private final Map<Long, Order> orders = new HashMap<>();

    NavigableMap<Long, PriceLevel> levels(Side side) { return side == Side.BUY ? bids : asks; }
    Order get(long id) { return orders.get(id); }
    void add(Order order) {
        if (order.remaining <= 0) throw new IllegalArgumentException("A resting order must have remaining quantity");
        if (orders.putIfAbsent(order.id, order) != null)
            throw new IllegalStateException("Duplicate order ID: " + order.id);
        levels(order.command.side()).computeIfAbsent(order.price, price -> new PriceLevel()).add(order);
    }
    void remove(Order o) {
        var map = levels(o.command.side());
        var level = map.get(o.price);
        level.remove(o);
        if (level.queue.isEmpty()) map.remove(o.price);
        orders.remove(o.id);
    }
    void reduce(Order o, long n) { levels(o.command.side()).get(o.price).onReduced(n); }
    Order best(Side side) {
        var entry = levels(side).firstEntry();
        return entry == null ? null : entry.getValue().first();
    }
}
