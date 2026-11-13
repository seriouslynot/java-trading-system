package com.fredlin.trading.core;

import com.fredlin.trading.journal.JournalRecord;
import java.util.List;

public record RecoveryResult(TradingCore core, List<JournalRecord> records, int discardedLines) {
    public RecoveryResult { records = List.copyOf(records); }
}
