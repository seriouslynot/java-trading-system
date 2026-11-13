package com.fredlin.trading.ledger;

@FunctionalInterface
public interface PostingSink { void accept(Posting posting); }
