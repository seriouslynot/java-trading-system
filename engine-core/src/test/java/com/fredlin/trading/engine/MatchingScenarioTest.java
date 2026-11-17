package com.fredlin.trading.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.fredlin.trading.engine.Side.*;
import static com.fredlin.trading.engine.OrderStatus.*;

class MatchingScenarioTest {
    final MatchingEngine e = new MatchingEngine("DEMO");
    PlaceResult p(String client, Side side, long price, long qty) {
        return e.place(NewOrder.limit(client, "DEMO", side, price, qty));
    }
    @Test void priceThenFifoAndMakerPrice() {
        var a = p("A", SELL, 100, 4).order();
        var b = p("B", SELL, 100, 4).order();
        var c = p("C", SELL, 99, 2).order();
        var trades = p("D", BUY, 101, 7).trades();
        assertEquals(java.util.List.of(c.orderId(), a.orderId(), b.orderId()),
            trades.stream().map(Trade::sellOrderId).toList());
        assertEquals(java.util.List.of(99L, 100L, 100L), trades.stream().map(Trade::price).toList());
        assertEquals(3, e.depth(SELL, 1).getFirst().totalQuantity());
    }
    @Test void selfTradeCancelsRemainder() {
        p("B", SELL, 99, 2);
        var maker = p("A", SELL, 100, 4).order();
        var r = p("A", BUY, 100, 5);
        assertEquals(CANCELLED, r.order().status());
        assertEquals(2, r.order().filledQuantity());
        assertEquals(3, r.order().cancelledQuantity());
        assertNotNull(r.reason());
        assertEquals(maker, e.order(maker.orderId()).orElseThrow());
    }
    @Test void iocAndOwnership() {
        var maker = p("A", BUY, 100, 4).order();
        var r = e.place(NewOrder.ioc("B", "DEMO", SELL, 100, 7));
        assertEquals(CANCELLED, r.order().status());
        assertEquals(3, r.order().cancelledQuantity());
        assertTrue(e.bestAsk().isEmpty());
        assertEquals(CancelStatus.NOT_FOUND, e.cancel(maker.orderId(), "B").status());
        assertEquals(CancelStatus.ALREADY_FILLED, e.cancel(maker.orderId(), "A").status());
    }
    @Test void amendReduceRetainsPriorityAndIncreaseLosesIt() {
        var a = p("A", BUY, 100, 5).order();
        var b = p("B", BUY, 100, 5).order();
        assertEquals(a.sequence(), e.modify(a.orderId(), "A", 100, 3).order().sequence());
        assertEquals(a.orderId(), e.openOrders(BUY).getFirst().orderId());
        var amended = e.modify(a.orderId(), "A", 100, 6);
        assertEquals(amended.sequence(), amended.order().sequence());
        assertEquals(b.orderId(), e.openOrders(BUY).getFirst().orderId());
    }
    @Test void rejectedOrderIsQueryableButCannotBeCancelled() {
        var r = p("A", BUY, 0, 1);
        assertEquals(REJECTED, r.order().status());
        assertEquals("价格必须大于 0", r.reason());
        assertEquals(r.order(), e.order(r.order().orderId()).orElseThrow());
        assertEquals(0, r.order().cancelledQuantity());
        assertEquals(0, r.order().remainingQuantity());
        assertEquals(0, r.order().filledQuantity());
        assertEquals(CancelStatus.NOT_FOUND, e.cancel(r.order().orderId(), "A").status());
        assertEquals(ModifyStatus.NOT_FOUND, e.modify(r.order().orderId(), "A", 1, 1).status());
        assertEquals(r.order().orderId() + 1, p("A", BUY, 1, 1).order().orderId());
    }
}
