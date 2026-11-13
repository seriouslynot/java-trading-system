package com.fredlin.trading.ops;

import java.util.*;

public final class AlertEvaluator {
    public enum State { OK, FIRING }
    public record AlertEvent(String rule, State state, long epochSecond, String description) {}
    private final List<AlertRule> rules;
    private final Map<String, State> states = new LinkedHashMap<>();
    private final Map<String, Integer> streak = new HashMap<>();
    private final Deque<AlertEvent> events = new ArrayDeque<>();
    private long last = Long.MIN_VALUE;

    public AlertEvaluator(List<AlertRule> rules) {
        this.rules = List.copyOf(rules);
        for (var rule : rules) states.put(rule.name(), State.OK);
    }
    public synchronized void evaluate(List<SecondStats> recent) {
        for (var s : recent) {
            if (s.epochSecond() <= last) continue;
            if (last != s.epochSecond() - 1) streak.clear();
            last = s.epochSecond();
            for (var rule : rules) {
                var target = rule.breached().test(s) ? State.FIRING : State.OK;
                if (target == states.get(rule.name())) streak.remove(rule.name());
                else if (streak.merge(rule.name(), 1, Integer::sum) >= rule.forSeconds()) {
                    streak.remove(rule.name());
                    states.put(rule.name(), target);
                    events.addLast(new AlertEvent(rule.name(), target, last, rule.description()));
                    if (events.size() > 300) events.removeFirst();
                }
            }
        }
    }
    public synchronized Map<String, State> states() { return Collections.unmodifiableMap(new LinkedHashMap<>(states)); }
    public synchronized List<AlertEvent> events() { return List.copyOf(events); }
    public List<AlertRule> rules() { return rules; }
}
