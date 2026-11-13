package com.fredlin.trading.app.api;

import com.fredlin.trading.engine.*;
import com.fredlin.trading.ops.Outcome;
import java.util.List;

public final class Dtos {
    private Dtos() {}
    public record PlaceRequest(String clientId, Side side, Long price, Long quantity, String clientOrderId) {}
    public record PlaceResponse(Outcome outcome, OrderView order, List<Trade> trades, String reason, boolean duplicate) {}
    public record CancelResponse(Outcome outcome, CancelStatus status, OrderView order, String reason) {}
    public record Injection(Double rate) {}
    public record Experiment(Integer orders, Double rate, Long seed) {}
    public record Protection(Boolean rateLimitEnabled, Boolean queueLimitEnabled, Boolean cancelPriorityEnabled,
                             Integer queueCapacity, Long rateLimitBurst, Long rateLimitPerSecond) {}
    public record Surge(Integer ratePerSecond, Integer seconds) {}
    public record Error(String reason) {}
}
