package com.fredlin.trading.bench;

import com.fredlin.trading.core.TradingCore;
import com.fredlin.trading.engine.NewOrder;
import com.fredlin.trading.journal.FileJournal;
import com.fredlin.trading.ledger.Books;
import com.fredlin.trading.sim.OrderGenerator;
import java.nio.file.*;
import java.util.*;

public final class JournalBenchmark {
    public static void main(String[] args) throws Exception {
        var out = new StringBuilder("# Trading core by journal policy\n\n| Policy | Commands | Batch size | Commands/s |\n|---|---:|---:|---:|\n");
        for (var policy : FileJournal.FsyncPolicy.values()) {
            int steps = policy == FileJournal.FsyncPolicy.EVERY_RECORD ? 4_000 : 80_000;
            int batch = policy == FileJournal.FsyncPolicy.GROUP ? 256 : 1;
            run(policy, batch, 1200);
            double rate = run(policy, batch, steps);
            out.append(String.format(Locale.ROOT, "| %s | %d | %d | %.0f |%n", policy, steps, batch, rate));
        }
        RegimeBenchmark.report("journal", out);
    }
    private static double run(FileJournal.FsyncPolicy policy, int batch, int steps) throws Exception {
        Path temp = Files.createTempFile("trading-journal-bench-", ".log");
        var clients = OrderGenerator.clientIds("S", 20);
        var generator = OrderGenerator.standard("DEMO", clients);
        var random = new Random(1);
        var orders = new NewOrder[steps];
        for (int i = 0; i < steps; i++) orders[i] = generator.next(random);
        try (var journal = new FileJournal(temp, policy)) {
            var core = new TradingCore("DEMO", journal, new Books());
            for (String c : clients) core.deposit(c, 100_000_000L, 100_000);
            core.flush();
            long start = System.nanoTime();
            for (int i = 0; i < steps; i++) {
                core.place(orders[i]);
                if ((i + 1) % batch == 0) core.flush();
            }
            core.flush();
            return steps * 1e9 / (System.nanoTime() - start);
        } finally { Files.deleteIfExists(temp); }
    }
}
