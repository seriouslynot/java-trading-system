package com.fredlin.trading.ops;

import java.util.*;

public final class AlertEvaluator {
    public enum State { OK, FIRING }
    public record AlertEvent(String rule, State state, long epochSecond, String description) {}
    private final List<AlertRule> rules;
    private final Map<String, State> states = new LinkedHashMap<>();
    private final Map<String, Integer> streak = new HashMap<>();
    private final Deque<AlertEvent> events = new ArrayDeque<>();
    private final Map<String, Long> immediateAt = new HashMap<>();
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
                if (s.epochSecond() <= immediateAt.getOrDefault(rule.name(), Long.MIN_VALUE)) continue;
                var target = rule.breached().test(s) ? State.FIRING : State.OK;
                if (target == states.get(rule.name())) streak.remove(rule.name());
                else if (streak.merge(rule.name(), 1, Integer::sum) >= rule.forSeconds()) {
                    streak.remove(rule.name());
                    transition(rule, target, last);
                }
            }
        }
    }
    public synchronized void observeImmediate(String name, boolean breached, long epochSecond) {
        var rule = rules.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
        if (rule.forSeconds() != 1) throw new IllegalArgumentException("Rule requires a sustained observation");
        immediateAt.put(name, epochSecond);
        var target = breached ? State.FIRING : State.OK;
        if (target != states.get(name)) transition(rule, target, epochSecond);
    }

    private void transition(AlertRule rule, State state, long second) {
        states.put(rule.name(), state);
        events.addLast(new AlertEvent(rule.name(), state, second, rule.description()));
        if (events.size() > 300) events.removeFirst();
    }

    public synchronized Map<String, State> states() { return Collections.unmodifiableMap(new LinkedHashMap<>(states)); }
    public synchronized List<AlertEvent> events() { return List.copyOf(events); }
    public List<AlertRule> rules() { return rules; }
}
