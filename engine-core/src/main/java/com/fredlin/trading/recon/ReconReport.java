package com.fredlin.trading.recon;

import java.util.*;

public record ReconReport(long uptoSeq, int expectedPostings, int actualPostings, List<Anomaly> anomalies) {
    public ReconReport { anomalies = List.copyOf(anomalies); }
    public Map<AnomalyType, Integer> countByType() {
        var counts = new EnumMap<AnomalyType, Integer>(AnomalyType.class);
        for (var a : anomalies) counts.merge(a.type(), 1, Integer::sum);
        return Collections.unmodifiableMap(counts);
    }
    public boolean clean() { return anomalies.isEmpty(); }
}
