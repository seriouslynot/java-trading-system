package com.fredlin.trading.app.engine;

import com.fredlin.trading.app.config.TradingProperties;
import com.fredlin.trading.core.TradingCore;
import com.fredlin.trading.engine.*;
import com.fredlin.trading.journal.FileJournal;
import com.fredlin.trading.journal.FileJournal.FsyncPolicy;
import com.fredlin.trading.ops.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class EngineServicePromptTest {
    @TempDir Path temporary;

    private TradingProperties properties() {
        return new TradingProperties("DEMO", temporary.resolve("journal.log"), FsyncPolicy.GROUP,
                256, 2000, 5000, 500, 1000, "", true);
    }

    @Test void duplicateUsesCommittedStatusAfterAnotherOrderFillsIt() {
        var order = new NewOrder("A", "DEMO", Side.SELL, 100, 3, "first");
        var props = properties();
        long id;
        try (var engine = new EngineService(props)) {
            var placed = engine.place(order);
            id = placed.value().order().orderId();
            assertEquals(OrderStatus.NEW, placed.value().order().status());
            assertEquals(Outcome.SUCCESS, engine.place(NewOrder.limit("B", "DEMO", Side.BUY, 100, 3)).outcome());
            assertFilledDuplicate(engine, order, id);
        }
        try (var engine = new EngineService(props)) {
            assertFilledDuplicate(engine, order, id);
        }
    }

    private static void assertFilledDuplicate(EngineService engine, NewOrder order, long id) {
        long seq = engine.snapshot().journalSeq();
        var duplicate = engine.place(order);
        assertTrue(duplicate.duplicate());
        assertEquals(OrderStatus.FILLED, duplicate.value().order().status());
        assertEquals(id, duplicate.value().order().orderId());
        assertEquals(engine.order(id).orElseThrow(), duplicate.value().order());
        assertEquals(seq, engine.snapshot().journalSeq());
    }

    @Test void rejectedOrderCanBeQueriedAndReplayedWithZeroCancelledQuantity() {
        var command = new NewOrder("A", "DEMO", Side.BUY, 0, 7, "invalid");
        var props = properties();
        PlaceResult first;
        try (var engine = new EngineService(props)) {
            var rejected = engine.place(command);
            assertEquals(Outcome.BUSINESS_REJECTED, rejected.outcome());
            first = rejected.value();
            long id = first.order().orderId();
            assertEquals(first.order(), engine.order(id).orElseThrow());
            assertEquals(CancelStatus.NOT_FOUND, engine.cancel(id, "A").value().status());
        }
        try (var engine = new EngineService(props)) {
            var duplicate = engine.place(command);
            assertTrue(duplicate.duplicate());
            assertEquals(Outcome.BUSINESS_REJECTED, duplicate.outcome());
            assertEquals(first, duplicate.value());
            assertEquals(0, duplicate.value().order().cancelledQuantity());
        }
    }
    @Test void simultaneousSubmissionsAppendOnlyOneOrder() throws Exception {
        var order = new NewOrder("A", "DEMO", Side.SELL, 100, 3, "concurrent");
        try (var engine = new EngineService(properties());
             var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            long before = engine.snapshot().journalSeq();
            var gate = new CountDownLatch(1);
            var jobs = new ArrayList<java.util.concurrent.Future<SubmitResult<PlaceResult>>>();
            Callable<SubmitResult<PlaceResult>> request = () -> {
                gate.await();
                return engine.place(order);
            };
            for (int i = 0; i < 16; i++) jobs.add(pool.submit(request));
            gate.countDown();
            int originals = 0;
            long id = jobs.getFirst().get().value().order().orderId();
            for (var job : jobs) {
                var result = job.get();
                assertEquals(id, result.value().order().orderId());
                if (!result.duplicate()) originals++;
            }
            assertEquals(1, originals);
            assertEquals(before + 1, engine.snapshot().journalSeq());
        }
    }
    @Test void retainedKeyKeepsFinalStateAfterOrderCacheEvictionAndRestart() {
        var command = new NewOrder("A", "DEMO", Side.SELL, 100, 3, "retained");
        var props = properties();
        long id;
        try (var journal = new FileJournal(props.journalPath(), FsyncPolicy.GROUP)) {
            var core = new TradingCore("DEMO", journal, posting -> {});
            core.deposit("A", 0, 3);
            core.deposit("B", 300, 0);
            id = core.place(command).order().orderId();
            core.place(NewOrder.limit("B", "DEMO", Side.BUY, 100, 3));
            var invalid = NewOrder.limit("B", "DEMO", Side.BUY, 0, 1);
            for (int i = 0; i < EngineLimits.FINISHED_ORDERS_TO_KEEP; i++) core.place(invalid);
            core.flush();
        }
        try (var engine = new EngineService(props)) {
            assertTrue(engine.order(id).isEmpty());
            var duplicate = engine.place(command);
            assertTrue(duplicate.duplicate());
            assertEquals(Outcome.SUCCESS, duplicate.outcome());
            assertEquals(id, duplicate.value().order().orderId());
            assertEquals(OrderStatus.FILLED, duplicate.value().order().status());
            assertEquals(3, duplicate.value().order().filledQuantity());
        }
    }
}
