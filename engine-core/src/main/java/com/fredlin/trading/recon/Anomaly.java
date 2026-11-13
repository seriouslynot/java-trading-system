package com.fredlin.trading.recon;

import com.fredlin.trading.ledger.Asset;

public record Anomaly(AnomalyType type, String reference, long journalSeq, String accountId, Asset asset,
                      Long expected, Long actual, String description) {}
