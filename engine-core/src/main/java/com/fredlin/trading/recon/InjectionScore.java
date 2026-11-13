package com.fredlin.trading.recon;

import java.util.*;

public record InjectionScore(int injected, int detected, int missed, int falsePositives, int balanceAnomalies,
                             double recall, double precision) {
    public static InjectionScore of(List<AnomalyInjector.Injection> manifest, ReconReport report) {
        var expected = new HashSet<>(manifest);
        int hits = 0, extra = 0, balance = 0;
        for (var a : report.anomalies()) {
            if (a.type() == AnomalyType.BALANCE_MISMATCH) { balance++; continue; }
            if (expected.remove(new AnomalyInjector.Injection(a.reference(), a.type()))) hits++;
            else extra++;
        }
        return new InjectionScore(manifest.size(), hits, manifest.size() - hits, extra, balance,
                manifest.isEmpty() ? 1 : hits / (double) manifest.size(), hits + extra == 0 ? 1 : hits / (double) (hits + extra));
    }
}
