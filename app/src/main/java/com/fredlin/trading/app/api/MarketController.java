package com.fredlin.trading.app.api;

import com.fredlin.trading.app.engine.EngineService;
import com.fredlin.trading.engine.EngineLimits;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MarketController {
    private final EngineService engine;
    public MarketController(EngineService engine) { this.engine = engine; }
    @GetMapping("/book") public Object book() { return engine.snapshot(); }
    @GetMapping("/accounts") public Object accounts() { return engine.snapshot().accounts(); }
    @GetMapping("/postings") public Object postings(@RequestParam(defaultValue = "100") int limit) {
        if (limit < 0) throw new IllegalArgumentException("条数不能为负");
        var recent = engine.snapshot().recentPostings();
        return recent.subList(0, Math.min(limit, recent.size()));
    }
    @GetMapping("/info") public Object info() {
        return Map.of("symbol", engine.symbol(), "priceScale", 2, "cashScale", 2,
                "maxPrice", EngineLimits.MAX_PRICE, "maxQuantity", EngineLimits.MAX_QUANTITY);
    }
}
