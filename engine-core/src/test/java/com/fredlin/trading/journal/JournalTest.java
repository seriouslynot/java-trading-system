package com.fredlin.trading.journal;

import com.fredlin.trading.engine.*;
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
    @Test void keepsCrLfRecordsAndAppendsLfRecords() throws Exception {
        var path = tmp.resolve("windows.log");
        var first = new JournalRecord.Deposit(1, "A", 12, 3);
        var next = new JournalRecord.Deposit(2, "B", 15, 4);
        Files.writeString(path, JournalCodec.encode(first) + "\r\n");
        try (var j = new FileJournal(path, FileJournal.FsyncPolicy.GROUP)) {
            assertEquals(0, j.discardedLines());
            assertEquals(List.of(first), j.readAll());
            j.append(next);
            j.flush();
        }
        try (var j = new FileJournal(path, FileJournal.FsyncPolicy.GROUP)) {
            assertEquals(0, j.discardedLines());
            assertEquals(List.of(first, next), j.readAll());
        }
    }

    @Test void eachRecordRoundTripsAndRejectsTampering() {
        var order = new NewOrder("A", "DEMO", Side.BUY, 100, 5, "client-1", TimeInForce.IOC);
        var trade = new Trade(1, 2, "DEMO", 95, 3, 2, "A", 1, "B", Side.BUY);
        List<JournalRecord> records = List.of(
                new JournalRecord.Deposit(1, "A", 1000, 0),
                new JournalRecord.Place(2, order, 2, OrderStatus.CANCELLED, "IOC", List.of(trade)),
                new JournalRecord.Cancel(3, 3, "B", CancelStatus.CANCELLED),
                new JournalRecord.Modify(4, 3, "B", 99, 6, ModifyStatus.REJECTED, List.of()));
        for (var record : records) {
            String encoded = JournalCodec.encode(record);
            assertEquals(record, JournalCodec.decode(encoded));
            assertThrows(JournalCorruptedException.class, () -> JournalCodec.decode("9" + encoded.substring(1)));
        }
    }
}
