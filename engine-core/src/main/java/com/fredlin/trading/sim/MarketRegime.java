package com.fredlin.trading.sim;

public enum MarketRegime {
    STATIC("平静", 0.00, 0.00, Shape.FLAT),
    NORMAL("正常", 0.15, 0.02, Shape.SWING),
    LARGE_SWING("大幅摆动", 0.50, 0.25, Shape.SWING),
    FLASH_CRASH_40("闪崩 40%", 0.50, 0.40, Shape.CRASH),
    FLASH_CRASH_60("闪崩 60%", 0.50, 0.60, Shape.CRASH);

    private enum Shape { FLAT, SWING, CRASH }
    private final String label;
    private final double volatility, amplitude;
    private final Shape shape;

    MarketRegime(String label, double volatility, double amplitude, Shape shape) {
        this.label = label;
        this.volatility = volatility;
        this.amplitude = amplitude;
        this.shape = shape;
    }
    public String label() { return label; }
    public double annualVolatility() { return volatility; }
    public double trend(double u) {
        return switch (shape) {
            case FLAT -> 1;
            case SWING -> 1 + amplitude * Math.sin(2 * Math.PI * u);
            case CRASH -> {
                if (u <= 0.30 || u >= 0.60) yield 1;
                if (u <= 0.32) yield 1 - amplitude * (u - 0.30) / 0.02;
                if (u <= 0.35) yield 1 - amplitude;
                yield 1 - amplitude * (0.60 - u) / 0.25;
            }
        };
    }
}
