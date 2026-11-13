package com.fredlin.trading.journal;

public class JournalCorruptedException extends RuntimeException {
    public JournalCorruptedException(String message) { super(message); }
    public JournalCorruptedException(String message, Throwable cause) { super(message, cause); }
}
