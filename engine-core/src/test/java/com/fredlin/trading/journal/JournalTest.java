package com.fredlin.trading.journal;

import com.fredlin.trading.engine.TimeInForce;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JournalTest {
    @TempDir Path tmp;
    @Test void legacyCrcAndEscaping() {
        var legacy = (JournalRecord.Place) JournalCodec.decode(
                "32|P|S09||DEMO|SELL|10029|92|8|FILLED||2:8:DEMO:10035:12:1:S04:8:S09:SELL;3:8:DEMO:10033:80:6:S14:8:S09:SELL|ac288f16");
        assertEquals(TimeInForce.GTC, legacy.command().timeInForce());
        assertEquals(2, legacy.trades().size());
        var d = new JournalRecord.Deposit(1, "A%|:;\r\n中", 2, 3);
        var encoded = JournalCodec.encode(d);
        assertEquals(d, JournalCodec.decode(encoded));
        assertThrows(JournalCorruptedException.class, () -> JournalCodec.decode(encoded.replace("中", "损")));
    }
    @Test void pendingVisibleButOnlyCommittedRecordsSurviveClose() throws Exception {
        var path = tmp.resolve("journal.log");
        var first = new JournalRecord.Deposit(1, "A", 1, 1);
        var second = new JournalRecord.Deposit(2, "A", 2, 2);
        try (var j = new FileJournal(path, FileJournal.FsyncPolicy.GROUP)) {
            j.append(first);
            assertEquals(0, Files.size(path));
            j.flush();
            j.append(second);
            assertEquals(List.of(first, second), j.readAll());
        }
        Files.writeString(path, "unfinished", StandardOpenOption.APPEND);
        try (var j = new FileJournal(path, FileJournal.FsyncPolicy.GROUP)) {
            assertEquals(1, j.discardedLines());
            assertEquals(List.of(first), j.readAll());
            j.append(second);
            j.flush();
            assertEquals(List.of(first, second), j.readAll());
        }
    }
    @Test void middleCorruptionFailsWithoutTruncation() throws Exception {
        var path = tmp.resolve("journal.log");
        String content = "bad\n" + JournalCodec.encode(new JournalRecord.Deposit(2, "A", 2, 2)) + "\n";
        Files.writeString(path, content);
        assertThrows(JournalCorruptedException.class, () -> new FileJournal(path, FileJournal.FsyncPolicy.GROUP));
        assertEquals(content, Files.readString(path));
    }
    @Test void expandsGroupBufferAndDropsBadCompleteTail() throws Exception {
        var path = tmp.resolve("large.log");
        var expected = new java.util.ArrayList<JournalRecord>();
        try (var j = new FileJournal(path, FileJournal.FsyncPolicy.GROUP)) {
            for (int i = 1; i <= 6000; i++) {
                var record = new JournalRecord.Deposit(i, "ABCDEFGHIJKLMNO", 100, 200);
                j.append(record);
                expected.add(record);
            }
            j.flush();
        }
        assertTrue(Files.size(path) > 64 * 1024);
        Files.writeString(path, "bad-complete-row\n", StandardOpenOption.APPEND);
        try (var j = new FileJournal(path, FileJournal.FsyncPolicy.GROUP)) {
            assertEquals(1, j.discardedLines());
            assertEquals(expected, j.readAll());
        }
    }
}
