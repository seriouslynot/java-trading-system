package com.fredlin.trading.recon;

import com.fredlin.trading.journal.JournalRecord;
import com.fredlin.trading.ledger.*;
import java.util.*;

public final class Reconciler {
    private record Key(String ref, String account, Asset asset) {
        Key(Posting p) { this(p.reference(), p.accountId(), p.asset()); }
    }
    private record Balance(String account, Asset asset) {}
    private Reconciler() {}

    public static ReconReport reconcile(List<JournalRecord> records, List<Posting> actual, long uptoSeq) {
        return reconcile(records, actual, null, uptoSeq);
    }
    public static ReconReport reconcile(List<JournalRecord> records, List<Posting> actual,
                                        List<AccountView> accounts, long uptoSeq) {
        var expected = expectedPostings(records, uptoSeq);
        var postings = new LinkedHashMap<Key, List<Posting>>();
        int actualCount = 0;
        for (var p : actual) if (p.sequence() <= uptoSeq) {
            postings.computeIfAbsent(new Key(p), k -> new ArrayList<>(1)).add(p);
            actualCount++;
        }
        var anomalies = new ArrayList<Anomaly>();
        var balances = new LinkedHashMap<Balance, Long>();
        for (var p : expected) {
            if (accounts != null && !PostingRules.EXTERNAL.equals(p.accountId()))
                balances.merge(new Balance(p.accountId(), p.asset()), p.amount(), Math::addExact);
            var found = postings.remove(new Key(p));
            if (found == null) {
                anomalies.add(anomaly(AnomalyType.MISSING, p, p.amount(), null));
                continue;
            }
            var best = found.stream().filter(a -> a.amount() == p.amount() && a.sequence() == p.sequence())
                    .findFirst().orElse(found.getFirst());
            if (Long.signum(best.amount()) != Long.signum(p.amount()) && p.amount() != 0 && best.amount() != 0)
                anomalies.add(anomaly(AnomalyType.SIDE_MISMATCH, p, p.amount(), best.amount()));
            else if (best.amount() != p.amount())
                anomalies.add(anomaly(AnomalyType.AMOUNT_MISMATCH, p, p.amount(), best.amount()));
            else if (best.sequence() != p.sequence())
                anomalies.add(anomaly(AnomalyType.UNEXPECTED, p, p.amount(), best.amount()));
            if (found.size() > 1) anomalies.add(anomaly(AnomalyType.DUPLICATE, p, p.amount(), best.amount()));
        }
        for (var group : postings.values()) for (var p : group) {
            anomalies.add(anomaly(AnomalyType.UNEXPECTED, p, null, p.amount()));
        }
        if (accounts != null) {
            var actualBalances = new LinkedHashMap<Balance, Long>();
            for (var a : accounts) {
                actualBalances.put(new Balance(a.accountId(), Asset.CASH), a.cashTotal());
                actualBalances.put(new Balance(a.accountId(), Asset.POSITION), a.positionTotal());
            }
            var keys = new LinkedHashSet<>(balances.keySet());
            keys.addAll(actualBalances.keySet());
            for (var key : keys) {
                long a = actualBalances.getOrDefault(key, 0L);
                long e = balances.getOrDefault(key, 0L);
                if (a != e) anomalies.add(new Anomaly(AnomalyType.BALANCE_MISMATCH, null, uptoSeq, key.account(),
                        key.asset(), e, a, AnomalyType.BALANCE_MISMATCH.label()));
            }
        }
        return new ReconReport(uptoSeq, expected.size(), actualCount, anomalies);
    }
    private static Anomaly anomaly(AnomalyType type, Posting p, Long expected, Long actual) {
        return new Anomaly(type, p.reference(), p.sequence(), p.accountId(), p.asset(), expected, actual, type.label());
    }
    public static List<Posting> expectedPostings(List<JournalRecord> records, long uptoSeq) {
        var result = new ArrayList<Posting>();
        for (var r : records) {
            if (r.seq() > uptoSeq) continue;
            switch (r) {
                case JournalRecord.Deposit d -> result.addAll(PostingRules.forDeposit(d.accountId(), d.cash(), d.position(), d.seq()));
                case JournalRecord.Place p -> p.trades().forEach(t -> result.addAll(PostingRules.forTrade(t, p.seq())));
                case JournalRecord.Modify m -> m.trades().forEach(t -> result.addAll(PostingRules.forTrade(t, m.seq())));
                case JournalRecord.Cancel ignored -> {}
            }
        }
        return List.copyOf(result);
    }
}
