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
import java.util.concurrent.atomic.*;

public final class PipelineBenchmark {
    public static void main(String[] args) throws Exception {
        var out = new StringBuilder("# 进程内接入链路 · 512 并发\n\n| 策略 | 命令 | 命令/秒 | 快速拒绝 | 超时 | 对账差异 |\n|---|---:|---:|---:|---:|---:|\n");
        for (var policy : List.of(FileJournal.FsyncPolicy.EVERY_RECORD, FileJournal.FsyncPolicy.GROUP)) {
            run(policy, 2048);
            var s = run(policy, policy == FileJournal.FsyncPolicy.EVERY_RECORD ? 4_096 : 65_536);
            out.append(String.format(Locale.ROOT, "| %s | %d | %.0f | %d | %d | %d |%n",
                    policy, s.orders, s.rate, s.fastRejects, s.timeouts, s.anomalies));
        }
        var report = Path.of("build", "bench", "pipeline.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, out);
        System.out.print(out);
    }
    private record Stats(int orders, double rate, long fastRejects, long timeouts, int anomalies) {}
    private static Stats run(FileJournal.FsyncPolicy policy, int count) throws Exception {
        Path path = Files.createTempFile("trading-pipeline-bench-", ".log");
        var props = new TradingProperties("DEMO", path, policy, 256, 2000, 30_000, 500, 1000, "", true);
        var generator = OrderGenerator.standard("DEMO", OrderGenerator.clientIds("S", 20));
        var random = new Random(1);
        var orders = new NewOrder[count];
        for (int i = 0; i < count; i++) orders[i] = generator.next(random);
        try (var engine = new EngineService(props); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            engine.protection(false, true, true, null, null, null);
            var index = new AtomicInteger();
            var fastRejects = new LongAdder();
            var timeouts = new LongAdder();
            var startGate = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 512; i++) futures.add(executor.submit(() -> {
                startGate.await();
                for (int n; (n = index.getAndIncrement()) < count;) {
                    var o = engine.place(orders[n]).outcome();
                    if (o == Outcome.QUEUE_FULL || o == Outcome.RATE_LIMITED) fastRejects.increment();
                    if (o == Outcome.TIMEOUT) timeouts.increment();
                }
                return null;
            }));
            long start = System.nanoTime();
            startGate.countDown();
            for (var f : futures) f.get();
            double rate = count * 1e9 / (System.nanoTime() - start);
            int anomalies = engine.reconcile().report().anomalies().size();
            return new Stats(count, rate, fastRejects.sum(), timeouts.sum(), anomalies);
        } finally { Files.deleteIfExists(path); }
    }
}
