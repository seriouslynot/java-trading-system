package com.fredlin.trading.engine;

import java.util.*;

final class OrderBook {
    private final NavigableMap<Long, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Long, PriceLevel> asks = new TreeMap<>();
    private final Map<Long, Order> orders = new HashMap<>();

    NavigableMap<Long, PriceLevel> levels(Side side) { return side == Side.BUY ? bids : asks; }
    Order get(long id) { return orders.get(id); }
    boolean add(Order o) {
        if (orders.putIfAbsent(o.id, o) != null) return false;
        levels(o.command.side()).computeIfAbsent(o.price, p -> new PriceLevel()).add(o);
        return true;
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
