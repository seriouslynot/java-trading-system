package com.fredlin.trading.sim;

import com.fredlin.trading.engine.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketWorkloadTest {
    private final MarketWorkload.Parameters params = MarketWorkload.Parameters.standard(20_000);
    @Test void sameSeedProducesSameCommandsAndRegimesOnlyChangePrices() {
        var flat = MarketWorkload.generate(MarketRegime.STATIC, params, 1);
        var crash = MarketWorkload.generate(MarketRegime.FLASH_CRASH_60, params, 1);
        assertEquals(flat, MarketWorkload.generate(MarketRegime.STATIC, params, 1));
        assertNotEquals(flat, crash);
        for (int i = 0; i < flat.size(); i++) {
            var c = crash.get(i);
            switch (flat.get(i)) {
                case MarketWorkload.Place p -> {
                    var a = p.order();
                    var b = ((MarketWorkload.Place)c).order();
                    assertEquals(a, new NewOrder(b.clientId(), b.symbol(), b.side(), a.price(), b.quantity(), b.clientOrderId(), b.timeInForce()));
                }
                case MarketWorkload.Cancel d -> assertEquals(d, c);
                case MarketWorkload.Modify m -> {
                    var b = (MarketWorkload.Modify)c;
                    assertEquals(m, new MarketWorkload.Modify(b.orderId(), b.clientId(), m.newPrice(), b.newQuantity()));
                }
            }
        }
    }
    @Test void proportionsMatchParameters() {
        var workload = MarketWorkload.generate(MarketRegime.STATIC, params, 1);
        long ioc = workload.stream().filter(c -> c instanceof MarketWorkload.Place p && p.order().timeInForce() == TimeInForce.IOC).count();
        long place = workload.stream().filter(MarketWorkload.Place.class::isInstance).count();
        long cancel = workload.stream().filter(MarketWorkload.Cancel.class::isInstance).count();
        long modify = workload.stream().filter(MarketWorkload.Modify.class::isInstance).count();
        assertEquals(.15, ioc / (double)place, .02);
        assertEquals(.95, cancel / (double)(place - ioc), .025);
        assertEquals(.20, modify / (double)(place - ioc), .02);
    }
    @ParameterizedTest @EnumSource(MarketRegime.class)
    void legalPricesAndOwnerIds(MarketRegime regime) {
        var workload = MarketWorkload.generate(regime, params, 1);
        var placed = new ArrayList<NewOrder>();
        for (var c : workload) switch (c) {
            case MarketWorkload.Place p -> {
                assertTrue(p.order().price() >= 1 && p.order().price() <= EngineLimits.MAX_PRICE);
                placed.add(p.order());
            }
            case MarketWorkload.Cancel x -> check(placed, x.orderId(), x.clientId());
            case MarketWorkload.Modify x -> {
                check(placed, x.orderId(), x.clientId());
                assertTrue(x.newPrice() >= 1 && x.newPrice() <= EngineLimits.MAX_PRICE);
            }
        }
    }
    private static void check(List<NewOrder> orders, long id, String client) {
        var o = orders.get(Math.toIntExact(id - 1));
        assertEquals(client, o.clientId());
        assertEquals(TimeInForce.GTC, o.timeInForce());
    }
    @Test void crashAndSwingShapes() {
        for (var r : List.of(MarketRegime.FLASH_CRASH_40, MarketRegime.FLASH_CRASH_60)) {
            assertEquals(1, r.trend(.30));
            assertEquals(r == MarketRegime.FLASH_CRASH_40 ? .60 : .40, r.trend(.32), 1e-12);
            assertEquals(1, r.trend(.60));
        }
        assertEquals(1.25, MarketRegime.LARGE_SWING.trend(.25), 1e-12);
        assertEquals(.75, MarketRegime.LARGE_SWING.trend(.75), 1e-12);
        assertEquals(1, MarketRegime.LARGE_SWING.trend(1), 1e-12);
    }
}
