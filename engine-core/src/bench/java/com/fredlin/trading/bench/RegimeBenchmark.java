package com.fredlin.trading.bench;

import com.fredlin.trading.engine.MatchingEngine;
import com.fredlin.trading.sim.*;
import java.nio.file.*;
import java.util.*;

public final class RegimeBenchmark {
    public static void main(String[] args) throws Exception {
        int steps = args.length == 0 ? 300_000 : Integer.parseInt(args[0]);
        var p = MarketWorkload.Parameters.standard(steps);
        var out = new StringBuilder("# Matching engine by market regime\n\n")
                .append("| Market regime | Commands | Median ns per command (three runs) |\n|---|---:|---:|\n");
        for (var regime : MarketRegime.values()) {
            var commands = MarketWorkload.generate(regime, p, 1);
            run(commands);
            long[] times = {run(commands), run(commands), run(commands)};
            Arrays.sort(times);
            out.append(String.format(Locale.ROOT, "| %s | %d | %.0f |%n", regime.name(), steps, times[1] / (double)steps));
        }
        report("regime", out);
    }
    private static long run(List<MarketWorkload.Command> commands) {
        var engine = new MatchingEngine("DEMO");
        long start = System.nanoTime();
        for (var cmd : commands) switch (cmd) {
            case MarketWorkload.Place p -> engine.place(p.order());
            case MarketWorkload.Cancel c -> engine.cancel(c.orderId(), c.clientId());
            case MarketWorkload.Modify m -> engine.modify(m.orderId(), m.clientId(), m.newPrice(), m.newQuantity());
        }
        return System.nanoTime() - start;
    }
    static void report(String name, CharSequence out) throws Exception {
        var target = Path.of("build", "bench", name + ".md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, out);
        System.out.print(out);
    }
}
