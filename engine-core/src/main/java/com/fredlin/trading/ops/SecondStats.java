package com.fredlin.trading.ops;

public record SecondStats(long epochSecond, long requests, long success, long businessRejected,
                          long rateLimited, long queueFull, long timeouts, double p99Millis,
                          int queueDepth, int queueCapacity, int reconAnomalies) {
    public long admitted() { return success + businessRejected + timeouts; }
    public double admittedSuccessRate() { return admitted() == 0 ? 1 : (success + businessRejected) / (double) admitted(); }
    public double fastRejectRate() { return requests == 0 ? 0 : (rateLimited + queueFull) / (double) requests; }
}
