package com.fredlin.trading.journal;

import com.fredlin.trading.engine.*;
import java.util.List;

public sealed interface JournalRecord {
    long seq();
    record Deposit(long seq, String accountId, long cash, long position) implements JournalRecord {}
    record Place(long seq, NewOrder command, long orderId, OrderStatus status, String reason,
                 List<Trade> trades) implements JournalRecord {
        public Place { trades = List.copyOf(trades); }
    }
    record Cancel(long seq, long orderId, String clientId, CancelStatus status) implements JournalRecord {}
    record Modify(long seq, long orderId, String clientId, long newPrice, long newQuantity,
                  ModifyStatus status, List<Trade> trades) implements JournalRecord {
        public Modify { trades = List.copyOf(trades); }
    }
}
