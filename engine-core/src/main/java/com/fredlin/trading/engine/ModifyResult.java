package com.fredlin.trading.engine;

import java.util.List;

public record ModifyResult(ModifyStatus status, OrderView order, List<Trade> trades, String reason, long sequence) {
    public ModifyResult { trades = List.copyOf(trades); }
    public boolean modified() { return status == ModifyStatus.MODIFIED; }
}
