package com.fredlin.trading.engine;

import com.fredlin.trading.sim.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderBookInvariantTest {
    @ParameterizedTest @ValueSource(longs = {1, 2, 3, 42, 20261111})
    void invariantsHoldAfterEveryStep(long seed) {
        checkRandom(seed, false);
    }
    @ParameterizedTest @ValueSource(longs = {1, 2, 3, 42, 20261112})
    void invariantsHoldWithIocAndModify(long seed) {
        checkRandom(seed, true);
    }
    private void checkRandom(long seed, boolean amendments) {
        var e = new MatchingEngine("DEMO");
        var random = new Random(seed);
        var generator = OrderGenerator.standard("DEMO", OrderGenerator.clientIds("C", 20));
        for (int i = 0; i < 1500; i++) {
            var side = random.nextBoolean() ? Side.BUY : Side.SELL;
            var open = e.openOrders(side);
            int roll = random.nextInt(10);
            if (!open.isEmpty() && roll < 2) {
                var selected = open.get(random.nextInt(open.size()));
                var r = e.cancel(selected.orderId(), random.nextInt(10) == 0 ? "C99" : selected.clientId());
                if (r.cancelled()) checkOrder(r.order());
            } else if (amendments && !open.isEmpty() && roll < 4) {
                var o = open.get(random.nextInt(open.size()));
                var m = e.modify(o.orderId(), o.clientId(), o.price() + random.nextInt(-20, 21),
                        Math.max(1, o.quantity() + random.nextInt(-80, 81)));
                checkOrder(m.order());
                checkTrades(m.trades());
            } else {
                var c = generator.next(random);
                if (amendments && random.nextInt(6) == 0) c = NewOrder.ioc(c.clientId(), c.symbol(), c.side(), c.price(), c.quantity());
                var p = e.place(c);
                checkOrder(p.order());
                checkTrades(p.trades());
            }
            checkBook(e);
        }
    }
    @ParameterizedTest @EnumSource(MarketRegime.class)
    void invariantsHoldUnderMarketRegimes(MarketRegime regime) {
        var e = new MatchingEngine("DEMO");
        for (var command : MarketWorkload.generate(regime, MarketWorkload.Parameters.standard(2000), 42)) {
            switch (command) {
                case MarketWorkload.Place p -> checkTrades(e.place(p.order()).trades());
                case MarketWorkload.Cancel c -> assertNotEquals(CancelStatus.NOT_FOUND, e.cancel(c.orderId(), c.clientId()).status());
                case MarketWorkload.Modify m -> {
                    var r = e.modify(m.orderId(), m.clientId(), m.newPrice(), m.newQuantity());
                    assertNotEquals(ModifyStatus.NOT_FOUND, r.status());
                    checkTrades(r.trades());
                }
            }
            checkBook(e);
        }
    }
    private void checkBook(MatchingEngine e) {
        if (e.bestBid().isPresent() && e.bestAsk().isPresent()) assertTrue(e.bestBid().getAsLong() < e.bestAsk().getAsLong());
        var unique = new HashSet<Long>();
        for (var side : Side.values()) {
            var depth = new ArrayList<DepthLevel>();
            long previous = side == Side.BUY ? Long.MAX_VALUE : Long.MIN_VALUE;
            long lastSequence = 0, total = 0;
            int count = 0;
            for (var o : e.openOrders(side)) {
                assertTrue(unique.add(o.orderId()));
                assertEquals(side, o.side());
                assertEquals(o, e.order(o.orderId()).orElseThrow());
                assertFalse(o.status().isTerminal());
                assertEquals(0, o.cancelledQuantity());
                checkOrder(o);
                if (o.price() != previous) {
                    if (count > 0) depth.add(new DepthLevel(previous, total, count));
                    assertTrue(side == Side.BUY ? o.price() < previous : o.price() > previous);
                    previous = o.price();
                    count = 0;
                    total = 0;
                    lastSequence = 0;
                }
                assertTrue(o.sequence() > lastSequence);
                lastSequence = o.sequence();
                total += o.remainingQuantity();
                count++;
            }
            if (count > 0) depth.add(new DepthLevel(previous, total, count));
            assertEquals(depth, e.depth(side, Integer.MAX_VALUE));
        }
    }
    private static void checkOrder(OrderView o) {
        assertEquals(o.quantity(), o.filledQuantity() + o.remainingQuantity() + o.cancelledQuantity());
        assertTrue(o.remainingQuantity() >= 0);
        assertEquals(o.status().isTerminal(), o.remainingQuantity() == 0);
    }
    private static void checkTrades(List<Trade> trades) {
        for (var t : trades) {
            assertTrue(t.quantity() > 0 && t.price() > 0);
            assertNotEquals(t.buyClientId(), t.sellClientId());
        }
    }
}
