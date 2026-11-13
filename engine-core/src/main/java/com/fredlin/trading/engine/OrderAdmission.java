package com.fredlin.trading.engine;

import java.util.Optional;

@FunctionalInterface
public interface OrderAdmission {
    OrderAdmission ALLOW_ALL = order -> Optional.empty();
    Optional<String> admit(OrderView order);
    default Optional<String> amend(OrderView current, long newPrice, long newRemaining) { return Optional.empty(); }
}
