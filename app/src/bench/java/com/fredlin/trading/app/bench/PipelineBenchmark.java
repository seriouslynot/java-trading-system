package com.fredlin.trading.app.bench;

import com.fredlin.trading.app.config.TradingProperties;
import com.fredlin.trading.app.engine.EngineService;
import com.fredlin.trading.engine.NewOrder;
import com.fredlin.trading.journal.FileJournal;
import com.fredlin.trading.ops.Outcome;
import com.fredlin.trading.sim.OrderGenerator;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class PipelineBenchmark {
    private static final int CLIENTS = 512;
    private static final int RUNS = 3;
    private static final List<FileJournal.FsyncPolicy> POLICIES =
            List.of(FileJournal.FsyncPolicy.EVERY_RECORD, FileJournal.FsyncPolicy.GROUP);

    public static void main(String[] args) throws Exception {
        int count = args.length == 0 ? 4096 : Integer.parseInt(args[0]);
        if (count < CLIENTS) throw new IllegalArgumentException("至少需要 " + CLIENTS + " 条命令");
        var orders = orders(count);
        var warmup = Arrays.copyOf(orders, CLIENTS);
        var stats = new EnumMap<FileJournal.FsyncPolicy, List<Stats>>(FileJournal.FsyncPolicy.class);
        for (var policy : POLICIES) {
            run(policy, warmup);
            stats.put(policy, new ArrayList<>());
        }
        for (int run = 0; run < RUNS; run++) {
            var sequence = run % 2 == 0 ? POLICIES : POLICIES.reversed();
            for (var policy : sequence) stats.get(policy).add(run(policy, orders));
        }
        var report = new StringBuilder("# 进程内接入层基准\n\n")
                .append("512 个并发任务，同一条种子为 1 的输入序列。每个策略 3 次，交替顺序。")
                .append("每轮用新的日志和演示账户。含排队、撮合、记账与落盘，排除 HTTP。\n\n")
                .append("| 策略 | 轮次 | 命令 | 命令/秒 | P99 ms | 成功 | 业务拒绝 | 快速拒绝 | 超时 | 对账差异 |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var policy : POLICIES) {
            int i = 0;
            for (var s : stats.get(policy)) {
                report.append(String.format(Locale.ROOT, "| %s | %d | %d | %.0f | %.2f | %d | %d | %d | %d | %d |%n",
                        policy, ++i, count, s.rate(), s.p99(), s.success(), s.rejected(),
                        s.fastRejects(), s.timeouts(), s.anomalies()));
            }
        }
        report.append("\n| 策略 | 命令/秒中位数 | P99 ms 中位数 |\n|---|---:|---:|\n");
        for (var policy : POLICIES) {
            var samples = stats.get(policy);
            double rate = median(samples.stream().mapToDouble(Stats::rate).toArray());
            double p99 = median(samples.stream().mapToDouble(Stats::p99).toArray());
            report.append(String.format(Locale.ROOT, "| %s | %.0f | %.2f |%n", policy, rate, p99));
        }
        var output = Path.of("build", "bench", "pipeline.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report);
        System.out.print(report);
    }

    private static double median(double[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    private static NewOrder[] orders(int count) {
        var generator = OrderGenerator.standard("DEMO", OrderGenerator.clientIds("S", 20));
        var random = new Random(1);
        var orders = new NewOrder[count];
        for (int i = 0; i < count; i++) orders[i] = generator.next(random);
        return orders;
    }

    private record Stats(double rate, double p99, long success, long rejected,
                         long fastRejects, long timeouts, int anomalies) {}

    private static Stats run(FileJournal.FsyncPolicy policy, NewOrder[] orders) throws Exception {
        var path = Files.createTempFile("trading-pipeline-bench-", ".log");
        var props = new TradingProperties("DEMO", path, policy, 256, 2000, 30_000, 500, 1000, "", true);
        try (var engine = new EngineService(props); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            engine.protection(false, true, true, null, null, null);
            var nextOrder = new AtomicInteger();
            var ready = new CountDownLatch(CLIENTS);
            var startGate = new CountDownLatch(1);
            var latencies = new long[orders.length];
            var outcomes = new Outcome[orders.length];
            List<Future<?>> workers = new ArrayList<>();
            for (int i = 0; i < CLIENTS; i++) {
                workers.add(executor.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    for (int n; (n = nextOrder.getAndIncrement()) < orders.length;) {
                        long before = System.nanoTime();
                        outcomes[n] = engine.place(orders[n]).outcome();
                        latencies[n] = System.nanoTime() - before;
                    }
                    return null;
                }));
            }
            ready.await();
            long start = System.nanoTime();
            startGate.countDown();
            for (var worker : workers) worker.get();
            double rate = orders.length * 1e9 / (System.nanoTime() - start);

            var counts = new EnumMap<Outcome, Long>(Outcome.class);
            for (Outcome outcome : outcomes) counts.merge(outcome, 1L, Long::sum);
            long[] admitted = new long[orders.length];
            int length = 0;
            for (int i = 0; i < outcomes.length; i++) {
                if (outcomes[i] == Outcome.RATE_LIMITED || outcomes[i] == Outcome.QUEUE_FULL) continue;
                admitted[length++] = latencies[i];
            }
            Arrays.sort(admitted, 0, length);
            double p99 = length == 0 ? 0 : admitted[(int)Math.ceil(length * 0.99) - 1] / 1e6;
            return new Stats(rate, p99, counts.getOrDefault(Outcome.SUCCESS, 0L),
                    counts.getOrDefault(Outcome.BUSINESS_REJECTED, 0L),
                    counts.getOrDefault(Outcome.RATE_LIMITED, 0L) + counts.getOrDefault(Outcome.QUEUE_FULL, 0L),
                    counts.getOrDefault(Outcome.TIMEOUT, 0L), engine.reconcile().report().anomalies().size());
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
