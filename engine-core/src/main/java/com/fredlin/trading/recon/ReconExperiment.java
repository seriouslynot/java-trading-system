package com.fredlin.trading.recon;

import com.fredlin.trading.core.TradingCore;
import com.fredlin.trading.journal.InMemoryJournal;
import com.fredlin.trading.ledger.Books;
import com.fredlin.trading.sim.MarketWorkload;
import com.fredlin.trading.sim.MixedOrderWorkload;
import com.fredlin.trading.sim.OrderGenerator;

public final class ReconExperiment {
    public record Result(int commands, int trades, double rate, long seed, ReconReport report, InjectionScore score) {}
    public static final long CASH_PER_ACCOUNT = 100_000_000L;
    public static final long POSITION_PER_ACCOUNT = 100_000L;
    private ReconExperiment() {}
    public static Result run(int commands, double rate, long seed) {
        if (commands < 1 || commands > 100_000) throw new IllegalArgumentException("实验命令数须在 1 到 100000 之间");
        var books = new Books();
        var injector = new AnomalyInjector(books, rate, seed ^ 0x5a5a5a5aL);
        var journal = new InMemoryJournal();
        var core = new TradingCore("DEMO", journal, injector);
        var clients = OrderGenerator.clientIds("S", 20);
        for (var client : clients) core.deposit(client, CASH_PER_ACCOUNT, POSITION_PER_ACCOUNT);
        var generator = OrderGenerator.standard(core.symbol(), clients);
        int trades = 0;
        for (var command : MixedOrderWorkload.generate(generator, commands, seed)) {
            switch (command) {
                case MarketWorkload.Place p -> trades += core.place(p.order()).trades().size();
                case MarketWorkload.Cancel c -> core.cancel(c.orderId(), c.clientId());
                case MarketWorkload.Modify m -> throw new IllegalStateException("Unexpected amendment in experiment");
            }
        }
        var report = Reconciler.reconcile(journal.readAll(), books.postings(), core.accounts(), core.journalSequence());
        return new Result(commands, trades, rate, seed, report, InjectionScore.of(injector.manifest(), report));
    }
}
