package com.fredlin.trading.core;

import com.fredlin.trading.engine.*;
import com.fredlin.trading.journal.*;
import com.fredlin.trading.ledger.*;
import java.util.*;
import java.util.function.BiConsumer;

public final class TradingCore {
    private final Journal journal;
    private final Ledger ledger;
    private final MatchingEngine engine;
    private long seq;
    private boolean failed;

    public TradingCore(String symbol, Journal journal, PostingSink sink) {
        this.journal = Objects.requireNonNull(journal);
        ledger = new Ledger(sink);
        engine = new MatchingEngine(symbol, ledger);
    }
    public static RecoveryResult recover(String symbol, Journal journal, PostingSink sink) {
        return recover(symbol, journal, sink, (core, record) -> {});
    }

    /** Calls the observer after each record has been replayed and checked. */
    public static RecoveryResult recover(String symbol, Journal journal, PostingSink sink,
                                         BiConsumer<TradingCore, JournalRecord> replayed) {
        var core = new TradingCore(symbol, journal, sink);
        var records = journal.readAll();
        for (var record : records) {
            if (record.seq() != core.seq + 1) throw new JournalCorruptedException("日志序列号不连续：" + record.seq());
            core.seq++;
            try {
                boolean matches = switch (record) {
                    case JournalRecord.Deposit d -> {
                        core.ledger.deposit(d.accountId(), d.cash(), d.position(), core.seq);
                        yield true;
                    }
                    case JournalRecord.Place p -> {
                        var r = core.applyPlace(p.command());
                        yield r.order().orderId() == p.orderId() && r.order().status() == p.status()
                                && (r.reason() == null) == (p.reason() == null) && r.trades().equals(p.trades());
                    }
                    case JournalRecord.Cancel c -> core.applyCancel(c.orderId(), c.clientId()).status() == c.status();
                    case JournalRecord.Modify m -> {
                        var r = core.applyModify(m.orderId(), m.clientId(), m.newPrice(), m.newQuantity());
                        yield r.status() == m.status() && r.trades().equals(m.trades());
                    }
                };
                if (!matches) throw new IllegalStateException("重放结果和日志不一致");
                replayed.accept(core, record);
            } catch (RuntimeException ex) {
                throw new JournalCorruptedException("日志第 " + record.seq() + " 条重放失败", ex);
            }
        }
        return new RecoveryResult(core, records, journal.discardedLines());
    }
    public AccountView deposit(String id, long cash, long position) {
        ensureHealthy();
        if (!EngineLimits.validId(id) || PostingRules.EXTERNAL.equals(id) || cash < 0 || position < 0)
            throw new IllegalArgumentException("入金参数不合法");
        var existing = ledger.account(id);
        existing.ifPresent(a -> {
            Math.addExact(a.cashTotal(), cash);
            Math.addExact(a.positionTotal(), position);
        });
        return change(() -> {
            var view = ledger.deposit(id, cash, position, seq);
            journal.append(new JournalRecord.Deposit(seq, id, cash, position));
            return view;
        });
    }
    public PlaceResult place(NewOrder command) {
        Objects.requireNonNull(command);
        // The legacy wire format uses an empty field for an omitted client order ID.
        final NewOrder c = "".equals(command.clientOrderId())
                ? new NewOrder(command.clientId(), command.symbol(), command.side(), command.price(),
                    command.quantity(), null, command.timeInForce()) : command;
        return change(() -> {
            var r = applyPlace(c);
            journal.append(new JournalRecord.Place(seq, c, r.order().orderId(), r.order().status(), r.reason(), r.trades()));
            return r;
        });
    }
    public CancelResult cancel(long id, String client) {
        return change(() -> {
            var r = applyCancel(id, client);
            journal.append(new JournalRecord.Cancel(seq, id, client, r.status()));
            return r;
        });
    }
    public ModifyResult modify(long id, String client, long price, long quantity) {
        return change(() -> {
            var r = applyModify(id, client, price, quantity);
            journal.append(new JournalRecord.Modify(seq, id, client, price, quantity, r.status(), r.trades()));
            return r;
        });
    }
    private PlaceResult applyPlace(NewOrder c) {
        var r = engine.place(c);
        if (r.accepted()) settleAndRelease(r.order(), r.trades());
        return r;
    }
    private CancelResult applyCancel(long id, String client) {
        var r = engine.cancel(id, client);
        if (r.cancelled()) ledger.release(r.order(), r.order().cancelledQuantity());
        return r;
    }
    private ModifyResult applyModify(long id, String client, long price, long quantity) {
        var r = engine.modify(id, client, price, quantity);
        if (r.modified()) settleAndRelease(r.order(), r.trades());
        return r;
    }
    private void settleAndRelease(OrderView o, List<Trade> trades) {
        for (var t : trades) {
            // A maker buy always trades at its own limit; the taker buy may get a better price.
            ledger.settle(t, t.takerSide() == Side.BUY ? o.price() : t.price(), seq);
        }
        ledger.release(o, o.cancelledQuantity());
    }
    private <T> T change(java.util.function.Supplier<T> action) {
        ensureHealthy();
        seq++;
        try { return action.get(); }
        catch (RuntimeException ex) { failed = true; throw ex; }
    }
    public void flush() {
        ensureHealthy();
        try { journal.flush(); }
        catch (RuntimeException ex) { failed = true; throw ex; }
    }
    private void ensureHealthy() { if (failed) throw new IllegalStateException("交易核心已停止"); }
    public OptionalLong bestBid() { return engine.bestBid(); }
    public OptionalLong bestAsk() { return engine.bestAsk(); }
    public List<DepthLevel> depth(Side side, int n) { return engine.depth(side, n); }
    public Optional<OrderView> order(long id) { return engine.order(id); }
    public List<OrderView> openOrders(Side side) { return engine.openOrders(side); }
    public Optional<AccountView> account(String id) { return ledger.account(id); }
    public List<AccountView> accounts() { return ledger.accounts(); }
    public long journalSequence() { return seq; }
    public Journal journal() { return journal; }
    public String symbol() { return engine.symbol(); }
    public boolean isFailed() { return failed; }
}
