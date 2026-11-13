package com.fredlin.trading.core;

import com.fredlin.trading.engine.*;
import com.fredlin.trading.journal.InMemoryJournal;
import com.fredlin.trading.ledger.*;
import com.fredlin.trading.recon.Reconciler;
import com.fredlin.trading.sim.OrderGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LedgerInvariantTest {
    @ParameterizedTest @ValueSource(longs = {11, 12, 13})
    void ledgerInvariantsHold(long seed) { exercise(seed, false); }
    @ParameterizedTest @ValueSource(longs = {11, 12, 13})
    void ledgerInvariantsHoldWithIocAndModify(long seed) { exercise(seed, true); }

    private static void exercise(long seed, boolean extended) {
        var journal = new InMemoryJournal();
        var books = new Books();
        var core = new TradingCore("DEMO", journal, books);
        var clients = OrderGenerator.clientIds("S", 10);
        long cash = 2_000_000, position = 400;
        clients.forEach(c -> core.deposit(c, cash, position));
        var random = new Random(seed);
        var generator = OrderGenerator.standard(core.symbol(), clients);
        for (int i = 0; i < 1200; i++) {
            var side = random.nextBoolean() ? Side.BUY : Side.SELL;
            var open = core.openOrders(side);
            int roll = random.nextInt(10);
            if (!open.isEmpty() && roll < 2) {
                var o = open.get(random.nextInt(open.size()));
                core.cancel(o.orderId(), o.clientId());
            } else if (extended && !open.isEmpty() && roll < 4) {
                var o = open.get(random.nextInt(open.size()));
                core.modify(o.orderId(), o.clientId(), o.price() + random.nextInt(-25, 26),
                        Math.max(1, o.quantity() + random.nextInt(-100, 101)));
            } else {
                var c = generator.next(random);
                core.place(extended && roll == 9
                        ? NewOrder.ioc(c.clientId(), c.symbol(), c.side(), c.price(), c.quantity()) : c);
            }
            var frozenCash = new HashMap<String, Long>();
            var frozenPosition = new HashMap<String, Long>();
            for (var o : core.openOrders(Side.BUY)) frozenCash.merge(o.clientId(), o.price() * o.remainingQuantity(), Long::sum);
            for (var o : core.openOrders(Side.SELL)) frozenPosition.merge(o.clientId(), o.remainingQuantity(), Long::sum);
            long totalCash = 0, totalPosition = 0;
            for (var a : core.accounts()) {
                assertTrue(a.cashAvailable() >= 0 && a.positionAvailable() >= 0);
                assertEquals(frozenCash.getOrDefault(a.accountId(), 0L).longValue(), a.cashFrozen());
                assertEquals(frozenPosition.getOrDefault(a.accountId(), 0L).longValue(), a.positionFrozen());
                totalCash += a.cashTotal();
                totalPosition += a.positionTotal();
            }
            assertEquals(cash * clients.size(), totalCash);
            assertEquals(position * clients.size(), totalPosition);
        }
        var log = journal.readAll();
        assertTrue(Reconciler.reconcile(log, books.postings(), core.accounts(), core.journalSequence()).clean());
        var recoveredBooks = new Books();
        var replay = TradingCore.recover("DEMO", journal, recoveredBooks).core();
        assertEquals(core.accounts(), replay.accounts());
        assertEquals(core.openOrders(Side.BUY), replay.openOrders(Side.BUY));
        assertEquals(core.openOrders(Side.SELL), replay.openOrders(Side.SELL));
        assertEquals(books.postings(), recoveredBooks.postings());
    }
}
