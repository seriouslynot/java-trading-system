package com.fredlin.trading.engine;

public record Trade(long tradeId, long sequence, String symbol, long price, long quantity,
                    long buyOrderId, String buyClientId, long sellOrderId, String sellClientId, Side takerSide) {
    public long amount() { return Math.multiplyExact(price, quantity); }
}
