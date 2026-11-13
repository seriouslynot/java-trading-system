package com.fredlin.trading.engine;

public record DepthLevel(long price, long totalQuantity, int orderCount) {}
