package com.fredlin.trading.engine;

import java.util.*;

/** Single writer. Price priority comes from the book; time priority comes from each level. */
public final class MatchingEngine {
    private final String symbol;
    private final OrderAdmission admission;
    private final OrderBook book = new OrderBook();
    private final LinkedHashMap<Long, OrderView> finished = new LinkedHashMap<>();
    private long orderId, sequence, tradeId;

    public MatchingEngine(String symbol) { this(symbol, OrderAdmission.ALLOW_ALL); }
    public MatchingEngine(String symbol, OrderAdmission admission) {
        this.symbol = Objects.requireNonNull(symbol);
        this.admission = Objects.requireNonNull(admission);
    }
    public PlaceResult place(NewOrder c) {
        Objects.requireNonNull(c);
        long id = ++orderId;
        long seq = ++sequence;
        String reason = validateValues(c.price(), c.quantity());
        if (reason == null) {
            if (!symbol.equals(c.symbol())) reason = "交易对不匹配";
            else if (!EngineLimits.validId(c.clientId())) reason = "客户 ID 不合法";
            else if (c.clientOrderId() != null && !EngineLimits.validId(c.clientOrderId())) reason = "客户端订单号不合法";
            else if (c.side() == null) reason = "订单方向不能为空";
        }
        if (reason != null) return reject(id, c, seq, reason);
        var order = new Order(id, c, seq);
        var rejection = admission.admit(order.view());
        if (rejection.isPresent()) return reject(id, c, seq, rejection.get());
        List<Trade> trades = new ArrayList<>();
        reason = match(order, trades);
        return new PlaceResult(order.view(), trades, reason);
    }
    public CancelResult cancel(long id, String client) {
        long seq = ++sequence;
        var order = book.get(id);
        if (order == null || !order.command.clientId().equals(client)) {
            var known = ownedFinished(id, client);
            return new CancelResult(known == null ? CancelStatus.NOT_FOUND
                    : known.status() == OrderStatus.FILLED ? CancelStatus.ALREADY_FILLED
                    : CancelStatus.ALREADY_CANCELLED, known, seq);
        }
        book.remove(order);
        order.cancel();
        remember(order);
        return new CancelResult(CancelStatus.CANCELLED, order.view(), seq);
    }
    public ModifyResult modify(long id, String client, long newPrice, long newQuantity) {
        long seq = ++sequence;
        var order = book.get(id);
        if (order == null || !order.command.clientId().equals(client)) {
            var known = ownedFinished(id, client);
            return new ModifyResult(known == null ? ModifyStatus.NOT_FOUND
                    : known.status() == OrderStatus.FILLED ? ModifyStatus.ALREADY_FILLED
                    : ModifyStatus.ALREADY_CANCELLED, known, List.of(), null, seq);
        }
        String reason = validateValues(newPrice, newQuantity);
        if (reason != null) return new ModifyResult(ModifyStatus.REJECTED, order.view(), List.of(), reason, seq);
        if (newQuantity <= order.filled) {
            book.remove(order);
            order.cancel();
            remember(order);
            return new ModifyResult(ModifyStatus.MODIFIED, order.view(), List.of(), "新数量不大于已成交数量", seq);
        }
        if (newPrice == order.price && newQuantity == order.quantity) {
            return new ModifyResult(ModifyStatus.MODIFIED, order.view(), List.of(), null, seq);
        }
        var rejection = admission.amend(order.view(), newPrice, newQuantity - order.filled);
        if (rejection.isPresent()) {
            return new ModifyResult(ModifyStatus.REJECTED, order.view(), List.of(), rejection.get(), seq);
        }
        if (newPrice == order.price && newQuantity < order.quantity) {
            book.reduce(order, order.quantity - newQuantity);
            order.reduceTo(newQuantity);
            return new ModifyResult(ModifyStatus.MODIFIED, order.view(), List.of(), null, seq);
        }
        book.remove(order);
        order.replace(newPrice, newQuantity, seq);
        List<Trade> trades = new ArrayList<>();
        reason = match(order, trades);
        return new ModifyResult(ModifyStatus.MODIFIED, order.view(), trades, reason, seq);
    }
    private String match(Order taker, List<Trade> trades) {
        var side = taker.command.side();
        String reason = null;
        while (taker.remaining > 0) {
            var maker = book.best(side.opposite());
            if (maker == null || (side == Side.BUY ? taker.price < maker.price : taker.price > maker.price)) break;
            if (taker.command.clientId().equals(maker.command.clientId())) {
                reason = "自成交防范";
                break;
            }
            long n = Math.min(taker.remaining, maker.remaining);
            var buy = side == Side.BUY ? taker : maker;
            var sell = side == Side.SELL ? taker : maker;
            trades.add(new Trade(++tradeId, taker.sequence, symbol, maker.price, n,
                    buy.id, buy.command.clientId(), sell.id, sell.command.clientId(), side));
            boolean done = n == maker.remaining;
            if (done) book.remove(maker);
            else book.reduce(maker, n);
            maker.fill(n);
            taker.fill(n);
            if (done) remember(maker);
        }
        if (taker.remaining > 0 && (reason != null || taker.command.timeInForce() == TimeInForce.IOC)) {
            if (reason == null) reason = "IOC 剩余撤销";
            taker.cancel();
        }
        if (taker.status.isTerminal()) remember(taker);
        else book.add(taker);
        return reason;
    }
    private static String validateValues(long price, long quantity) {
        if (price <= 0) return "价格必须大于 0";
        if (price > EngineLimits.MAX_PRICE) return "价格超过上限";
        if (quantity <= 0) return "数量必须大于 0";
        if (quantity > EngineLimits.MAX_QUANTITY) return "数量超过上限";
        return null;
    }
    private PlaceResult reject(long id, NewOrder command, long seq, String reason) {
        var view = OrderView.rejected(id, command, seq);
        remember(view);
        return new PlaceResult(view, List.of(), reason);
    }
    private void remember(Order order) { remember(order.view()); }
    private void remember(OrderView view) {
        finished.put(view.orderId(), view);
        if (finished.size() > EngineLimits.FINISHED_ORDERS_TO_KEEP) finished.pollFirstEntry();
    }
    private OrderView ownedFinished(long id, String client) {
        var known = finished.get(id);
        return known != null && known.status() != OrderStatus.REJECTED && known.clientId().equals(client) ? known : null;
    }
    public OptionalLong bestBid() { return best(Side.BUY); }
    public OptionalLong bestAsk() { return best(Side.SELL); }
    private OptionalLong best(Side side) {
        var order = book.best(side);
        return order == null ? OptionalLong.empty() : OptionalLong.of(order.price);
    }
    public List<DepthLevel> depth(Side side, int levels) {
        if (levels <= 0) throw new IllegalArgumentException("档数必须大于 0");
        return book.levels(Objects.requireNonNull(side)).entrySet().stream().limit(levels)
                .map(e -> new DepthLevel(e.getKey(), e.getValue().total, e.getValue().queue.size())).toList();
    }
    public Optional<OrderView> order(long id) {
        var open = book.get(id);
        return Optional.ofNullable(open == null ? finished.get(id) : open.view());
    }
    public List<OrderView> openOrders(Side side) {
        return book.levels(Objects.requireNonNull(side)).values().stream()
                .flatMap(level -> level.queue.values().stream()).map(Order::view).toList();
    }
    public String symbol() { return symbol; }
    public long lastSequence() { return sequence; }
}
