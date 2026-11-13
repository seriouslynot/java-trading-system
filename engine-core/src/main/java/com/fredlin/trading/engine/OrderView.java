package com.fredlin.trading.engine;

public record OrderView(long orderId, String clientId, String clientOrderId, String symbol, Side side,
                        long price, long quantity, long filledQuantity, long remainingQuantity,
                        long cancelledQuantity, long sequence, OrderStatus status) {
    static OrderView rejected(long id, NewOrder c, long seq) {
        return new OrderView(id, c.clientId(), c.clientOrderId(), c.symbol(), c.side(),
                c.price(), c.quantity(), 0, 0, c.quantity(), seq, OrderStatus.REJECTED);
    }
}
