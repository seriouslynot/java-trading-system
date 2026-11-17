package com.fredlin.trading.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.fredlin.trading.engine.OrderStatus.*;
import static com.fredlin.trading.engine.Side.*;
import static org.junit.jupiter.api.Assertions.*;

class PromptMatchingScenariosTest {
    private final MatchingEngine book = new MatchingEngine("DEMO");

    private PlaceResult place(String client, Side side, long price, long quantity) {
        return book.place(NewOrder.limit(client, "DEMO", side, price, quantity));
    }

    @Test void rejectsPriceAboveMaximum() {
        assertFalse(place("A", BUY, EngineLimits.MAX_PRICE + 1, 1).accepted());
        assertTrue(place("A", BUY, EngineLimits.MAX_PRICE, 1).accepted());
    }

    @Test void rejectsZeroQuantity() {
        assertFalse(place("A", BUY, 100, 0).accepted());
        assertTrue(book.openOrders(BUY).isEmpty());
    }

    @Test void rejectsQuantityAboveMaximum() {
        assertFalse(place("A", BUY, 100, EngineLimits.MAX_QUANTITY + 1).accepted());
        assertTrue(place("A", BUY, 100, EngineLimits.MAX_QUANTITY).accepted());
    }

    @Test void rejectsAnUnknownSymbol() {
        assertFalse(book.place(NewOrder.limit("A", "MISSING", BUY, 100, 1)).accepted());
    }

    @Test void rejectsInvalidClient() {
        assertFalse(place("A|broken", BUY, 100, 1).accepted());
        assertFalse(place(null, BUY, 100, 1).accepted());
    }

    @Test void rejectsMalformedClientOrderId() {
        assertFalse(book.place(new NewOrder("A", "DEMO", BUY, 100, 1, "order|id")).accepted());
    }

    @Test void rejectsMissingSide() {
        assertEquals(REJECTED, place("A", null, 100, 1).order().status());
    }

    @Test void pricesThatDoNotCrossRemainOnBothSides() {
        place("A", BUY, 99, 3);
        var ask = place("B", SELL, 100, 2);
        assertTrue(ask.trades().isEmpty());
        assertEquals(99, book.bestBid().orElseThrow());
        assertEquals(100, book.bestAsk().orElseThrow());
    }

    @Test void equalPricesCross() {
        place("A", SELL, 100, 3);
        assertEquals(FILLED, place("B", BUY, 100, 3).order().status());
        assertTrue(book.bestAsk().isEmpty());
    }

    @Test void sellConsumesHighestBidFirst() {
        long lower = place("A", BUY, 100, 2).order().orderId();
        long higher = place("B", BUY, 101, 2).order().orderId();
        var trades = place("C", SELL, 99, 4).trades();
        assertEquals(List.of(higher, lower), trades.stream().map(Trade::buyOrderId).toList());
        assertEquals(List.of(101L, 100L), trades.stream().map(Trade::price).toList());
    }

    @Test void partiallyFilledMakerKeepsItsPlace() {
        long first = place("A", SELL, 100, 5).order().orderId();
        place("B", SELL, 100, 5);
        place("C", BUY, 100, 2);
        assertEquals(first, place("D", BUY, 100, 1).trades().getFirst().sellOrderId());
        assertEquals(2, book.order(first).orElseThrow().remainingQuantity());
    }

    @Test void takerStopsAtItsLimitAndRests() {
        place("A", SELL, 99, 2);
        place("B", SELL, 101, 2);
        var result = place("C", BUY, 100, 5);
        assertEquals(PARTIALLY_FILLED, result.order().status());
        assertEquals(3, result.order().remainingQuantity());
        assertEquals(100, book.bestBid().orElseThrow());
        assertEquals(101, book.bestAsk().orElseThrow());
    }

    @Test void removesConsumedPriceLevel() {
        place("A", BUY, 100, 2);
        place("B", BUY, 99, 3);
        place("C", SELL, 100, 2);
        assertEquals(List.of(new DepthLevel(99, 3, 1)), book.depth(BUY, 5));
    }

    @Test void stopsWhenItRunsOutOfOppositeOrders() {
        place("A", SELL, 100, 1);
        place("B", SELL, 101, 2);
        var r = place("C", BUY, 105, 4);
        assertEquals(3, r.order().filledQuantity());
        assertEquals(1, r.order().remainingQuantity());
        assertTrue(book.bestAsk().isEmpty());
    }

    @Test void cancelInTheMiddleKeepsFifo() {
        long a = place("A", SELL, 100, 1).order().orderId();
        long b = place("B", SELL, 100, 1).order().orderId();
        long c = place("C", SELL, 100, 1).order().orderId();
        assertTrue(book.cancel(b, "B").cancelled());
        assertEquals(List.of(a, c), place("D", BUY, 100, 2).trades().stream().map(Trade::sellOrderId).toList());
    }

    @Test void cancelOfPartialOrderReportsOnlyUnfilledShares() {
        long id = place("A", SELL, 100, 5).order().orderId();
        place("B", BUY, 100, 2);
        var cancelled = book.cancel(id, "A").order();
        assertEquals(2, cancelled.filledQuantity());
        assertEquals(3, cancelled.cancelledQuantity());
        assertEquals(0, cancelled.remainingQuantity());
    }

    @Test void cancelByAnotherClientLeavesBookUnchanged() {
        var order = place("A", SELL, 100, 1).order();
        assertEquals(CancelStatus.NOT_FOUND, book.cancel(order.orderId(), "B").status());
        assertEquals(order, book.openOrders(SELL).getFirst());
    }

    @Test void cancelOfUnknownOrderLeavesAnEmptyBook() {
        assertEquals(CancelStatus.NOT_FOUND, book.cancel(1, "A").status());
        assertTrue(book.openOrders(SELL).isEmpty());
        assertTrue(book.openOrders(BUY).isEmpty());
    }

    @Test void cancelTwiceReturnsAlreadyCancelled() {
        long id = place("A", BUY, 100, 2).order().orderId();
        assertTrue(book.cancel(id, "A").cancelled());
        assertEquals(CancelStatus.ALREADY_CANCELLED, book.cancel(id, "A").status());
    }

    @Test void returnsTerminalStatusAfterCompleteFill() {
        long id = place("A", SELL, 100, 2).order().orderId();
        place("B", BUY, 100, 2);
        assertEquals(FILLED, book.order(id).orElseThrow().status());
        assertEquals(CancelStatus.ALREADY_FILLED, book.cancel(id, "A").status());
    }

    @Test void depthLimitsAndSortsBothSides() {
        place("A", BUY, 90, 1);
        place("A", BUY, 95, 5);
        place("B", SELL, 105, 7);
        place("B", SELL, 101, 3);
        assertEquals(List.of(new DepthLevel(95, 5, 1)), book.depth(BUY, 1));
        assertEquals(List.of(new DepthLevel(101, 3, 1)), book.depth(SELL, 1));
        assertThrows(IllegalArgumentException.class, () -> book.depth(SELL, 0));
    }

    @Test void depthAggregatesOrdersAtTheSamePrice() {
        place("A", BUY, 100, 4);
        place("B", BUY, 100, 7);
        place("C", SELL, 100, 2);
        assertEquals(List.of(new DepthLevel(100, 9, 2)), book.depth(BUY, 1));
    }

    @Test void preventsAnImmediateSelfFillWithoutRemovingMaker() {
        var maker = place("A", SELL, 100, 2).order();
        var taker = place("A", BUY, 100, 2);
        assertEquals(CANCELLED, taker.order().status());
        assertTrue(taker.trades().isEmpty());
        assertEquals(maker, book.openOrders(SELL).getFirst());
    }

    @Test void duplicateInternalIdThrowsWithoutChangingDepth() {
        var internalBook = new OrderBook();
        var command = NewOrder.limit("A", "DEMO", BUY, 100, 2);
        var order = new Order(1, command, 1);
        internalBook.add(order);
        assertThrows(IllegalStateException.class, () -> internalBook.add(new Order(1, command, 2)));
        assertSame(order, internalBook.get(1));
        assertEquals(2, internalBook.levels(BUY).get(100L).total);
        assertThrows(IllegalArgumentException.class,
                () -> internalBook.add(new Order(2, NewOrder.limit("A", "DEMO", BUY, 100, 0), 2)));
    }
}
