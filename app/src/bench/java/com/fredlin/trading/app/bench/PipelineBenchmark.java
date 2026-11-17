package com.fredlin.trading.app.bench;

import com.fredlin.trading.app.config.TradingProperties;
import com.fredlin.trading.app.engine.EngineService;
import com.fredlin.trading.journal.FileJournal;
import com.fredlin.trading.ops.Outcome;
import com.fredlin.trading.sim.OrderGenerator;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class PipelineBenchmark {
    private static final int WORKERS = 512;
    private static final int MAX_OPEN_PER_WORKER = 8;
    private static final long SEED = 1;

    private record Variant(FileJournal.FsyncPolicy policy, int batch) {
        String name() { return policy == FileJournal.FsyncPolicy.GROUP ? "GROUP " + batch : policy.name(); }
    }
    private record RestingOrder(long id, String owner) {}
    private record Sample(long elapsed, long placed, long cancelled, long[] counts, long[] admittedLatencies,
                          int differences) {}

    public static void main(String[] args) throws Exception {
        int warmup = duration(args, 0, 3);
        int measure = duration(args, 1, 10);
        var out = new StringBuilder("# In-process order pipeline benchmark\n\n")
                .append("JDK ").append(System.getProperty("java.runtime.version")).append(", ")
                .append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version"))
                .append(", ").append(System.getProperty("os.arch")).append(", ")
                .append(Runtime.getRuntime().availableProcessors()).append(" logical processors.\n\n")
                .append(WORKERS).append(" closed-loop virtual-thread workers, seed ").append(SEED)
                .append(". Each worker has up to ").append(MAX_OPEN_PER_WORKER)
                .append(" candidate resting orders and attempts a cancel on 20% of iterations with an order available. ")
                .append("Workers cancel instead of placing when their own limit is reached. ")
                .append("Each policy uses a fresh journal and ").append(warmup)
                .append(" s warmup followed by ").append(measure).append(" s measurement. ")
                .append("HTTP, rate limiting, and queue capacity enforcement are excluded. ")
                .append("Request timeout is 30 s. Measured operations drain at the end, and wall time includes that drain. ")
                .append("Latency includes queue time and excludes fast rejections.\n\n")
                .append("| Policy | Elapsed s | Place | Cancel | Completed/s | Successful | Business rejected | Unadmitted | Timeout | P50 ms | P99 ms | P99.9 ms | Latency samples | Recon differences |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        var variants = List.of(new Variant(FileJournal.FsyncPolicy.EVERY_RECORD, 1),
                new Variant(FileJournal.FsyncPolicy.GROUP, 64), new Variant(FileJournal.FsyncPolicy.GROUP, 256),
                new Variant(FileJournal.FsyncPolicy.GROUP, 1024), new Variant(FileJournal.FsyncPolicy.OS_DEFAULT, 1));
        for (var variant : variants) {
            var sample = run(variant, warmup, measure);
            long success = count(sample, Outcome.SUCCESS);
            long rejected = count(sample, Outcome.BUSINESS_REJECTED);
            long unadmitted = count(sample, Outcome.QUEUE_FULL) + count(sample, Outcome.RATE_LIMITED);
            var latency = sample.admittedLatencies();
            out.append(String.format(Locale.ROOT, "| %s | %.3f | %d | %d | %.0f | %d | %d | %d | %d | %.3f | %.3f | %.3f | %d | %d |%n",
                    variant.name(), sample.elapsed() / 1e9, sample.placed(), sample.cancelled(),
                    (success + rejected) * 1e9 / sample.elapsed(), success, rejected, unadmitted,
                    count(sample, Outcome.TIMEOUT), percentile(latency, .5), percentile(latency, .99),
                    percentile(latency, .999), latency.length, sample.differences()));
        }
        var report = Path.of("build", "bench", "pipeline.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, out);
        System.out.print(out);
    }

    private static int duration(String[] args, int index, int fallback) {
        int seconds = args.length > index ? Integer.parseInt(args[index]) : fallback;
        if (seconds < 1 || seconds > 120) throw new IllegalArgumentException("Duration must be 1–120 seconds");
        return seconds;
    }

    private static long count(Sample sample, Outcome outcome) { return sample.counts()[outcome.ordinal()]; }

    private static double percentile(long[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.ceil(sorted.length * p) - 1] / 1e6;
    }

    private static Sample run(Variant variant, int warmup, int measure) throws Exception {
        var path = Files.createTempFile("trading-pipeline-bench-", ".log");
        var props = new TradingProperties("DEMO", path, variant.policy(), variant.batch(), 2_000, 30_000, 500, 1_000, "", true);
        var generator = OrderGenerator.standard("DEMO", OrderGenerator.clientIds("S", 20));
        try (var engine = new EngineService(props); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            engine.protection(false, false, true, null, null, null);
            var ready = new CountDownLatch(WORKERS);
            var startGate = new CompletableFuture<Long>();
            List<Future<Worker>> futures = new ArrayList<>(WORKERS);
            for (int i = 0; i < WORKERS; i++) {
                int workerId = i;
                futures.add(executor.submit(() -> {
                    var worker = new Worker(workerId);
                    ready.countDown();
                    long start = startGate.get();
                    long measureStart = start + TimeUnit.SECONDS.toNanos(warmup);
                    long end = measureStart + TimeUnit.SECONDS.toNanos(measure);
                    worker.run(engine, generator, measureStart, end);
                    return worker;
                }));
            }
            ready.await();
            long start = System.nanoTime();
            startGate.complete(start);
            List<Worker> workers = new ArrayList<>(WORKERS);
            for (var future : futures) workers.add(future.get());
            long elapsed = System.nanoTime() - start - TimeUnit.SECONDS.toNanos(warmup);
            long[] counts = new long[Outcome.values().length];
            long[] latencies = new long[workers.stream().mapToInt(w -> w.latencyCount).sum()];
            long placed = 0, cancelled = 0;
            int offset = 0;
            for (var worker : workers) {
                for (int i = 0; i < counts.length; i++) counts[i] += worker.counts[i];
                System.arraycopy(worker.latencies, 0, latencies, offset, worker.latencyCount);
                offset += worker.latencyCount;
                placed += worker.placed;
                cancelled += worker.cancelled;
            }
            Arrays.sort(latencies);
            return new Sample(elapsed, placed, cancelled, counts, latencies,
                    engine.reconcile().report().anomalies().size());
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private static final class Worker {
        private final Random random;
        private final Deque<RestingOrder> resting = new ArrayDeque<>();
        private final long[] counts = new long[Outcome.values().length];
        private long[] latencies = new long[128];
        private long placed, cancelled;
        private int latencyCount;

        Worker(int id) { random = new Random(SEED + id); }

        void run(EngineService engine, OrderGenerator generator, long measureStart, long end) {
            for (long before; (before = System.nanoTime()) < end;) {
                boolean measuring = before >= measureStart;
                Outcome outcome;
                if (!resting.isEmpty() && (resting.size() == MAX_OPEN_PER_WORKER || random.nextInt(5) == 0)) {
                    var toCancel = resting.removeFirst();
                    outcome = engine.cancel(toCancel.id(), toCancel.owner()).outcome();
                    if (outcome == Outcome.QUEUE_FULL || outcome == Outcome.TIMEOUT) resting.addLast(toCancel);
                    if (measuring) cancelled++;
                } else {
                    var response = engine.place(generator.next(random));
                    outcome = response.outcome();
                    if (response.value() != null && response.value().order().remainingQuantity() > 0) {
                        var order = response.value().order();
                        resting.addLast(new RestingOrder(order.orderId(), order.clientId()));
                    }
                    if (measuring) placed++;
                }
                if (measuring) record(outcome, System.nanoTime() - before);
            }
        }

        private void record(Outcome outcome, long nanos) {
            counts[outcome.ordinal()]++;
            if (outcome == Outcome.QUEUE_FULL || outcome == Outcome.RATE_LIMITED) return;
            if (latencyCount == latencies.length) latencies = Arrays.copyOf(latencies, latencies.length * 2);
            latencies[latencyCount++] = nanos;
        }
    }
}
