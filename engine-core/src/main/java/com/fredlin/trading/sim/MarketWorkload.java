package com.fredlin.trading.sim;

import com.fredlin.trading.engine.*;
import java.util.*;

public final class MarketWorkload {
    public sealed interface Command permits Place, Cancel, Modify {}
    public record Place(NewOrder order) implements Command {}
    public record Cancel(long orderId, String clientId) implements Command {}
    public record Modify(long orderId, String clientId, long newPrice, long newQuantity) implements Command {}
    public record Parameters(String symbol, int steps, long startMid, int clients, int spreadTicks,
                             int iocPct, int modifyPct, int cancelPct, int amendMeanSteps, int cancelMeanSteps) {
        public Parameters {
            if (symbol == null || steps < 1 || startMid < 1 || clients < 2 || spreadTicks < 1
                    || spreadTicks > 100_000_000 || iocPct < 0 || iocPct > 100
                    || modifyPct < 0 || modifyPct > 100 || cancelPct < 0 || cancelPct > 100
                    || amendMeanSteps < 1 || cancelMeanSteps < 1)
                throw new IllegalArgumentException("负载参数不合法");
        }
        public static Parameters standard(int steps) { return new Parameters("DEMO", steps, 10_000, 50, 8, 15, 20, 95, 200, 500); }
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
            while (!tasks.isEmpty() && tasks.peek().step() <= i) {
                var task = tasks.remove();
                result.add(task.modify() ? new Modify(task.id(), task.client(), price(center, task.priceOffset()), task.quantity())
                        : new Cancel(task.id(), task.client()));
            }
            String client = clients.get(random.nextInt(clients.size()));
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            boolean ioc = random.nextInt(100) < p.iocPct();
            boolean aggressive = ioc || random.nextInt(100) < 10;
            long price = price(center, offset(random, p, side, aggressive));
            long quantity = 1 + random.nextInt(100);
            result.add(new Place(new NewOrder(client, p.symbol(), side, price, quantity, null,
                    ioc ? TimeInForce.IOC : TimeInForce.GTC)));
            id++;
            if (!ioc) {
                if (random.nextInt(100) < p.modifyPct()) {
                    long due = i + lifetime(random, p.amendMeanSteps());
                    tasks.add(new Scheduled(due, id, client, offset(random, p, side, random.nextInt(100) < 10),
                            1 + random.nextInt(100), true));
                }
                if (random.nextInt(100) < p.cancelPct()) {
                    long due = i + lifetime(random, p.cancelMeanSteps());
                    tasks.add(new Scheduled(due, id, client, 0, 0, false));
                }
            }
        }
        return List.copyOf(result);
    }
    private static long offset(Random random, Parameters p, Side side, boolean aggressive) {
        long distance = aggressive ? 1 + random.nextInt(10) : distance(random, p);
        return (side == Side.BUY) == aggressive ? distance : -distance;
    }

    static long distance(Random random, Parameters p) {
        if (random.nextInt(5) != 0) {
            return Math.clamp(Math.round(p.spreadTicks() + 3 * random.nextGaussian()), 1, 1_000);
        }
        // Inverse CDF of a truncated power-law density with exponent 2.23.
        double minimum = Math.min(p.spreadTicks() + 3.0, 1_000);
        double power = 1 - 2.23;
        double lower = Math.pow(minimum, power);
        double upper = Math.pow(1_000, power);
        return Math.clamp(Math.round(Math.pow(lower + random.nextDouble() * (upper - lower), 1 / power)), 1, 1_000);
    }

    private static long lifetime(Random random, int mean) {
        return Math.max(1, Math.round(-Math.log1p(-random.nextDouble()) * mean));
    }
    private static long price(long center, long distance) { return Math.clamp(center + distance, 1, EngineLimits.MAX_PRICE); }
}
