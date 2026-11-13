package com.fredlin.trading.ledger;

import java.util.*;

public final class Books implements PostingSink {
    private final List<Posting> postings = new ArrayList<>();
    @Override public void accept(Posting p) { postings.add(Objects.requireNonNull(p)); }
    public List<Posting> postings() { return List.copyOf(postings); }
    public List<Posting> latest(int n) {
        if (n < 0) throw new IllegalArgumentException("条数不能为负");
        return List.copyOf(postings.subList(Math.max(0, postings.size() - n), postings.size()));
    }
    public int size() { return postings.size(); }
}
