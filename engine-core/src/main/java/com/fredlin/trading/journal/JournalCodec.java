package com.fredlin.trading.journal;

import com.fredlin.trading.engine.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import java.util.stream.Collectors;

public final class JournalCodec {
    private JournalCodec() {}

    public static String encode(JournalRecord r) {
        String body = switch (r) {
            case JournalRecord.Deposit d -> row("|", d.seq(), "D", esc(d.accountId()), d.cash(), d.position());
            case JournalRecord.Place p -> {
                var c = p.command();
                yield row("|", p.seq(), "P", esc(c.clientId()), esc(c.clientOrderId()), esc(c.symbol()),
                        c.side(), c.price(), c.quantity(), p.orderId(), p.status(), esc(p.reason()), trades(p.trades()),
                        c.timeInForce());
            }
            case JournalRecord.Cancel c -> row("|", c.seq(), "C", c.orderId(), esc(c.clientId()), c.status());
            case JournalRecord.Modify m -> row("|", m.seq(), "M", m.orderId(), esc(m.clientId()), m.newPrice(),
                    m.newQuantity(), m.status(), trades(m.trades()));
        };
        return body + '|' + crc(body);
    }
    public static JournalRecord decode(String line) {
        int split = line.lastIndexOf('|');
        if (split < 0) throw new JournalCorruptedException("日志缺少 CRC32");
        String body = line.substring(0, split);
        if (!crc(body).equals(line.substring(split + 1))) throw new JournalCorruptedException("日志 CRC32 不匹配");
        String[] f = body.split("\\|", -1);
        try {
            long seq = num(f[0]);
            return switch (f[1]) {
                case "D" -> {
                    require(f, 5, 5);
                    yield new JournalRecord.Deposit(seq, unesc(f[2]), num(f[3]), num(f[4]));
                }
                case "P" -> {
                    require(f, 12, 13);
                    var cmd = new NewOrder(unesc(f[2]), unesc(f[4]),
                            f[5].isEmpty() ? null : Side.valueOf(f[5]), num(f[6]), num(f[7]), unesc(f[3]),
                            f.length == 12 ? TimeInForce.GTC : TimeInForce.valueOf(f[12]));
                    yield new JournalRecord.Place(seq, cmd, num(f[8]), OrderStatus.valueOf(f[9]), unesc(f[10]), parseTrades(f[11]));
                }
                case "C" -> {
                    require(f, 5, 5);
                    yield new JournalRecord.Cancel(seq, num(f[2]), unesc(f[3]), CancelStatus.valueOf(f[4]));
                }
                case "M" -> {
                    require(f, 8, 8);
                    yield new JournalRecord.Modify(seq, num(f[2]), unesc(f[3]), num(f[4]), num(f[5]),
                            ModifyStatus.valueOf(f[6]), parseTrades(f[7]));
                }
                default -> throw new IllegalArgumentException("未知记录类型");
            };
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            throw new JournalCorruptedException("日志字段不合法", ex);
        }
    }
    private static void require(String[] f, int min, int max) {
        if (f.length < min || f.length > max) throw new IllegalArgumentException("日志字段数量不符");
    }
    private static String row(String separator, Object... values) {
        var out = new StringJoiner(separator);
        for (Object value : values) out.add(value == null ? "" : value.toString());
        return out.toString();
    }
    private static String trades(List<Trade> trades) {
        return trades.stream().map(t -> row(":", t.tradeId(), t.sequence(), esc(t.symbol()), t.price(), t.quantity(),
                t.buyOrderId(), esc(t.buyClientId()), t.sellOrderId(), esc(t.sellClientId()), t.takerSide()))
                .collect(Collectors.joining(";"));
    }
    private static List<Trade> parseTrades(String value) {
        if (value.isEmpty()) return List.of();
        var result = new ArrayList<Trade>();
        for (String text : value.split(";", -1)) {
            String[] f = text.split(":", -1);
            require(f, 10, 10);
            result.add(new Trade(num(f[0]), num(f[1]), unesc(f[2]), num(f[3]), num(f[4]), num(f[5]),
                    unesc(f[6]), num(f[7]), unesc(f[8]), Side.valueOf(f[9])));
        }
        return result;
    }
    private static long num(String s) { return Long.parseLong(s); }
    private static final HexFormat HEX = HexFormat.of();
    private static String crc(String s) {
        var crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return HEX.toHexDigits((int) crc.getValue());
    }
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("%", "%25").replace("|", "%7C").replace(":", "%3A")
                .replace(";", "%3B").replace("\n", "%0A").replace("\r", "%0D");
    }
    static String unesc(String s) {
        if (s.isEmpty()) return null;
        return s.replace("%7C", "|").replace("%3A", ":").replace("%3B", ";")
                .replace("%0A", "\n").replace("%0D", "\r").replace("%25", "%");
    }
}
