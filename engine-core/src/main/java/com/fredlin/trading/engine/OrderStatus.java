package com.fredlin.trading.engine;

public enum OrderStatus {
    NEW, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED;
    public boolean isTerminal() { return this == FILLED || this == CANCELLED || this == REJECTED; }
}
