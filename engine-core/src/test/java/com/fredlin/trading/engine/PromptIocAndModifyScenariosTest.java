package com.fredlin.trading.engine;

import org.junit.jupiter.api.Test;
import java.util.List;

import static com.fredlin.trading.engine.OrderStatus.*;
import static com.fredlin.trading.engine.Side.*;
import static org.junit.jupiter.api.Assertions.*;

class PromptIocAndModifyScenariosTest {
    private final MatchingEngine book = new MatchingEngine("DEMO");

    private OrderView place(String client, Side side, long price, long quantity) {
        return book.place(NewOrder.limit(client, "DEMO", side, price, quantity)).order();
    }

    @Test void iocFillsCompletelyWhenLiquidityExists() {
        place("A", BUY, 100, 2);
        var r = book.place(NewOrder.ioc("B", "DEMO", SELL, 99, 2));
        assertEquals(FILLED, r.order().status());
        assertEquals(100, r.trades().getFirst().price());
        assertNull(r.reason());
    }

    @Test void iocStopsAtLimitWithoutResting() {
        place("A", SELL, 99, 1);
        place("A", SELL, 101, 3);
        var r = book.place(NewOrder.ioc("B", "DEMO", BUY, 100, 4)).order();
        assertEquals(1, r.filledQuantity());
        assertEquals(3, r.cancelledQuantity());
        assertTrue(book.openOrders(BUY).isEmpty());
        assertEquals(101, book.bestAsk().orElseThrow());
    }

    @Test void emptyBookSellIocCancelsWholeOrder() {
        var r = book.place(NewOrder.ioc("A", "DEMO", SELL, 100, 3));
        assertEquals(CANCELLED, r.order().status());
        assertEquals(3, r.order().cancelledQuantity());
        assertNotNull(r.reason());
        assertEquals(CancelStatus.ALREADY_CANCELLED, book.cancel(r.order().orderId(), "A").status());
    }

    @Test void iocKeepsEarlierFillsWhenItMeetsItsOwnOrder() {
        place("B", SELL, 99, 1);
        var own = place("A", SELL, 100, 3);
        var r = book.place(NewOrder.ioc("A", "DEMO", BUY, 100, 3));
        assertEquals(1, r.order().filledQuantity());
        assertEquals(2, r.order().cancelledQuantity());
        assertEquals(own, book.openOrders(SELL).getFirst());
    }

    @Test void reductionUpdatesDepthWithoutCancellingQuantity() {
        var a = place("A", BUY, 100, 5);
        place("B", BUY, 100, 4);
        var r = book.modify(a.orderId(), "A", 100, 2).order();
        assertEquals(a.sequence(), r.sequence());
        assertEquals(0, r.cancelledQuantity());
        assertEquals(List.of(new DepthLevel(100, 6, 2)), book.depth(BUY, 1));
    }

    @Test void amendmentQuantityIncludesPreviousFills() {
        var a = place("A", BUY, 100, 10);
        place("B", SELL, 100, 4);
        var r = book.modify(a.orderId(), "A", 100, 7).order();
        assertEquals(4, r.filledQuantity());
        assertEquals(3, r.remainingQuantity());
    }

    @Test void movingToOccupiedPriceJoinsItsTail() {
        var moved = place("A", BUY, 99, 2);
        var existing = place("B", BUY, 100, 2);
        var r = book.modify(moved.orderId(), "A", 100, 2);
        assertEquals(r.sequence(), r.order().sequence());
        assertEquals(existing.orderId(), book.openOrders(BUY).getFirst().orderId());
    }

    @Test void increasingQuantityAtSamePriceMovesOrderBack() {
        var a = place("A", BUY, 100, 2);
        var b = place("B", BUY, 100, 2);
        book.modify(a.orderId(), "A", 100, 3);
        assertEquals(b.orderId(), book.openOrders(BUY).getFirst().orderId());
    }

    @Test void movingAcrossTheSpreadExecutesThenRestsAtNewLimit() {
        var a = place("A", BUY, 99, 5);
        place("B", SELL, 101, 2);
        var r = book.modify(a.orderId(), "A", 102, 5);
        assertEquals(101, r.trades().getFirst().price());
        assertEquals(3, r.order().remainingQuantity());
        assertEquals(102, book.bestBid().orElseThrow());
    }

    @Test void targetQuantityAtOrBelowFilledCancelsLeftover() {
        var a = place("A", BUY, 100, 8);
        place("B", SELL, 100, 3);
        var r = book.modify(a.orderId(), "A", 101, 2).order();
        assertEquals(CANCELLED, r.status());
        assertEquals(3, r.filledQuantity());
        assertEquals(5, r.cancelledQuantity());
        assertTrue(book.bestBid().isEmpty());
    }

    @Test void invalidOwnershipAndTerminalAmendmentsAreDistinguished() {
        var a = place("A", BUY, 100, 2);
        var b = place("B", BUY, 99, 2);
        place("C", SELL, 100, 2);
        book.cancel(b.orderId(), "B");
        assertEquals(ModifyStatus.NOT_FOUND, book.modify(a.orderId(), "D", 100, 2).status());
        assertEquals(ModifyStatus.ALREADY_FILLED, book.modify(a.orderId(), "A", 100, 2).status());
        assertEquals(ModifyStatus.ALREADY_CANCELLED, book.modify(b.orderId(), "B", 99, 2).status());
        assertEquals(ModifyStatus.NOT_FOUND, book.modify(99, "B", 99, 2).status());
    }

    @Test void rejectsInvalidPriceAndQuantityWithoutLosingPriority() {
        var a = place("A", BUY, 100, 2);
        place("B", BUY, 100, 2);
        assertEquals(ModifyStatus.REJECTED, book.modify(a.orderId(), "A", 0, 2).status());
        assertEquals(ModifyStatus.REJECTED, book.modify(a.orderId(), "A", 100, 0).status());
        assertEquals(a, book.openOrders(BUY).getFirst());
    }

    @Test void amendmentMatchesOtherClientBeforeCancellingSelfCross() {
        var bid = place("A", BUY, 99, 3);
        place("B", SELL, 100, 1);
        var own = place("A", SELL, 101, 3);
        var r = book.modify(bid.orderId(), "A", 101, 3);
        assertEquals(1, r.order().filledQuantity());
        assertEquals(2, r.order().cancelledQuantity());
        assertEquals(own, book.openOrders(SELL).getFirst());
    }

    @Test void unchangedAmendmentIsANoOpForPriorityAndDepth() {
        var a = place("A", BUY, 100, 2);
        place("B", BUY, 100, 2);
        var r = book.modify(a.orderId(), "A", 100, 2);
        assertEquals(a, r.order());
        assertTrue(r.trades().isEmpty());
        assertEquals(List.of(new DepthLevel(100, 4, 2)), book.depth(BUY, 1));
    }
}
