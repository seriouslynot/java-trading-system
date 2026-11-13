package com.fredlin.trading.ledger;

import com.fredlin.trading.engine.*;
import java.util.*;

public final class Ledger implements OrderAdmission {
    private final PostingSink sink;
    private final Map<String, Account> accounts = new TreeMap<>();
    private static final class Account {
        long cash, frozenCash, position, frozenPosition;
        AccountView view(String id) { return new AccountView(id, cash, frozenCash, position, frozenPosition); }
    }

    public Ledger(PostingSink sink) { this.sink = Objects.requireNonNull(sink); }

    public AccountView deposit(String id, long cash, long position, long seq) {
        if (!EngineLimits.validId(id) || PostingRules.EXTERNAL.equals(id)) throw new IllegalArgumentException("账户 ID 不合法");
        if (cash < 0 || position < 0) throw new IllegalArgumentException("入金和持仓不能为负");
        var a = accounts.get(id);
        if (a == null) a = new Account();
        Math.addExact(Math.addExact(a.cash, a.frozenCash), cash);
        Math.addExact(Math.addExact(a.position, a.frozenPosition), position);
        a.cash += cash;
        a.position += position;
        accounts.putIfAbsent(id, a);
        PostingRules.forDeposit(id, cash, position, seq).forEach(sink::accept);
        return a.view(id);
    }
    @Override public Optional<String> admit(OrderView order) {
        var a = accounts.get(order.clientId());
        if (a == null) return Optional.of("账户不存在");
        return adjust(a, order.side(), required(order.side(), order.price(), order.remainingQuantity()));
    }
    @Override public Optional<String> amend(OrderView current, long newPrice, long newRemaining) {
        long change = required(current.side(), newPrice, newRemaining)
                - required(current.side(), current.price(), current.remainingQuantity());
        return adjust(accounts.get(current.clientId()), current.side(), change);
    }
    private static long required(Side side, long price, long n) { return side == Side.BUY ? price * n : n; }
    private static Optional<String> adjust(Account a, Side side, long change) {
        if (side == Side.BUY) {
            if (change > a.cash) return Optional.of("可用资金不足");
            if (a.frozenCash + change < 0) throw new IllegalStateException("资金冻结不足");
            a.cash -= change;
            a.frozenCash += change;
        } else {
            if (change > a.position) return Optional.of("可用持仓不足");
            if (a.frozenPosition + change < 0) throw new IllegalStateException("持仓冻结不足");
            a.position -= change;
            a.frozenPosition += change;
        }
        return Optional.empty();
    }
    public void settle(Trade t, long buyLimitPrice, long seq) {
        var buy = accounts.get(t.buyClientId());
        var sell = accounts.get(t.sellClientId());
        long reserved = Math.multiplyExact(buyLimitPrice, t.quantity());
        if (reserved < t.amount() || reserved > buy.frozenCash || t.quantity() > sell.frozenPosition)
            throw new IllegalStateException("成交与冻结不匹配");
        Math.addExact(Math.addExact(buy.position, buy.frozenPosition), t.quantity());
        Math.addExact(Math.addExact(sell.cash, sell.frozenCash), t.amount());
        buy.frozenCash -= reserved;
        buy.cash += reserved - t.amount();
        buy.position += t.quantity();
        sell.frozenPosition -= t.quantity();
        sell.cash += t.amount();
        PostingRules.forTrade(t, seq).forEach(sink::accept);
    }
    public void release(OrderView order, long cancelledQuantity) {
        if (cancelledQuantity == 0) return;
        adjust(accounts.get(order.clientId()), order.side(), -required(order.side(), order.price(), cancelledQuantity));
    }
    public Optional<AccountView> account(String id) {
        var a = accounts.get(id);
        return a == null ? Optional.empty() : Optional.of(a.view(id));
    }
    public List<AccountView> accounts() {
        return accounts.entrySet().stream().map(e -> e.getValue().view(e.getKey())).toList();
    }
}
