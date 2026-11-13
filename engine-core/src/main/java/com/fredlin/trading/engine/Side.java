package com.fredlin.trading.engine;

public enum Side {
    BUY, SELL;
    public Side opposite() { return this == BUY ? SELL : BUY; }
}
