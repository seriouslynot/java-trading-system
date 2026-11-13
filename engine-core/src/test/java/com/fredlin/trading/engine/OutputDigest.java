package com.fredlin.trading.engine;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Canonical UTF-8 output used by this implementation's regression baseline. */
final class OutputDigest {
    private final MessageDigest hash;
    OutputDigest() {
        try { hash = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    void place(PlaceResult r) {
        line("P", r.reason() != null);
        order(r.order());
        r.trades().forEach(this::trade);
    }
    void cancel(CancelResult r) {
        line("C", r.status(), r.sequence());
        order(r.order());
    }
    void modify(ModifyResult r) {
        line("M", r.status(), r.sequence(), r.reason() != null);
        order(r.order());
        r.trades().forEach(this::trade);
    }
    void book(MatchingEngine engine) {
        line("B", engine.bestBid().orElse(0), engine.bestAsk().orElse(0));
        for (var side : Side.values()) for (var d : engine.depth(side, 5))
            line("D", side, d.price(), d.totalQuantity(), d.orderCount());
    }
    private void order(OrderView o) {
        if (o == null) line("O");
        else line("O", o.orderId(), o.status(), o.filledQuantity(), o.remainingQuantity(), o.cancelledQuantity(),
                o.price(), o.sequence());
    }
    private void trade(Trade t) {
        line("T", t.tradeId(), t.sequence(), t.symbol(), t.price(), t.quantity(), t.buyOrderId(), t.buyClientId(),
                t.sellOrderId(), t.sellClientId(), t.takerSide());
    }
    private void line(Object... fields) {
        var row = new StringBuilder();
        for (var field : fields) {
            if (!row.isEmpty()) row.append('|');
            row.append(field);
        }
        hash.update(row.append('\n').toString().getBytes(StandardCharsets.UTF_8));
    }
    String finish() { return HexFormat.of().formatHex(hash.digest()); }
}
