package com.fredlin.trading.ledger;

import com.fredlin.trading.engine.Trade;
import java.util.List;

public final class PostingRules {
    public static final String EXTERNAL = "EXTERNAL";
    private PostingRules() {}
    public static List<Posting> forTrade(Trade t, long seq) {
        var ref = Posting.tradeReference(t.tradeId());
        return List.of(new Posting(ref, seq, t.buyClientId(), Asset.CASH, -t.amount()),
                new Posting(ref, seq, t.sellClientId(), Asset.CASH, t.amount()),
                new Posting(ref, seq, t.buyClientId(), Asset.POSITION, t.quantity()),
                new Posting(ref, seq, t.sellClientId(), Asset.POSITION, -t.quantity()));
    }
    public static List<Posting> forDeposit(String id, long cash, long position, long seq) {
        var ref = Posting.depositReference(seq);
        return List.of(new Posting(ref, seq, id, Asset.CASH, cash),
                new Posting(ref, seq, EXTERNAL, Asset.CASH, -cash),
                new Posting(ref, seq, id, Asset.POSITION, position),
                new Posting(ref, seq, EXTERNAL, Asset.POSITION, -position));
    }
}
