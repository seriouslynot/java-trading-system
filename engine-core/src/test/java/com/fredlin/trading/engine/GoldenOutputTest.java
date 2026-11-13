package com.fredlin.trading.engine;

import com.fredlin.trading.sim.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Local baseline. Original generator and serialization source were not supplied. */
class GoldenOutputTest {
    private static final List<String> STANDARD = List.of("52c7db41874e909a088cde7d278ba8a8fb61cb63ee6150a091ff864cc7ab8d4c", "ac95e90e3322e26db7c9c27c535b92ee5f90dc915e5b15aed69c052490ffee46", "882e2869ac09b37510266ee12947c6a97289ce301b5093a9c2319dff986dc561");
    private static final List<String> REGIMES = List.of("05ca345c396f66a8ae4aa00661efef1377c02de917e9e4c857d3f80b26f19ca8", "10b033b95cf1ae674ed77e54bccf0884295f5150c3358baf96f162fca2777cb6", "8004040837c0dae835a63315accb03715b850a7f124425eb17639a8cf06be2f2", "90e0bf78b175213aa18c295e77408b96beb8f7c1f298785aebb85226c5955f50", "b69f2fe8144e148cb5fb7b127431b0bd2db760eade17574c75c3fddd55bed6c7");

    @ParameterizedTest @ValueSource(longs = {1, 2, 3})
    void standardRegression(long seed) {
        assertEquals(STANDARD.get((int)seed - 1), standard(seed));
    }
    @ParameterizedTest @EnumSource(MarketRegime.class)
    void regimeRegression(MarketRegime r) {
        assertEquals(REGIMES.get(r.ordinal()), regime(r));
    }
    static String standard(long seed) {
        var random = new Random(seed);
        var engine = new MatchingEngine("DEMO");
        var generator = OrderGenerator.standard("DEMO", OrderGenerator.clientIds("C", 20));
        var orders = new ArrayList<NewOrder>();
        var digest = new OutputDigest();
        for (int i = 1; i <= 30_000; i++) {
            if (random.nextInt(10) < 8 || orders.isEmpty()) {
                var order = generator.next(random);
                orders.add(order);
                digest.place(engine.place(order));
            } else {
                int selected = random.nextInt(orders.size());
                var owner = random.nextInt(10) == 0 ? "C99" : orders.get(selected).clientId();
                digest.cancel(engine.cancel(selected + 1L, owner));
            }
            if (i % 100 == 0) digest.book(engine);
        }
        return digest.finish();
    }
    static String regime(MarketRegime regime) {
        var engine = new MatchingEngine("DEMO");
        var digest = new OutputDigest();
        int step = 0;
        for (var c : MarketWorkload.generate(regime, MarketWorkload.Parameters.standard(10_000), 1)) {
            switch (c) {
                case MarketWorkload.Place p -> digest.place(engine.place(p.order()));
                case MarketWorkload.Cancel x -> digest.cancel(engine.cancel(x.orderId(), x.clientId()));
                case MarketWorkload.Modify x -> digest.modify(engine.modify(x.orderId(), x.clientId(), x.newPrice(), x.newQuantity()));
            }
            if (++step % 100 == 0) digest.book(engine);
        }
        return digest.finish();
    }
}
