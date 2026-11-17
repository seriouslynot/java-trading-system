package com.fredlin.trading.bench;

import com.fredlin.trading.core.TradingCore;
import com.fredlin.trading.journal.FileJournal;
import com.fredlin.trading.journal.InMemoryJournal;
import com.fredlin.trading.journal.Journal;
import com.fredlin.trading.ledger.Books;
import com.fredlin.trading.sim.*;
import java.nio.file.*;
import java.util.*;

public final class JournalBenchmark {
    private static final int MATCHED = 4_000;
    private static final int STEADY = 80_000;
    private static final int WARMUP = 512;
    private static final int RUNS = 3;
    private record Variant(String name, FileJournal.FsyncPolicy policy, int batch) {}
    private record Sample(double rate, double batchP50, double batchP99, double forceP50, double forceP99) {}

    public static void main(String[] args) throws Exception {
        var out = new StringBuilder("# Trading core by journal policy\n\n")
                .append(BenchSupport.environment()).append("\n\n")
                .append("Seed 1, roughly 80% place and 20% cancel attempts, 20 seeded accounts. ")
                .append("The matched policies use the same 4,000-command prefix. ")
                .append("The 80,000-command GROUP runs are separate steady-workload measurements. ")
                .append("Every policy gets 512 warmup commands on a separate fresh journal, then three timed runs. Encode-only uses the same ledger and matching code with an in-memory text journal. ")
                .append("Batch and force columns report medians of the three run-level percentiles.\n\n")
                .append("| Workload | Policy | Commands | Batch | Commands/s | Mean µs/command | Batch P50 µs | Batch P99 µs | Force P50 µs | Force P99 µs |\n")
                .append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        var generator = OrderGenerator.standard("DEMO", OrderGenerator.clientIds("S", 20));
        var commands = MixedOrderWorkload.generate(generator, STEADY, 1);
        var matched = commands.subList(0, MATCHED);
        append(out, "Matched", new Variant("ENCODE_ONLY", null, 1), matched, generator.clients());
        append(out, "Matched", new Variant("EVERY_RECORD", FileJournal.FsyncPolicy.EVERY_RECORD, 1), matched, generator.clients());
        for (int batch : new int[]{1, 8, 64, 256, 1024}) append(out, "Matched", group(batch), matched, generator.clients());
        append(out, "Matched", new Variant("OS_DEFAULT", FileJournal.FsyncPolicy.OS_DEFAULT, 1), matched, generator.clients());
        for (int batch : new int[]{64, 256, 1024}) append(out, "Steady", group(batch), commands, generator.clients());
        RegimeBenchmark.report("journal", out);
    }

    private static Variant group(int batch) { return new Variant("GROUP", FileJournal.FsyncPolicy.GROUP, batch); }

    private static void append(StringBuilder out, String label, Variant variant,
                               List<MarketWorkload.Command> commands, List<String> clients) throws Exception {
        run(variant, commands.subList(0, WARMUP), clients);
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) samples.add(run(variant, commands, clients));
        out.append(String.format(Locale.ROOT, "| %s | %s | %d | %d | %.0f | %.2f | %.2f | %.2f | %s | %s |%n",
                label, variant.name(), commands.size(), variant.batch(),
                BenchSupport.median(samples.stream().mapToDouble(Sample::rate).toArray()),
                BenchSupport.median(samples.stream().mapToDouble(v -> 1e6 / v.rate()).toArray()),
                BenchSupport.median(samples.stream().mapToDouble(Sample::batchP50).toArray()),
                BenchSupport.median(samples.stream().mapToDouble(Sample::batchP99).toArray()),
                forceColumn(variant.policy(), samples.stream().mapToDouble(Sample::forceP50).toArray()),
                forceColumn(variant.policy(), samples.stream().mapToDouble(Sample::forceP99).toArray())));
    }

    private static String forceColumn(FileJournal.FsyncPolicy policy, double[] values) {
        return policy == null || policy == FileJournal.FsyncPolicy.OS_DEFAULT ? "—"
                : String.format(Locale.ROOT, "%.2f", BenchSupport.median(values));
    }

    private static Sample run(Variant variant, List<MarketWorkload.Command> commands,
                              List<String> clients) throws Exception {
        var forces = new ArrayList<Long>();
        if (variant.policy() == null) {
            try (var journal = new InMemoryJournal()) {
                return measure(journal, variant.batch(), commands, clients, forces);
            }
        }
        Path path = Files.createTempFile("trading-journal-bench-", ".log");
        try (var journal = new FileJournal(path, variant.policy(), forces::add)) {
            return measure(journal, variant.batch(), commands, clients, forces);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private static Sample measure(Journal journal, int batch, List<MarketWorkload.Command> commands,
                                  List<String> clients, List<Long> forces) {
        long[] batches = new long[(commands.size() - 1) / batch + 1];
        var core = new TradingCore("DEMO", journal, new Books());
        for (var client : clients) core.deposit(client, 10_000_000_000L, 10_000_000);
        core.flush();
        forces.clear();
        int fundingRejects = 0;
        long start = System.nanoTime();
        for (int i = 0; i < commands.size();) {
            long batchStart = System.nanoTime();
            int end = Math.min(i + batch, commands.size());
            int batchIndex = i / batch;
            for (; i < end; i++) {
                switch (commands.get(i)) {
                    case MarketWorkload.Place p -> {
                        if (!core.place(p.order()).accepted()) fundingRejects++;
                    }
                    case MarketWorkload.Cancel c -> core.cancel(c.orderId(), c.clientId());
                    case MarketWorkload.Modify m -> throw new IllegalStateException("Unexpected amendment");
                }
            }
            core.flush();
            batches[batchIndex] = System.nanoTime() - batchStart;
        }
        double rate = commands.size() * 1e9 / (System.nanoTime() - start);
        if (fundingRejects != 0) throw new IllegalStateException("Benchmark placements rejected: " + fundingRejects);
        Arrays.sort(batches);
        long[] sortedForces = forces.stream().mapToLong(Long::longValue).sorted().toArray();
        return new Sample(rate, BenchSupport.percentile(batches, .50) / 1000d,
                BenchSupport.percentile(batches, .99) / 1000d,
                BenchSupport.percentile(sortedForces, .50) / 1000d,
                BenchSupport.percentile(sortedForces, .99) / 1000d);
    }
}
