package com.fredlin.trading.recon;

import com.fredlin.trading.engine.*;
import com.fredlin.trading.journal.JournalRecord;
import com.fredlin.trading.ledger.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReconcilerTest {
    private final Trade trade = new Trade(1, 1, "DEMO", 100, 5, 1, "A", 2, "B", Side.BUY);
    private final List<JournalRecord> log = List.of(new JournalRecord.Modify(1, 1, "A", 100, 5, ModifyStatus.MODIFIED, List.of(trade)));
    @Test void allPostingFailureKindsAndCutoff() {
        var good = PostingRules.forTrade(trade, 1);
        assertTrue(Reconciler.reconcile(log, good, 1).clean());
        var broken = new ArrayList<>(good);
        broken.remove(0);
        broken.set(0, good.get(1).withAmount(good.get(1).amount() + 1));
        broken.set(1, good.get(2).withAmount(-good.get(2).amount()));
        broken.add(good.getLast());
        broken.add(new Posting("T9", 1, "A", Asset.CASH, 10));
        broken.add(new Posting("T10", 2, "A", Asset.CASH, 10));
        var r = Reconciler.reconcile(log, broken, 1);
        assertEquals(Set.of(AnomalyType.DUPLICATE, AnomalyType.MISSING, AnomalyType.AMOUNT_MISMATCH,
                AnomalyType.SIDE_MISMATCH, AnomalyType.UNEXPECTED), r.countByType().keySet());
        assertEquals(5, r.anomalies().size());
        assertTrue(r.anomalies().stream().allMatch(a -> a.journalSeq() == 1));
    }
    @Test void checksActualAccountBalancesEvenWhenPostingsAreClean() {
        var deposits = List.<JournalRecord>of(new JournalRecord.Deposit(1, "A", 100, 3));
        var actual = PostingRules.forDeposit("A", 100, 3, 1);
        var r = Reconciler.reconcile(deposits, actual, List.of(new AccountView("A", 70, 31, 0, 3)), 1);
        assertEquals(Map.of(AnomalyType.BALANCE_MISMATCH, 1), r.countByType());
    }
    @ParameterizedTest @ValueSource(longs = {1, 2, 3})
    void detectsEveryInjectedFault(long seed) {
        var r = ReconExperiment.run(4000, .01, seed);
        assertTrue(r.score().injected() > 0);
        assertEquals(r.score().injected(), r.score().detected());
        assertEquals(0, r.score().missed());
        assertEquals(0, r.score().falsePositives());
    }
    @Test void noFalsePositivesWithNoInjection() { assertTrue(ReconExperiment.run(4000, 0, 1).report().clean()); }
}
