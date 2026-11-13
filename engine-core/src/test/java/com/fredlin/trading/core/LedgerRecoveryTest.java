package com.fredlin.trading.core;

import org.junit.jupiter.api.Test;
import com.fredlin.trading.engine.*;
import com.fredlin.trading.journal.*;
import com.fredlin.trading.ledger.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.fredlin.trading.engine.Side.*;

class LedgerRecoveryTest {
    @Test void fillsRefundPriceDifferenceAndCancelReleasesOnlyRemainder() {
        var journal = new InMemoryJournal();
        var books = new Books();
        var core = new TradingCore("DEMO", journal, books);
        core.deposit("A", 1000, 10);
        core.deposit("B", 1000, 10);
        core.place(NewOrder.limit("B", "DEMO", SELL, 70, 4));
        var buy = core.place(NewOrder.limit("A", "DEMO", BUY, 90, 8)).order();
        assertEquals(new AccountView("A", 360, 360, 14, 0), core.account("A").orElseThrow());
        core.modify(buy.orderId(), "A", 90, 6);
        assertEquals(180, core.account("A").orElseThrow().cashFrozen());
        core.cancel(buy.orderId(), "A");
        assertEquals(new AccountView("A", 720, 0, 14, 0), core.account("A").orElseThrow());
        assertEquals(12, books.size());
        var replayBooks = new Books();
        var replay = TradingCore.recover("DEMO", journal, replayBooks).core();
        assertEquals(core.accounts(), replay.accounts());
        assertEquals(core.order(buy.orderId()), replay.order(buy.orderId()));
        assertEquals(core.journalSequence(), replay.journalSequence());
        assertEquals(books.postings(), replayBooks.postings());
    }
    @Test void recoveryRejectsValidlyEncodedButIncorrectResult() {
        var journal = new InMemoryJournal();
        journal.append(new JournalRecord.Deposit(1, "A", 100, 0));
        journal.append(new JournalRecord.Place(2, NewOrder.limit("A", "DEMO", BUY, 100, 1), 1,
            OrderStatus.FILLED, null, java.util.List.of()));
        assertThrows(JournalCorruptedException.class, () -> TradingCore.recover("DEMO", journal, new Books()));
    }
    @Test void journalFailureStopsCore() {
        Journal journal = new Journal() {
            public void append(JournalRecord record) {}
            public java.util.List<JournalRecord> readAll() { return java.util.List.of(); }
            public void flush() { throw new IllegalStateException("disk failed"); }
        };
        var core = new TradingCore("DEMO", journal, new Books());
        core.deposit("A", 10, 1);
        assertThrows(IllegalStateException.class, core::flush);
        assertTrue(core.isFailed());
        assertThrows(IllegalStateException.class, () -> core.cancel(1, "A"));
        assertThrows(IllegalStateException.class, core::flush);
    }
    @Test void rejectsUnfundedCommandsAndPreservesFreezeOnRejectedAmend() {
        var core = new TradingCore("DEMO", new InMemoryJournal(), new Books());
        core.deposit("A", 100, 2);
        assertFalse(core.place(NewOrder.limit("B", "DEMO", BUY, 1, 1)).accepted());
        assertFalse(core.place(NewOrder.limit("A", "DEMO", SELL, 1, 3)).accepted());
        assertFalse(core.place(NewOrder.limit("A", "DEMO", BUY, 101, 1)).accepted());
        var order = core.place(NewOrder.limit("A", "DEMO", BUY, 90, 1)).order();
        assertEquals(ModifyStatus.REJECTED, core.modify(order.orderId(), "A", 110, 1).status());
        assertEquals(order, core.order(order.orderId()).orElseThrow());
        assertEquals(new AccountView("A", 10, 90, 2, 0), core.account("A").orElseThrow());
    }
    @Test void iocAndSelfTradeUnfreezeRemaindersAndKeepMaker() {
        var core = new TradingCore("DEMO", new InMemoryJournal(), new Books());
        core.deposit("A", 1000, 10);
        var ask = core.place(NewOrder.limit("A", "DEMO", SELL, 50, 5));
        var ioc = core.place(NewOrder.ioc("A", "DEMO", BUY, 50, 5));
        assertEquals(OrderStatus.CANCELLED, ioc.order().status());
        assertEquals(0, ioc.trades().size());
        assertEquals(ask.order(), core.order(ask.order().orderId()).orElseThrow());
        assertEquals(new AccountView("A", 1000, 0, 5, 5), core.account("A").orElseThrow());
        var miss = core.place(NewOrder.ioc("A", "DEMO", BUY, 49, 5));
        assertEquals(5, miss.order().cancelledQuantity());
        assertEquals(1000, core.account("A").orElseThrow().cashAvailable());
    }
    @Test void amendIntoTradeThenReduceToFilledReleasesRemainder() {
        var journal = new InMemoryJournal();
        var books = new Books();
        var core = new TradingCore("DEMO", journal, books);
        core.deposit("A", 1000, 0);
        core.deposit("B", 0, 10);
        core.place(NewOrder.limit("B", "DEMO", SELL, 80, 4));
        var o = core.place(NewOrder.limit("A", "DEMO", BUY, 70, 5)).order();
        var m = core.modify(o.orderId(), "A", 90, 6);
        assertEquals(80, m.trades().getFirst().price());
        assertEquals(new AccountView("A", 500, 180, 4, 0), core.account("A").orElseThrow());
        var cancel = core.modify(o.orderId(), "A", 90, 3);
        assertEquals(OrderStatus.CANCELLED, cancel.order().status());
        assertEquals(4, cancel.order().filledQuantity());
        assertEquals(2, cancel.order().cancelledQuantity());
        assertEquals(new AccountView("A", 680, 0, 4, 0), core.account("A").orElseThrow());
        assertEquals(core.accounts(), TradingCore.recover("DEMO", journal, new Books()).core().accounts());
    }
    @Test void recoveryRejectsMissingSequence() {
        var journal = new InMemoryJournal();
        journal.append(new JournalRecord.Deposit(1, "A", 100, 1));
        journal.append(new JournalRecord.Deposit(3, "B", 100, 1));
        assertThrows(JournalCorruptedException.class, () -> TradingCore.recover("DEMO", journal, new Books()));
    }
}
