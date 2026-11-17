package com.fredlin.trading.sim;

import com.fredlin.trading.engine.EngineLimits;
import java.util.Random;

final class MidPricePath {
    private final Random random;
    private final MarketRegime regime;
    private final long start;
    private final int steps;
    private final double drift, diffusion;
    private double logFactor;

    MidPricePath(MarketRegime regime, long start, int steps, long seed) {
        random = new Random(seed);
        this.regime = regime;
        this.start = start;
        this.steps = steps;
        double sigma = regime.annualVolatility();
        double dt = 1.0 / (252 * (double) steps);
        drift = -0.5 * sigma * sigma * dt;
        diffusion = sigma * Math.sqrt(dt);
    }
    long next(int step) {
        logFactor += drift + diffusion * random.nextGaussian();
        return Math.clamp(Math.round(start * regime.trend(step / (double) steps) * Math.exp(logFactor)),
                1, EngineLimits.MAX_PRICE);
    }
}
