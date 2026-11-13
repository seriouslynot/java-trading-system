package com.fredlin.trading.engine;

final class Order {
    final long id;
    final NewOrder command;
    long price, quantity, filled, remaining, cancelled, sequence;
    OrderStatus status = OrderStatus.NEW;

    Order(long id, NewOrder command, long sequence) {
        this.id = id;
        this.command = command;
        this.sequence = sequence;
        price = command.price();
        quantity = remaining = command.quantity();
    }
    void fill(long n) {
        remaining -= n;
        filled += n;
        status = remaining == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }
    void cancel() {
        cancelled = remaining;
        remaining = 0;
        status = OrderStatus.CANCELLED;
    }
    void reduceTo(long n) {
        quantity = n;
        remaining = n - filled;
    }
    void replace(long p, long n, long seq) {
        price = p;
        reduceTo(n);
        sequence = seq;
    }
    OrderView view() {
        return new OrderView(id, command.clientId(), command.clientOrderId(), command.symbol(), command.side(),
                price, quantity, filled, remaining, cancelled, sequence, status);
    }
}
