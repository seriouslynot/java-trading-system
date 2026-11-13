package com.fredlin.trading.app.api;

import com.fredlin.trading.app.engine.EngineService;
import com.fredlin.trading.recon.ReconExperiment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recon")
public class ReconController {
    private final EngineService engine;
    public ReconController(EngineService engine) { this.engine = engine; }
    @PostMapping("/run") public Object run() { return engine.reconcile(); }
    @GetMapping("/latest") public ResponseEntity<?> latest() {
        var result = engine.latest();
        return result == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }
    @GetMapping("/injection") public Dtos.Injection rate() { return new Dtos.Injection(engine.injectionRate()); }
    @PostMapping("/injection") public Dtos.Injection rate(@RequestBody Dtos.Injection body) {
        if (body.rate() == null) throw new IllegalArgumentException("rate 必填");
        return new Dtos.Injection(engine.setInjectionRate(body.rate()));
    }
    @PostMapping("/experiment") public Object experiment(@RequestBody Dtos.Experiment body) {
        if (body.orders() == null || body.rate() == null || body.seed() == null) throw new IllegalArgumentException("orders、rate 和 seed 必填");
        return ReconExperiment.run(body.orders(), body.rate(), body.seed());
    }
}
