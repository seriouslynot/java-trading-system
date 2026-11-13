package com.fredlin.trading.app.api;

import com.fredlin.trading.app.engine.EngineService;
import com.fredlin.trading.engine.NewOrder;
import com.fredlin.trading.ops.Outcome;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final EngineService engine;
    public OrderController(EngineService engine) { this.engine = engine; }

    @PostMapping
    public ResponseEntity<Dtos.PlaceResponse> place(@RequestBody Dtos.PlaceRequest req) {
        if (req.clientId() == null || req.side() == null || req.price() == null || req.quantity() == null)
            throw new IllegalArgumentException("clientId、side、price 和 quantity 必填");
        var r = engine.place(new NewOrder(req.clientId(), engine.symbol(), req.side(), req.price(), req.quantity(), req.clientOrderId()));
        var p = r.value();
        return ResponseEntity.status(status(r.outcome())).body(new Dtos.PlaceResponse(r.outcome(),
                p == null ? null : p.order(), p == null ? List.of() : p.trades(), r.reason(), r.duplicate()));
    }
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Dtos.CancelResponse> cancel(@PathVariable long orderId, @RequestParam String clientId) {
        var r = engine.cancel(orderId, clientId);
        var c = r.value();
        return ResponseEntity.status(status(r.outcome())).body(new Dtos.CancelResponse(r.outcome(),
                c == null ? null : c.status(), c == null ? null : c.order(), r.reason()));
    }
    @GetMapping("/{orderId}")
    public ResponseEntity<?> order(@PathVariable long orderId) { return ResponseEntity.of(engine.order(orderId)); }

    static HttpStatus status(Outcome o) {
        return switch (o) {
            case SUCCESS, BUSINESS_REJECTED -> HttpStatus.OK;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case QUEUE_FULL -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
        };
    }
}
