package com.fredlin.trading.sim;

import com.fredlin.trading.engine.NewOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A repeatable stream of roughly 80% placements and 20% cancellation attempts. */
public final class MixedOrderWorkload {
    private MixedOrderWorkload() {}

    public static List<MarketWorkload.Command> generate(OrderGenerator generator, int count, long seed) {
        var random = new Random(seed);
        List<NewOrder> placements = new ArrayList<>();
        List<MarketWorkload.Command> commands = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (placements.isEmpty() || random.nextInt(5) != 0) {
                var order = generator.next(random);
                placements.add(order);
                commands.add(new MarketWorkload.Place(order));
            } else {
                int index = random.nextInt(placements.size());
                commands.add(new MarketWorkload.Cancel(index + 1L, placements.get(index).clientId()));
            }
        }
        return List.copyOf(commands);
    }
}
