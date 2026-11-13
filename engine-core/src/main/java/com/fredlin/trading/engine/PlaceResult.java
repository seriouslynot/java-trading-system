package com.fredlin.trading.engine;

import java.util.List;

public record PlaceResult(OrderView order, List<Trade> trades, String reason) {
    public PlaceResult { trades = List.copyOf(trades); }
    public boolean accepted() { return order.status() != OrderStatus.REJECTED; }
}
