package com.fredlin.trading.bench;

import com.fredlin.trading.engine.*;
import com.fredlin.trading.sim.*;
import java.nio.file.*;
import java.util.*;

public final class RegimeBenchmark {
    private static final int WARMUPS = 2;
    private static final int RUNS = 11;
    private static volatile long consumed;

    private record Sample(double rate, double baselineNs, long p50, long p99, long p999) {}

    public static void main(String[] args) throws Exception {
        int steps = args.length == 0 ? 300_000 : Integer.parseInt(args[0]);
        var parameters = MarketWorkload.Parameters.standard(steps);
        var workloads = new EnumMap<MarketRegime, List<MarketWorkload.Command>>(MarketRegime.class);
        var samples = new EnumMap<MarketRegime, List<Sample>>(MarketRegime.class);
        var regimes = List.of(MarketRegime.values());
        for (var regime : regimes) {
            workloads.put(regime, MarketWorkload.generate(regime, parameters, 1));
            samples.put(regime, new ArrayList<>());
        }
        for (int round = -WARMUPS; round < RUNS; round++) {
            for (var regime : round % 2 == 0 ? regimes : regimes.reversed()) {
                var sample = run(workloads.get(regime));
                if (round >= 0) samples.get(regime).add(sample);
            }
        }
        var out = new StringBuilder("# Matching engine by market regime\n\n")
                .append(BenchSupport.environment()).append("\n\n")
                .append("Seed 1. Two warmup rounds and eleven timed rounds per regime. ")
                .append("Each run starts with an empty book and consumes order, trade, and sequence results. ")
                .append("Latency is measured in a separate pass, sampling every 32nd command. ")
                .append("The dispatch-only baseline is reported without subtraction. Values are medians across runs.\n\n")
                .append("| Regime | Steps | Commands | Commands/s | Mean ns/command | Dispatch ns/command | P50 ns | P99 ns | P99.9 ns |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var regime : regimes) {
            var runs = samples.get(regime);
            out.append(String.format(Locale.ROOT, "| %s | %d | %d | %.0f | %.1f | %.1f | %.0f | %.0f | %.0f |%n",
                    regime, steps, workloads.get(regime).size(),
                    BenchSupport.median(runs.stream().mapToDouble(Sample::rate).toArray()),
                    BenchSupport.median(runs.stream().mapToDouble(v -> 1e9 / v.rate()).toArray()),
                    BenchSupport.median(runs.stream().mapToDouble(Sample::baselineNs).toArray()),
                    BenchSupport.median(runs.stream().mapToDouble(Sample::p50).toArray()),
                    BenchSupport.median(runs.stream().mapToDouble(Sample::p99).toArray()),
                    BenchSupport.median(runs.stream().mapToDouble(Sample::p999).toArray())));
        }
        out.append("\nConsumed result checksum: ").append(consumed).append("\n");
        report("regime", out);
    }

    private static Sample run(List<MarketWorkload.Command> commands) {
        long sum = 0;
        long start = System.nanoTime();
        for (var command : commands) {
            sum += switch (command) {
                case MarketWorkload.Place p -> p.order().quantity();
                case MarketWorkload.Cancel c -> c.orderId();
                case MarketWorkload.Modify m -> m.newQuantity();
            };
        }
        long baseline = System.nanoTime() - start;
        consumed = sum;
        var engine = new MatchingEngine("DEMO");
        sum = 0;
        start = System.nanoTime();
        for (var command : commands) sum += execute(engine, command);
        long elapsed = System.nanoTime() - start;
        consumed = sum;

        engine = new MatchingEngine("DEMO");
        long[] timings = new long[(commands.size() - 1) / 32 + 1];
        for (int i = 0; i < commands.size(); i++) {
            var command = commands.get(i);
            if (i % 32 == 0) {
                long before = System.nanoTime();
                sum += execute(engine, command);
                timings[i / 32] = System.nanoTime() - before;
            } else {
                sum += execute(engine, command);
            }
        }
        consumed = sum;
        Arrays.sort(timings);
        return new Sample(commands.size() * 1e9 / elapsed, baseline / (double) commands.size(),
                BenchSupport.percentile(timings, .5), BenchSupport.percentile(timings, .99),
                BenchSupport.percentile(timings, .999));
    }

    private static long execute(MatchingEngine engine, MarketWorkload.Command command) {
        return switch (command) {
            case MarketWorkload.Place p -> {
                var result = engine.place(p.order());
                yield consume(result.order(), result.trades(), engine.lastSequence());
            }
            case MarketWorkload.Cancel c -> {
                var result = engine.cancel(c.orderId(), c.clientId());
                yield result.sequence() + result.status().ordinal()
                        + (result.order() == null ? 0 : result.order().cancelledQuantity());
            }
            case MarketWorkload.Modify m -> {
                var result = engine.modify(m.orderId(), m.clientId(), m.newPrice(), m.newQuantity());
                yield consume(result.order(), result.trades(), result.sequence()) + result.status().ordinal();
            }
        };
    }

    private static long consume(OrderView order, List<Trade> trades, long sequence) {
        long value = sequence + (order == null ? 0 : order.orderId() + order.remainingQuantity());
        for (var trade : trades) value += trade.tradeId() + trade.quantity() + trade.price();
        return value;
    }

    static void report(String name, CharSequence out) throws Exception {
        var target = Path.of("build", "bench", name + ".md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, out);
        System.out.print(out);
    }
}
