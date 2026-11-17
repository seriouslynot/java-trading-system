package com.fredlin.trading.recon;

import java.util.*;

public record InjectionScore(int injected, int detected, int missed, int falsePositives, int balanceAnomalies,
                             double recall, double precision, Map<AnomalyType, Integer> injectedByType,
                             Map<AnomalyType, Integer> detectedByType) {
    public InjectionScore {
        injectedByType = Map.copyOf(injectedByType);
        detectedByType = Map.copyOf(detectedByType);
    }

    public static InjectionScore of(List<AnomalyInjector.Injection> manifest, ReconReport report) {
        var expected = new HashSet<>(manifest);
        var reported = new HashSet<AnomalyInjector.Injection>();
        var injectedTypes = new EnumMap<AnomalyType, Integer>(AnomalyType.class);
        var detectedTypes = new EnumMap<AnomalyType, Integer>(AnomalyType.class);
        expected.forEach(event -> injectedTypes.merge(event.type(), 1, Integer::sum));
        int hits = 0, extra = 0, balance = 0;
        for (var a : report.anomalies()) {
            if (a.type() == AnomalyType.BALANCE_MISMATCH) {
                balance++;
                continue;
            }
            var event = new AnomalyInjector.Injection(a.reference(), a.type());
            if (!reported.add(event)) continue;
            if (expected.contains(event)) {
                hits++;
                detectedTypes.merge(a.type(), 1, Integer::sum);
            } else {
                extra++;
            }
        }
        return new InjectionScore(expected.size(), hits, expected.size() - hits, extra, balance,
                expected.isEmpty() ? 1 : hits / (double) expected.size(),
                hits + extra == 0 ? 1 : hits / (double) (hits + extra), injectedTypes, detectedTypes);
    }
}
