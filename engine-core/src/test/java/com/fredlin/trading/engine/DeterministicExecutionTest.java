package com.fredlin.trading.engine;

import com.fredlin.trading.sim.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeterministicExecutionTest {
    @ParameterizedTest @ValueSource(longs = {1, 2, 3})
    void repeatMixedCommands(long seed) {
        var generator = OrderGenerator.standard("DEMO", OrderGenerator.clientIds("C", 20));
        compare(MixedOrderWorkload.generate(generator, 30_000, seed), "seed=" + seed);
    }

    @ParameterizedTest @EnumSource(MarketRegime.class)
    void repeatMarketPath(MarketRegime regime) {
        compare(MarketWorkload.generate(regime, MarketWorkload.Parameters.standard(10_000), 1),
                "regime=" + regime + ", seed=1");
    }

    private static void compare(List<MarketWorkload.Command> commands, String description) {
        var first = new MatchingEngine("DEMO");
        var second = new MatchingEngine("DEMO");
        for (int i = 0; i < commands.size(); i++) {
            var command = commands.get(i);
            int step = i + 1;
            assertEquals(execute(first, command), execute(second, command),
                    () -> description + ", step=" + step + ", command=" + command);
        }
        for (Side side : Side.values()) {
            assertEquals(first.openOrders(side), second.openOrders(side));
            assertEquals(first.depth(side, 10), second.depth(side, 10));
        }
    }

    private static Object execute(MatchingEngine engine, MarketWorkload.Command command) {
        return switch (command) {
            case MarketWorkload.Place p -> engine.place(p.order());
            case MarketWorkload.Cancel c -> engine.cancel(c.orderId(), c.clientId());
            case MarketWorkload.Modify m -> engine.modify(m.orderId(), m.clientId(), m.newPrice(), m.newQuantity());
        };
    }
}
