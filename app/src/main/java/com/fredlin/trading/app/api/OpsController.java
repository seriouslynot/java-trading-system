package com.fredlin.trading.app.api;

import com.fredlin.trading.app.engine.*;
import com.fredlin.trading.ops.AlertEvaluator;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/ops")
public class OpsController {
    private final EngineService engine;
    private final SurgeGenerator surge;
    public OpsController(EngineService engine, SurgeGenerator surge) { this.engine = engine; this.surge = surge; }
    @GetMapping("/metrics") public Object metrics(@RequestParam(defaultValue = "60") int seconds) { return engine.metrics(seconds); }
    @GetMapping("/status") public Object status() {
        return Map.of("protection", engine.protection(), "queueDepth", engine.queueDepth(),
                "surge", surge.status(), "coreFailed", engine.failed());
    }
    @PostMapping("/protection") public Object protection(@RequestBody Dtos.Protection body) {
        return engine.protection(body.rateLimitEnabled(), body.queueLimitEnabled(), body.cancelPriorityEnabled(),
                body.queueCapacity(), body.rateLimitBurst(), body.rateLimitPerSecond());
    }
    @PostMapping("/surge") public Object start(@RequestBody Dtos.Surge body) {
        if (body.ratePerSecond() == null || body.seconds() == null) throw new IllegalArgumentException("ratePerSecond 和 seconds 必填");
        return surge.start(body.ratePerSecond(), body.seconds());
    }
    @PostMapping("/surge/stop") public Object stop() { return surge.stop(); }
    private record RuleView(String name, String description, int forSeconds) {}
    private record Alerts(Map<String, AlertEvaluator.State> states, List<AlertEvaluator.AlertEvent> events, List<RuleView> rules) {}
    @GetMapping("/alerts") public Object alerts() {
        var alerts = engine.alerts();
        return new Alerts(alerts.states(), alerts.events(),
                alerts.rules().stream().map(r -> new RuleView(r.name(), r.description(), r.forSeconds())).toList());
    }
}
