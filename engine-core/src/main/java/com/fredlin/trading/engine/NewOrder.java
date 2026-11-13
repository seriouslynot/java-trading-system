package com.fredlin.trading.engine;

public record NewOrder(String clientId, String symbol, Side side, long price, long quantity,
                       String clientOrderId, TimeInForce timeInForce) {
    public NewOrder {
        if (timeInForce == null) timeInForce = TimeInForce.GTC;
    }
    public NewOrder(String clientId, String symbol, Side side, long price, long quantity, String clientOrderId) {
        this(clientId, symbol, side, price, quantity, clientOrderId, TimeInForce.GTC);
    }
    public static NewOrder limit(String clientId, String symbol, Side side, long price, long quantity) {
        return new NewOrder(clientId, symbol, side, price, quantity, null);
    }
    public static NewOrder ioc(String clientId, String symbol, Side side, long price, long quantity) {
        return new NewOrder(clientId, symbol, side, price, quantity, null, TimeInForce.IOC);
    }
}
