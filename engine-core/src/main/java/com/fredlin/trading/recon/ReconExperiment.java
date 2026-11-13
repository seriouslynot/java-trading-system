package com.fredlin.trading.recon;

import com.fredlin.trading.core.TradingCore;
import com.fredlin.trading.journal.InMemoryJournal;
import com.fredlin.trading.ledger.Books;
import com.fredlin.trading.sim.OrderGenerator;
import java.util.Random;

public final class ReconExperiment {
    public record Result(int orders, int trades, double rate, long seed, ReconReport report, InjectionScore score) {}
    public static final long CASH_PER_ACCOUNT = 100_000_000L;
    public static final long POSITION_PER_ACCOUNT = 100_000L;
    private ReconExperiment() {}
    public static Result run(int orders, double rate, long seed) {
        if (orders < 1 || orders > 100_000) throw new IllegalArgumentException("实验订单数须在 1 到 100000 之间");
        var books = new Books();
        var injector = new AnomalyInjector(books, rate, seed ^ 0x5a5a5a5aL);
        var journal = new InMemoryJournal();
        var core = new TradingCore("DEMO", journal, injector);
        var clients = OrderGenerator.clientIds("S", 20);
        for (var client : clients) core.deposit(client, CASH_PER_ACCOUNT, POSITION_PER_ACCOUNT);
        var generator = OrderGenerator.standard(core.symbol(), clients);
        var random = new Random(seed);
        int trades = 0;
        for (int i = 0; i < orders; i++) trades += core.place(generator.next(random)).trades().size();
        var report = Reconciler.reconcile(journal.readAll(), books.postings(), core.accounts(), core.journalSequence());
        return new Result(orders, trades, rate, seed, report, InjectionScore.of(injector.manifest(), report));
    }
}
