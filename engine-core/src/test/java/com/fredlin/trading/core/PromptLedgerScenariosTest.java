package com.fredlin.trading.core;

import com.fredlin.trading.engine.*;
import com.fredlin.trading.journal.InMemoryJournal;
import com.fredlin.trading.ledger.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static com.fredlin.trading.engine.Side.*;
import static org.junit.jupiter.api.Assertions.*;

class PromptLedgerScenariosTest {
    private final Books postings = new Books();
    private final TradingCore core = new TradingCore("DEMO", new InMemoryJournal(), postings);

    @BeforeEach void fund() {
        core.deposit("A", 1_000, 10);
        core.deposit("B", 1_000, 10);
    }

    private AccountView account(String id) { return core.account(id).orElseThrow(); }
    private PlaceResult place(String client, Side side, long price, long quantity) {
        return core.place(NewOrder.limit(client, "DEMO", side, price, quantity));
    }

    @Test void buyReservesLimitTimesQuantity() {
        place("A", BUY, 120, 3);
        assertEquals(new AccountView("A", 640, 360, 10, 0), account("A"));
    }

    @Test void sellReservesOnlyShares() {
        place("A", SELL, 120, 3);
        assertEquals(new AccountView("A", 1000, 0, 7, 3), account("A"));
    }

    @Test void rejectsUnknownAccountWithoutCreatingIt() {
        assertFalse(place("C", BUY, 100, 2).accepted());
        assertTrue(core.account("C").isEmpty());
        assertTrue(core.openOrders(BUY).isEmpty());
        assertEquals(8, postings.size());
    }

    @Test void insufficientCashDoesNotReservePartOfIt() {
        var before = account("A");
        assertFalse(place("A", BUY, 501, 2).accepted());
        assertEquals(before, account("A"));
    }

    @Test void insufficientInventoryDoesNotReservePartOfIt() {
        var before = account("A");
        assertFalse(place("A", SELL, 100, 11).accepted());
        assertEquals(before, account("A"));
    }

    @Test void buyerImmediatelyGetsPriceImprovementBack() {
        place("B", SELL, 80, 2);
        place("A", BUY, 100, 4);
        assertEquals(new AccountView("A", 640, 200, 12, 0), account("A"));
        assertEquals(new AccountView("B", 1160, 0, 8, 0), account("B"));
    }

    @Test void cancellingPartiallyFilledSellOnlyReleasesUnfilledShares() {
        long id = place("A", SELL, 100, 6).order().orderId();
        place("B", BUY, 100, 2);
        assertTrue(core.cancel(id, "A").cancelled());
        assertEquals(new AccountView("A", 1200, 0, 8, 0), account("A"));
    }

    @Test void selfTradeProtectionReleasesTheAggressorsReservation() {
        place("A", SELL, 100, 2);
        var cancelled = place("A", BUY, 100, 2);
        assertEquals(OrderStatus.CANCELLED, cancelled.order().status());
        assertEquals(new AccountView("A", 1000, 0, 8, 2), account("A"));
        assertEquals(8, postings.size());
    }

    @Test void iocReleasesUnfilledReservationAfterSettlement() {
        place("B", SELL, 80, 2);
        var r = core.place(NewOrder.ioc("A", "DEMO", BUY, 100, 4));
        assertEquals(2, r.order().cancelledQuantity());
        assertEquals(new AccountView("A", 840, 0, 12, 0), account("A"));
    }

    @Test void fillWritesExactlyFourSignedPostings() {
        place("B", SELL, 80, 2);
        place("A", BUY, 100, 2);
        assertEquals(List.of(new Posting("T1", 4, "A", Asset.CASH, -160),
                new Posting("T1", 4, "B", Asset.CASH, 160),
                new Posting("T1", 4, "A", Asset.POSITION, 2),
                new Posting("T1", 4, "B", Asset.POSITION, -2)), postings.latest(4));
    }

    @Test void tradePostingsBalanceSeparatelyForCashAndInventory() {
        place("B", SELL, 80, 2);
        place("A", BUY, 100, 2);
        for (var asset : Asset.values()) {
            assertEquals(0, postings.latest(4).stream().filter(p -> p.asset() == asset).mapToLong(Posting::amount).sum());
        }
    }

    @Test void reservationsAmendmentsAndCancellationCreateNoPostings() {
        long id = place("A", BUY, 100, 4).order().orderId();
        core.modify(id, "A", 90, 3);
        core.cancel(id, "A");
        assertEquals(8, postings.size());
        assertEquals(new AccountView("A", 1000, 0, 10, 0), account("A"));
    }

    @Test void amendRepricesOnlyRemainingBuyQuantity() {
        long id = place("A", BUY, 100, 4).order().orderId();
        place("B", SELL, 100, 1);
        assertTrue(core.modify(id, "A", 120, 5).modified());
        assertEquals(new AccountView("A", 420, 480, 11, 0), account("A"));
    }

    @Test void amendReductionReleasesExcessInventoryImmediately() {
        long id = place("A", SELL, 100, 6).order().orderId();
        place("B", BUY, 100, 2);
        assertTrue(core.modify(id, "A", 100, 4).modified());
        assertEquals(new AccountView("A", 1200, 0, 6, 2), account("A"));
    }

    @Test void unfundedAmendmentPreservesFreezeAndQueuePosition() {
        var first = place("A", BUY, 100, 5).order();
        var second = place("B", BUY, 100, 5).order();
        var before = account("A");
        assertEquals(ModifyStatus.REJECTED, core.modify(first.orderId(), "A", 100, 11).status());
        assertEquals(List.of(first, second), core.openOrders(BUY));
        assertEquals(before, account("A"));
        assertEquals(8, postings.size());
    }

    @Test void depositsPairWithExternalAccountByAsset() {
        core.deposit("C", 600, 4);
        assertEquals(List.of(new Posting("D3", 3, "C", Asset.CASH, 600),
                new Posting("D3", 3, "EXTERNAL", Asset.CASH, -600),
                new Posting("D3", 3, "C", Asset.POSITION, 4),
                new Posting("D3", 3, "EXTERNAL", Asset.POSITION, -4)), postings.latest(4));
    }
}
