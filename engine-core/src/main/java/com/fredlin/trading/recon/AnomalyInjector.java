package com.fredlin.trading.recon;

import com.fredlin.trading.ledger.*;
import java.util.*;

public final class AnomalyInjector implements PostingSink {
    public record Injection(String reference, AnomalyType type) {}
    private static final AnomalyType[] TYPES = {AnomalyType.DUPLICATE, AnomalyType.MISSING,
            AnomalyType.AMOUNT_MISMATCH, AnomalyType.SIDE_MISMATCH};
    private final PostingSink target;
    private final Random random;
    private final List<Injection> manifest = new ArrayList<>();
    private String lastTrade;
    private AnomalyType activeFault;
    private volatile double rate;

    public AnomalyInjector(PostingSink target, double rate, long seed) {
        this.target = Objects.requireNonNull(target);
        random = new Random(seed);
        setRate(rate);
    }
    public void accept(Posting p) {
        if (!p.isTrade()) {
            target.accept(p);
            return;
        }
        if (!p.reference().equals(lastTrade)) {
            lastTrade = p.reference();
            double probability = rate;
            activeFault = probability > 0 && random.nextDouble() < probability
                    ? TYPES[random.nextInt(TYPES.length)] : null;
            if (activeFault != null) manifest.add(new Injection(p.reference(), activeFault));
        }
        if (activeFault == null) {
            target.accept(p);
            return;
        }
        switch (activeFault) {
            case DUPLICATE -> { target.accept(p); target.accept(p); }
            case MISSING -> {}
            case AMOUNT_MISMATCH -> target.accept(p.withAmount(p.amount() + Long.signum(p.amount())));
            case SIDE_MISMATCH -> target.accept(p.withAmount(-p.amount()));
            default -> throw new IllegalStateException();
        }
    }
    public void setRate(double rate) {
        if (!Double.isFinite(rate) || rate < 0 || rate > 1) throw new IllegalArgumentException("注入比例须在 0 到 1 之间");
        this.rate = rate;
    }
    public double rate() { return rate; }
    public List<Injection> manifest() { return List.copyOf(manifest); }
}
