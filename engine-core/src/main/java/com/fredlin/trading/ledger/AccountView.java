package com.fredlin.trading.ledger;

public record AccountView(String accountId, long cashAvailable, long cashFrozen,
                          long positionAvailable, long positionFrozen) {
    public long cashTotal() { return Math.addExact(cashAvailable, cashFrozen); }
    public long positionTotal() { return Math.addExact(positionAvailable, positionFrozen); }
}
