package com.fredlin.trading.engine;

public record CancelResult(CancelStatus status, OrderView order, long sequence) {
    public boolean cancelled() { return status == CancelStatus.CANCELLED; }
}
