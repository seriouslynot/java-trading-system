package com.fredlin.trading.bench;

import java.util.Arrays;

final class BenchSupport {
    private BenchSupport() {}

    static double median(double[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    static long percentile(long[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.ceil(p * sorted.length) - 1];
    }

    static String environment() {
        return "JDK " + System.getProperty("java.runtime.version")
                + ", " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + ", " + System.getProperty("os.arch") + ", " + Runtime.getRuntime().availableProcessors()
                + " logical processors.";
    }
}
