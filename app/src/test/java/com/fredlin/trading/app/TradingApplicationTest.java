package com.fredlin.trading.app;

import com.fredlin.trading.app.engine.EngineService;
import com.fredlin.trading.journal.JournalCodec;
import com.fredlin.trading.journal.JournalRecord;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradingApplicationTest {
    private static final Path JOURNAL;
    static {
        try { JOURNAL = Files.createTempFile("trading-http-", ".log"); }
        catch (java.io.IOException ex) { throw new ExceptionInInitializerError(ex); }
        JOURNAL.toFile().deleteOnExit();
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("trading.journal-path", JOURNAL::toString);
        registry.add("trading.admin-token", () -> "local-test");
        registry.add("trading.request-timeout-ms", () -> "5000");
    }
    @LocalServerPort int port;
    @Autowired EngineService engine;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private record Result(int status, JsonNode json) {}
    private Result req(String method, String path, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(10))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) b.header("Content-Type", "application/json");
        if (path.startsWith("/api/recon/") || path.startsWith("/api/ops/")) b.header("X-Admin-Token", "local-test");
        var r = client.send(b.build(), HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        return new Result(r.statusCode(), r.body().isEmpty() ? null : json.readTree(r.body()));
    }
    private Result place(String client, String side, long price, long qty, String key) throws Exception {
        return req("POST", "/api/orders", """
                {"clientId":"%s","side":"%s","price":%d,"quantity":%d,"clientOrderId":"%s"}
                """.formatted(client, side, price, qty, key));
    }
    @Test void a01Health() throws Exception {
        assertEquals("UP", req("GET", "/actuator/health", null).json().path("status").asString());
    }
    @Test void a02CrossAndPublishedBook() throws Exception {
        long id = place("A", "SELL", 40_000, 5, "cross-ask").json().path("order").path("orderId").asLong();
        var trade = place("B", "BUY", 40_000, 3, "cross-bid").json().path("trades").get(0);
        assertEquals(3, trade.path("quantity").asLong());
        assertEquals(40_000, trade.path("price").asLong());
        assertEquals(id, trade.path("sellOrderId").asLong());
        assertTrue(req("GET", "/api/book", null).json().path("recentTrades").isArray());
        assertEquals(2, req("GET", "/api/orders/" + id, null).json().path("remainingQuantity").asLong());
    }
    @Test void a03BusinessVersusFormatFailure() throws Exception {
        var r = place("A", "BUY", 0, 1, "bad-price");
        assertEquals(200, r.status());
        assertEquals("BUSINESS_REJECTED", r.json().path("outcome").asString());
        assertEquals("价格必须大于 0", r.json().path("reason").asString());
        assertEquals(400, req("POST", "/api/orders", "{").status());
    }
    @Test void a04Idempotent() throws Exception {
        var a = place("C", "BUY", 401, 1, "same-key").json();
        var b = place("C", "BUY", 402, 1, "same-key").json();
        assertTrue(b.path("duplicate").asBoolean());
        assertEquals(a.path("order"), b.path("order"));
    }
    @Test void a05ConcurrentAndReconciled() throws Exception {
        long cash = engine.snapshot().accounts().stream().mapToLong(a -> a.cashTotal()).sum();
        long position = engine.snapshot().accounts().stream().mapToLong(a -> a.positionTotal()).sum();
        try (var exec = Executors.newFixedThreadPool(16)) {
            var tasks = IntStream.range(0, 80).<Callable<Result>>mapToObj(i ->
                    () -> place(i % 2 == 0 ? "S10" : "S11", i % 2 == 0 ? "SELL" : "BUY", 9000, 1, "con-" + i)).toList();
            for (var f : exec.invokeAll(tasks)) assertEquals("SUCCESS", f.get().json().path("outcome").asString());
        }
        var r = req("POST", "/api/recon/run", null).json().path("report");
        assertEquals(0, r.path("anomalies").size());
        assertEquals(cash, engine.snapshot().accounts().stream().mapToLong(a -> a.cashTotal()).sum());
        assertEquals(position, engine.snapshot().accounts().stream().mapToLong(a -> a.positionTotal()).sum());
    }
    @Test void a06CancelOwnership() throws Exception {
        long id = place("A", "BUY", 77, 5, "owned").json().path("order").path("orderId").asLong();
        assertEquals("NOT_FOUND", req("DELETE", "/api/orders/" + id + "?clientId=B", null).json().path("status").asString());
        assertEquals("CANCELLED", req("DELETE", "/api/orders/" + id + "?clientId=A", null).json().path("status").asString());
    }
    @Test void a07InvalidDirection() throws Exception {
        assertEquals(400, place("A", "WRONG", 100, 1, "bad-direction").status());
        assertEquals(400, req("POST", "/api/orders", """
                {"clientId":"A","side":0,"price":100,"quantity":1}
                """).status());
        assertEquals(400, req("POST", "/api/orders", """
                {"clientId":"A","side":"BUY","price":100.5,"quantity":1}
                """).status());
    }
    @Test void a08ClearSurgeAccounts() throws Exception {
        place("S20", "SELL", 123_456, 12, "clear-s");
        place("S20", "BUY", 3, 10, "clear-b");
        assertEquals(2, engine.clearAccounts(Set.of("S20")));
        var a = engine.snapshot().accounts().stream().filter(v -> v.accountId().equals("S20")).findFirst().orElseThrow();
        assertEquals(0, a.cashFrozen());
        assertEquals(0, a.positionFrozen());
        assertTrue(engine.reconcile().report().clean());
    }
    @Test void a09ConcurrentResponseHasWrittenRecord() throws Exception {
        try (var exec = Executors.newFixedThreadPool(16)) {
            var tasks = IntStream.range(0, 16).<Callable<Void>>mapToObj(i -> () -> {
                var r = place("D", "BUY", 15, 1, "dur-" + i);
                assertEquals("SUCCESS", r.json().path("outcome").asString());
                long id = r.json().path("order").path("orderId").asLong();
                // Ignore any concurrent partial tail after the acknowledged record.
                try (var lines = Files.lines(JOURNAL)) {
                    assertTrue(lines.filter(line -> line.contains("|dur-" + i + "|"))
                            .map(JournalCodec::decode).anyMatch(v -> v instanceof JournalRecord.Place p && p.orderId() == id));
                }
                return null;
            }).toList();
            for (var f : exec.invokeAll(tasks)) f.get();
        }
    }
    @Test void adminTokenRequired() throws Exception {
        var r = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/recon/run"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, r.statusCode());
    }
}
