package com.fredlin.trading.sim;

import com.fredlin.trading.engine.*;
import java.util.*;

public final class MarketWorkload {
    public sealed interface Command permits Place, Cancel, Modify {}
    public record Place(NewOrder order) implements Command {}
    public record Cancel(long orderId, String clientId) implements Command {}
    public record Modify(long orderId, String clientId, long newPrice, long newQuantity) implements Command {}
    public record Parameters(String symbol, int steps, long startMid, int clients, int spreadTicks,
                             int iocPct, int modifyPct, int cancelPct, int maxLifetime) {
        public Parameters {
            if (symbol == null || steps < 1 || startMid < 1 || clients < 2 || spreadTicks < 1
                    || spreadTicks > 100_000_000 || iocPct < 0 || iocPct > 100
                    || modifyPct < 0 || modifyPct > 100 || cancelPct < 0 || cancelPct > 100
                    || maxLifetime < 3 || maxLifetime > 10_000_000)
                throw new IllegalArgumentException("负载参数不合法");
        }
        public static Parameters standard(int steps) { return new Parameters("DEMO", steps, 10_000, 20, 50, 15, 20, 95, 100); }
    }
    private record Scheduled(long step, long id, String client, long priceOffset, long quantity, boolean modify) {}

    private MarketWorkload() {}
    public static List<Command> generate(MarketRegime regime, Parameters p, long seed) {
        var random = new Random(seed);
        var mid = new MidPricePath(regime, p.startMid(), p.steps(), seed ^ 0x6a09e667f3bcc909L);
        var clients = OrderGenerator.clientIds("C", p.clients());
        var tasks = new PriorityQueue<Scheduled>(Comparator.comparingLong(Scheduled::step).thenComparingLong(Scheduled::id));
        List<Command> result = new ArrayList<>(p.steps());
        long id = 0;
        for (int i = 0; i < p.steps(); i++) {
            long center = mid.next(i);
            if (!tasks.isEmpty() && tasks.peek().step() <= i) {
                var task = tasks.remove();
                result.add(task.modify() ? new Modify(task.id(), task.client(), price(center, task.priceOffset()), task.quantity())
                        : new Cancel(task.id(), task.client()));
                continue;
            }
            String client = clients.get(random.nextInt(clients.size()));
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            long price = price(center, distance(random, p));
            long quantity = 1 + random.nextInt(100);
            boolean ioc = random.nextInt(100) < p.iocPct();
            result.add(new Place(new NewOrder(client, p.symbol(), side, price, quantity, null,
                    ioc ? TimeInForce.IOC : TimeInForce.GTC)));
            id++;
            if (!ioc) {
                int life = 3 + random.nextInt(p.maxLifetime() - 2);
                if (random.nextInt(100) < p.modifyPct()) {
                    tasks.add(new Scheduled((long)i + life / 2, id, client, distance(random, p),
                            1 + random.nextInt(100), true));
                }
                if (random.nextInt(100) < p.cancelPct()) tasks.add(new Scheduled((long)i + life, id, client, 0, 0, false));
            }
        }
        return List.copyOf(result);
    }
    static long distance(Random random, Parameters p) { return random.nextInt(2 * p.spreadTicks() + 1) - p.spreadTicks(); }
    private static long price(long center, long distance) { return Math.clamp(center + distance, 1, EngineLimits.MAX_PRICE); }
}
