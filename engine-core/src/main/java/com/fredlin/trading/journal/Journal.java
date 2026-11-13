package com.fredlin.trading.journal;

import java.util.List;

public interface Journal extends AutoCloseable {
    void append(JournalRecord record);
    default void flush() {}
    List<JournalRecord> readAll();
    default int discardedLines() { return 0; }
    @Override default void close() {}
}
