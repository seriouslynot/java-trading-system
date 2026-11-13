package com.fredlin.trading.app.engine;

import com.fredlin.trading.engine.*;
import com.fredlin.trading.ledger.*;
import java.util.List;

public record Snapshot(long journalSeq, Long bestBid, Long bestAsk, List<DepthLevel> bids,
                       List<DepthLevel> asks, List<Trade> recentTrades, List<AccountView> accounts,
                       List<Posting> recentPostings, long publishedAtMillis) {
    public Snapshot {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
        recentTrades = List.copyOf(recentTrades);
        accounts = List.copyOf(accounts);
        recentPostings = List.copyOf(recentPostings);
    }
}
