package com.fredlin.trading.engine;

import java.util.regex.Pattern;

public final class EngineLimits {
    public static final long MAX_PRICE = 100_000_000L;
    public static final long MAX_QUANTITY = 1_000_000L;
    public static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    public static final int FINISHED_ORDERS_TO_KEEP = 100_000;
    private EngineLimits() {}
    public static boolean validId(String id) { return id != null && ID_PATTERN.matcher(id).matches(); }
}
