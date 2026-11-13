package com.fredlin.trading.ops;

import java.util.List;
import java.util.function.Predicate;

public record AlertRule(String name, String description, int forSeconds, Predicate<SecondStats> breached) {
    public AlertRule {
        if (forSeconds < 1) throw new IllegalArgumentException("持续秒数必须大于 0");
    }
    public static List<AlertRule> defaults() {
        return List.of(
                new AlertRule("受理成功率", "连续 5 秒受理成功率低于 95%", 5,
                        s -> s.admitted() > 0 && s.admittedSuccessRate() < .95),
                new AlertRule("P99 延迟", "连续 5 秒 P99 超过 200 ms", 5, s -> s.p99Millis() > 200),
                new AlertRule("队列积压", "连续 5 秒队列使用率超过 80%", 5,
                        s -> s.queueCapacity() > 0 && s.queueDepth() > s.queueCapacity() * .8),
                new AlertRule("对账差异", "连续 3 秒有对账差异", 3, s -> s.reconAnomalies() > 0));
    }
}
