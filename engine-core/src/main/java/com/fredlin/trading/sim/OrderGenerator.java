package com.fredlin.trading.sim;

import com.fredlin.trading.engine.*;
import java.util.*;
import java.util.stream.IntStream;

public record OrderGenerator(String symbol, List<String> clients, long midPrice, int spreadTicks, int largeOrderPct) {
    public OrderGenerator {
        clients = List.copyOf(clients);
        if (clients.isEmpty() || midPrice < 1 || spreadTicks < 0 || spreadTicks > 100_000_000
                || largeOrderPct < 0 || largeOrderPct > 100) throw new IllegalArgumentException("订单生成参数不合法");
    }
    public static OrderGenerator standard(String symbol, List<String> clients) {
        return new OrderGenerator(symbol, clients, 10_000, 50, 5);
    }
    public NewOrder next(Random random) {
        String client = clients.get(random.nextInt(clients.size()));
        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        long price = Math.clamp(midPrice + random.nextInt(2 * spreadTicks + 1) - spreadTicks, 1, EngineLimits.MAX_PRICE);
        long quantity = random.nextInt(100) < largeOrderPct ? 500 + random.nextInt(1501) : 1 + random.nextInt(100);
        return NewOrder.limit(client, symbol, side, price, quantity);
    }
    public static List<String> clientIds(String prefix, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> prefix + (i < 10 ? "0" : "") + i).toList();
    }
}
