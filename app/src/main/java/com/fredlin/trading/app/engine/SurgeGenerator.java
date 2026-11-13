package com.fredlin.trading.app.engine;

import com.fredlin.trading.sim.OrderGenerator;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

@Service
public final class SurgeGenerator implements AutoCloseable {
    public record Status(boolean running, int ratePerSecond, int seconds, long submitted,
                         long startedAtMillis, long completed, String error) {}
    private final EngineService engine;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<String> clients = OrderGenerator.clientIds("S", 20);
    private volatile Run run;
    private boolean closed;

    private static final class Run {
        final int rate, seconds;
        final long startedAt = System.currentTimeMillis();
        final AtomicLong submitted = new AtomicLong(), completed = new AtomicLong();
        volatile boolean running = true;
        volatile String error;
        Thread thread;
        Run(int rate, int seconds) { this.rate = rate; this.seconds = seconds; }
    }
    public SurgeGenerator(EngineService engine) { this.engine = engine; }
    public synchronized Status start(int rate, int seconds) {
        if (rate < 1 || rate > 20_000 || seconds < 1 || seconds > 120) throw new IllegalArgumentException("速率须为 1–20000，时长须为 1–120 秒");
        if (closed || (run != null && (run.running || run.submitted.get() != run.completed.get())))
            throw new IllegalArgumentException("已有流量暴涨正在运行或收尾");
        var r = new Run(rate, seconds);
        run = r;
        r.thread = Thread.ofPlatform().daemon().name("surge-producer").start(() -> generate(r));
        return status();
    }
    private void generate(Run r) {
        try {
            engine.clearAccounts(Set.copyOf(clients));
            var generator = OrderGenerator.standard(engine.symbol(), clients);
            var random = new Random(r.startedAt);
            long start = System.nanoTime();
            long total = (long)r.rate * r.seconds;
            for (long i = 0; i < total && r.running && !engine.failed(); i++) {
                long deadline = start + i * 1_000_000_000L / r.rate;
                long remaining;
                while (r.running && (remaining = deadline - System.nanoTime()) > 0) LockSupport.parkNanos(remaining);
                if (!r.running) break;
                var order = generator.next(random);
                r.submitted.incrementAndGet();
                try {
                    executor.execute(() -> {
                        try { engine.place(order); }
                        finally { r.completed.incrementAndGet(); }
                    });
                } catch (RuntimeException ex) {
                    r.completed.incrementAndGet();
                    throw ex;
                }
            }
        } catch (RuntimeException ex) {
            if (r.running) r.error = ex.getMessage();
        } finally {
            r.running = false;
        }
    }
    public synchronized Status stop() {
        var r = run;
        if (r != null) { r.running = false; r.thread.interrupt(); }
        return status();
    }
    public Status status() {
        var r = run;
        return r == null ? new Status(false, 0, 0, 0, 0, 0, null)
                : new Status(r.running, r.rate, r.seconds, r.submitted.get(), r.startedAt, r.completed.get(), r.error);
    }
    @Override @PreDestroy public synchronized void close() {
        if (closed) return;
        closed = true;
        stop();
        executor.close();
    }
}
