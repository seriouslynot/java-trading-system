package com.fredlin.trading.journal;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryJournal implements Journal {
    private final List<String> lines = new ArrayList<>();
    @Override public void append(JournalRecord record) { lines.add(JournalCodec.encode(record)); }
    @Override public List<JournalRecord> readAll() { return lines.stream().map(JournalCodec::decode).toList(); }
    public void replaceLine(int index, String line) { lines.set(index, line); }
    public List<String> lines() { return List.copyOf(lines); }
}
