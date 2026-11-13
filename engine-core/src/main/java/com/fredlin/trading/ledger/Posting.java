package com.fredlin.trading.ledger;

public record Posting(String reference, long sequence, String accountId, Asset asset, long amount) {
    public static String tradeReference(long tradeId) { return "T" + tradeId; }
    public static String depositReference(long sequence) { return "D" + sequence; }
    public boolean isTrade() { return reference.startsWith("T"); }
    public Posting withAmount(long newAmount) { return new Posting(reference, sequence, accountId, asset, newAmount); }
}
