package com.fredlin.trading.recon;

public enum AnomalyType {
    DUPLICATE("重复记账"), MISSING("缺失记账"), AMOUNT_MISMATCH("金额不符"),
    SIDE_MISMATCH("方向错误"), UNEXPECTED("来源不明"), BALANCE_MISMATCH("余额不符");

    private final String label;
    AnomalyType(String label) { this.label = label; }
    public String label() { return label; }
}
