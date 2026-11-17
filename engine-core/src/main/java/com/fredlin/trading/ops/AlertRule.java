package com.fredlin.trading.ops;

import java.util.List;
import java.util.function.Predicate;

public record AlertRule(String name, String description, int forSeconds, Predicate<SecondStats> breached) {
    public static final String RECONCILIATION = "对账差异";

    public AlertRule {
        if (forSeconds < 1) throw new IllegalArgumentException("持续秒数必须大于 0");
    }
    public static List<AlertRule> defaults() {
        return List.of(
                new AlertRule("受理成功率", "至少 20 笔受理请求，连续 10 秒受理成功率低于 95%", 10,
                        s -> s.admitted() >= 20 && s.admittedSuccessRate() < .95),
                new AlertRule("P99 延迟", "至少 20 笔受理请求，连续 10 秒 P99 超过 500 ms", 10, s -> s.admitted() >= 20 && s.p99Millis() > 500),
                new AlertRule("队列积压", "连续 5 秒队列使用率超过 80%", 5,
                        s -> s.queueCapacity() > 0 && s.queueDepth() > s.queueCapacity() * .8),
                new AlertRule(RECONCILIATION, "出现对账差异", 1, s -> s.reconAnomalies() > 0));
    }
}
