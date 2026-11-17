package com.fredlin.trading.app.engine;

import com.fredlin.trading.app.config.TradingProperties;
import com.fredlin.trading.core.TradingCore;
import com.fredlin.trading.engine.*;
import com.fredlin.trading.journal.*;
import com.fredlin.trading.ledger.Books;
import com.fredlin.trading.ops.*;
import com.fredlin.trading.recon.*;
import com.fredlin.trading.sim.OrderGenerator;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

@Service
public final class EngineService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(EngineService.class);
    private static final int KEEP = EngineLimits.FINISHED_ORDERS_TO_KEEP;
    private static final int POSTING_WINDOW = 1000;
    private static final long SNAPSHOT_NS = TimeUnit.MILLISECONDS.toNanos(20);
    public record Protection(boolean rateLimitEnabled, boolean queueLimitEnabled, boolean cancelPriorityEnabled,
                             int queueCapacity, long rateLimitBurst, long rateLimitPerSecond) {}
    public record ReconResult(ReconReport report, InjectionScore score) {}
    public static final class Unavailable extends RuntimeException {
        private final Outcome outcome;
        public Unavailable(Outcome outcome, String reason) { super(reason); this.outcome = outcome; }
        public Outcome outcome() { return outcome; }
    }

    private final TradingProperties props;
    private final FileJournal journal;
    private final TradingCore core;
    private final Books books = new Books();
    private final AnomalyInjector injector = new AnomalyInjector(books, 0, 19);
    private final TokenBucket bucket;
    private final IdempotencyStore ids = new IdempotencyStore(KEEP);
    private final MetricsWindow metrics = new MetricsWindow(300);
    private final AlertEvaluator alerts = new AlertEvaluator(AlertRule.defaults());
    private final Object queueLock = new Object();
    private final Deque<Job<?>> cancels = new ArrayDeque<>();
    private final Deque<Job<?>> ordinary = new ArrayDeque<>();
    private final Deque<Trade> recent = new ArrayDeque<>();
    private final Map<Long, OrderView> orders = new ConcurrentHashMap<>();
    private final LinkedHashSet<Long> terminal = new LinkedHashSet<>();
    private final LinkedHashMap<Long, PlaceResult> originals = new LinkedHashMap<>();
    private final Object originalLock = new Object();
    private final Thread worker;
    private final ScheduledExecutorService sampler;
    private volatile Snapshot snapshot;
    private volatile ReconResult latest;
    private volatile Protection protection;
    private volatile boolean running = true, failed;
    private long ticket, lastPublish;
    private boolean dirty;

    public EngineService(TradingProperties props) {
        this.props = props;
        protection = new Protection(true, true, true, props.queueCapacity(),
                props.rateLimitBurst(), props.rateLimitPerSecond());
        bucket = new TokenBucket(props.rateLimitBurst(), props.rateLimitPerSecond());
        journal = new FileJournal(props.journalPath(), props.journalFsync());
        try {
            var recovery = TradingCore.recover(props.symbol(), journal, injector, this::onReplay);
            core = recovery.core();
            if (recovery.records().isEmpty() && props.seedDemoAccounts()) {
                for (var client : demoAccounts()) core.deposit(client, ReconExperiment.CASH_PER_ACCOUNT,
                        ReconExperiment.POSITION_PER_ACCOUNT);
                core.flush();
            }
            publish();
            LOG.info("恢复完成：{} 条记录，丢弃半行 {} 条", recovery.records().size(), recovery.discardedLines());
        } catch (RuntimeException ex) {
            journal.close();
            throw ex;
        }
        worker = Thread.ofPlatform().daemon().name("trading-engine").start(this::run);
        sampler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("ops-sampler").factory());
        sampler.scheduleAtFixedRate(this::sample, 0, 1, TimeUnit.SECONDS);
    }
    private void onReplay(TradingCore replay, JournalRecord record) {
        switch (record) {
            case JournalRecord.Place place -> {
                updateOrder(replay, place.orderId());
                updateTrades(replay, place.trades());
                var key = key(place.command());
                if (key != null) {
                    var view = replay.order(place.orderId()).orElseThrow();
                    cacheOriginal(new PlaceResult(view, place.trades(), place.reason()));
                    ids.restore(key, place.orderId());
                }
            }
            case JournalRecord.Cancel cancel -> updateOrder(replay, cancel.orderId());
            case JournalRecord.Modify modify -> {
                updateOrder(replay, modify.orderId());
                updateTrades(replay, modify.trades());
            }
            case JournalRecord.Deposit ignored -> {}
        }
    }

    public static List<String> demoAccounts() {
        var clients = new ArrayList<>(List.of("A", "B", "C", "D"));
        clients.addAll(OrderGenerator.clientIds("S", 20));
        return List.copyOf(clients);
    }
    public SubmitResult<PlaceResult> place(NewOrder cmd) {
        long start = System.nanoTime();
        String key = key(cmd);
        var duplicate = ids.reserve(key);
        if (duplicate.isPresent()) {
            try {
                long id = duplicate.get().get(props.requestTimeoutMs(), TimeUnit.MILLISECONDS);
                PlaceResult value;
                synchronized (originalLock) { value = originals.get(id); }
                if (value == null) {
                    var view = orders.get(id);
                    if (view == null) return report(Outcome.QUEUE_FULL, null, "幂等订单状态已过期", true, start);
                    value = new PlaceResult(view, List.of(), null);
                }
                return report(value.accepted() ? Outcome.SUCCESS : Outcome.BUSINESS_REJECTED,
                        value, value.reason(), true, start);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return report(Outcome.TIMEOUT, null, "等待被中断", true, start);
            } catch (TimeoutException ex) {
                return report(Outcome.TIMEOUT, null, "请求超时", true, start);
            } catch (ExecutionException ex) {
                return report(Outcome.QUEUE_FULL, null, "原请求未受理，请重试", true, start);
            }
        }
        try {
            var future = enqueue(() -> core.place(cmd), r -> {
                updateOrder(core, r.order().orderId());
                updateTrades(core, r.trades());
                if (key != null) {
                    var current = orders.getOrDefault(r.order().orderId(), r.order());
                    cacheOriginal(new PlaceResult(current, r.trades(), r.reason()));
                    ids.complete(key, r.order().orderId());
                }
            }, false, true);
            var r = await(future);
            return report(r.accepted() ? Outcome.SUCCESS : Outcome.BUSINESS_REJECTED, r, r.reason(), false, start);
        } catch (Unavailable ex) {
            if (ex.outcome() != Outcome.TIMEOUT) ids.release(key, ex.getMessage());
            return report(ex.outcome(), null, ex.getMessage(), false, start);
        }
    }
    public SubmitResult<CancelResult> cancel(long id, String client) {
        long start = System.nanoTime();
        try {
            var r = await(enqueue(() -> core.cancel(id, client), result -> updateOrder(core, id), true, false));
            return report(r.cancelled() ? Outcome.SUCCESS : Outcome.BUSINESS_REJECTED, r, cancelReason(r.status()), false, start);
        } catch (Unavailable ex) { return report(ex.outcome(), null, ex.getMessage(), false, start); }
    }
    public int clearAccounts(Set<String> clients) {
        // Take a stable list on the writer, then use ordinary cancellation batches so one job
        // cannot create an unbounded durable group.
        var ids = await(enqueue(() -> {
            var open = new ArrayList<>(core.openOrders(Side.BUY));
            open.addAll(core.openOrders(Side.SELL));
            return open.stream().filter(o -> clients.contains(o.clientId())).toList();
        }, ignored -> {}, false, false));
        int count = 0;
        for (var order : ids) {
            var r = cancel(order.orderId(), order.clientId());
            if (r.outcome() == Outcome.QUEUE_FULL || r.outcome() == Outcome.TIMEOUT)
                throw new Unavailable(r.outcome(), r.reason());
            if (r.value() != null && r.value().cancelled()) count++;
        }
        return count;
    }
    private static String cancelReason(CancelStatus status) {
        return switch (status) {
            case CANCELLED -> null;
            case NOT_FOUND -> "订单不存在";
            case ALREADY_FILLED -> "订单已成交";
            case ALREADY_CANCELLED -> "订单已撤销";
        };
    }
    private static String key(NewOrder cmd) {
        return EngineLimits.validId(cmd.clientId()) && EngineLimits.validId(cmd.clientOrderId())
                ? IdempotencyStore.key(cmd.clientId(), cmd.clientOrderId()) : null;
    }
    private void cacheOriginal(PlaceResult r) {
        synchronized (originalLock) {
            originals.put(r.order().orderId(), r);
            if (originals.size() > KEEP) originals.pollFirstEntry();
        }
    }
    private <T> SubmitResult<T> report(Outcome outcome, T value, String reason, boolean duplicate, long start) {
        boolean queued = !duplicate && outcome != Outcome.RATE_LIMITED && outcome != Outcome.QUEUE_FULL;
        metrics.record(outcome, System.nanoTime() - start, queued);
        return new SubmitResult<>(outcome, value, reason, duplicate);
    }

    private static final class Job<T> {
        final long ticket;
        final Supplier<T> action;
        final Consumer<T> afterFlush;
        final CompletableFuture<T> result = new CompletableFuture<>();
        T value;
        Job(long ticket, Supplier<T> action, Consumer<T> afterFlush) {
            this.ticket = ticket; this.action = action; this.afterFlush = afterFlush;
        }
        void execute() { value = action.get(); }
        void publish() { afterFlush.accept(value); }
        void reply() { result.complete(value); }
    }
    private <T> CompletableFuture<T> enqueue(Supplier<T> action, Consumer<T> committed, boolean cancel, boolean place) {
        synchronized (queueLock) {
            if (!running || failed) throw new Unavailable(Outcome.QUEUE_FULL, "交易核心不可用");
            var p = protection;
            if (place && p.rateLimitEnabled() && !bucket.tryAcquire()) throw new Unavailable(Outcome.RATE_LIMITED, "下单过于频繁");
            if (p.queueLimitEnabled() && depthUnderLock() >= p.queueCapacity()) throw new Unavailable(Outcome.QUEUE_FULL, "请求队列已满");
            var job = new Job<T>(++ticket, action, committed);
            (cancel ? cancels : ordinary).addLast(job);
            queueLock.notify();
            return job.result;
        }
    }
    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(props.requestTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new Unavailable(Outcome.TIMEOUT, "请求超时，已受理的命令仍会继续处理");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Unavailable(Outcome.TIMEOUT, "等待被中断");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof Unavailable unavailable) throw unavailable;
            throw new Unavailable(Outcome.QUEUE_FULL, "交易核心不可用");
        }
    }
    private Job<?> next() {
        if (cancels.isEmpty()) return ordinary.pollFirst();
        if (ordinary.isEmpty() || protection.cancelPriorityEnabled() || cancels.peekFirst().ticket < ordinary.peekFirst().ticket)
            return cancels.pollFirst();
        return ordinary.pollFirst();
    }
    private void run() {
        var batch = new ArrayList<Job<?>>(props.groupCommitMaxBatch());
        try {
            while (true) {
                batch.clear();
                synchronized (queueLock) {
                    while (depthUnderLock() == 0 && running) queueLock.wait();
                    if (!running && depthUnderLock() == 0) break;
                    for (int i = 0; i < props.groupCommitMaxBatch() && depthUnderLock() != 0; i++) batch.add(next());
                }
                long before = core.journalSequence();
                for (var job : batch) job.execute();
                core.flush();
                for (var job : batch) job.publish();
                dirty |= core.journalSequence() != before;
                if (dirty && (queueDepth() == 0 || System.nanoTime() - lastPublish >= SNAPSHOT_NS)) publish();
                for (var job : batch) job.reply();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail(batch, ex);
        } catch (RuntimeException ex) {
            fail(batch, ex);
        }
    }
    private void fail(List<Job<?>> batch, Exception ex) {
        LOG.error("交易核心停止，最后发布序列号 {}", snapshot.journalSeq(), ex);
        failed = true;
        var error = new Unavailable(Outcome.QUEUE_FULL, "交易核心停止，批次未确认");
        synchronized (queueLock) {
            for (var job : batch) job.result.completeExceptionally(error);
            for (var job : ordinary) job.result.completeExceptionally(error);
            for (var job : cancels) job.result.completeExceptionally(error);
            ordinary.clear();
            cancels.clear();
        }
    }
    private void updateTrades(TradingCore source, List<Trade> trades) {
        for (var t : trades) {
            updateOrder(source, t.buyOrderId());
            updateOrder(source, t.sellOrderId());
        }
        rememberTrades(trades);
    }
    private void rememberTrades(List<Trade> trades) {
        for (var t : trades) {
            recent.addFirst(t);
            if (recent.size() > 50) recent.removeLast();
        }
    }
    private void updateOrder(TradingCore source, long id) {
        var view = source.order(id).orElse(null);
        if (view == null) return;
        orders.put(id, view);
        // The engine thread maintains both caches. A quiet client ID can outlive the
        // global terminal-order window, so keep its latest committed view with its key.
        if (!originals.isEmpty()) {
            synchronized (originalLock) {
                var previous = originals.get(id);
                if (previous != null && !previous.order().equals(view)) {
                    originals.put(id, new PlaceResult(view, previous.trades(), previous.reason()));
                }
            }
        }
        if (view.status().isTerminal() && terminal.add(id) && terminal.size() > KEEP) {
            long evicted = terminal.getFirst();
            terminal.removeFirst();
            orders.remove(evicted);
        }
    }
    private void publish() {
        var bid = core.bestBid();
        var ask = core.bestAsk();
        snapshot = new Snapshot(core.journalSequence(), bid.isPresent() ? bid.getAsLong() : null,
                ask.isPresent() ? ask.getAsLong() : null, core.depth(Side.BUY, 10), core.depth(Side.SELL, 10),
                List.copyOf(recent), core.accounts(), books.latest(POSTING_WINDOW).reversed(), System.currentTimeMillis());
        dirty = false;
        lastPublish = System.nanoTime();
    }
    private void sample() {
        var result = latest;
        metrics.sampleGauges(queueDepth(), protection.queueCapacity(), result == null ? 0 : result.report().anomalies().size());
        alerts.evaluate(metrics.lastCompletedSeconds(300));
    }
    public Snapshot snapshot() { return snapshot; }
    public Optional<OrderView> order(long id) { return Optional.ofNullable(orders.get(id)); }
    public String symbol() { return props.symbol(); }
    public List<SecondStats> metrics(int seconds) { return metrics.lastCompletedSeconds(seconds); }
    public AlertEvaluator alerts() { return alerts; }
    public boolean failed() { return failed; }
    public Protection protection() { return protection; }
    public int queueDepth() { synchronized (queueLock) { return depthUnderLock(); } }
    private int depthUnderLock() { return ordinary.size() + cancels.size(); }
    public Protection protection(Boolean rateEnabled, Boolean queueEnabled, Boolean cancelPriority,
                                 Integer capacity, Long burst, Long rate) {
        synchronized (queueLock) {
            var p = protection;
            var next = new Protection(rateEnabled == null ? p.rateLimitEnabled() : rateEnabled,
                    queueEnabled == null ? p.queueLimitEnabled() : queueEnabled,
                    cancelPriority == null ? p.cancelPriorityEnabled() : cancelPriority,
                    capacity == null ? p.queueCapacity() : capacity,
                    burst == null ? p.rateLimitBurst() : burst, rate == null ? p.rateLimitPerSecond() : rate);
            if (next.queueCapacity() < 1) throw new IllegalArgumentException("队列容量必须大于 0");
            bucket.setRate(next.rateLimitBurst(), next.rateLimitPerSecond());
            protection = next;
            return next;
        }
    }
    public ReconResult reconcile() {
        return await(enqueue(() -> {
            var report = Reconciler.reconcile(journal.readAll(), books.postings(), core.accounts(), core.journalSequence());
            return new ReconResult(report, InjectionScore.of(injector.manifest(), report));
        }, r -> {
            latest = r;
            alerts.observeImmediate(AlertRule.RECONCILIATION, !r.report().clean(), System.currentTimeMillis() / 1000);
        }, false, false));
    }
    public ReconResult latest() { return latest; }
    public double injectionRate() { return injector.rate(); }
    public double setInjectionRate(double rate) { injector.setRate(rate); return rate; }
    @Override @PreDestroy public void close() {
        sampler.shutdownNow();
        synchronized (queueLock) {
            running = false;
            queueLock.notifyAll();
        }
        try { worker.join(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        finally { journal.close(); }
    }
}
