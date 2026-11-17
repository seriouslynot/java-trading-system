package com.fredlin.trading.journal;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongConsumer;

public final class FileJournal implements Journal {
    public enum FsyncPolicy { GROUP, EVERY_RECORD, OS_DEFAULT }
    private final Path path;
    private final FsyncPolicy policy;
    private final LongConsumer forceNanos;
    private final FileChannel channel;
    private final Pending pending = new Pending();
    private final int discarded;

    public FileJournal(Path path, FsyncPolicy fsync) {
        this(path, fsync, null);
    }

    /** Observes successful command forces. Tail repair is excluded. */
    public FileJournal(Path path, FsyncPolicy fsync, LongConsumer forceNanos) {
        this.forceNanos = forceNanos;
        this.path = path.toAbsolutePath();
        this.policy = Objects.requireNonNull(fsync);
        try {
            Files.createDirectories(this.path.getParent());
            channel = FileChannel.open(this.path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            try {
                discarded = repairTail();
                channel.position(channel.size());
            } catch (IOException | RuntimeException ex) {
                try { channel.close(); } catch (IOException closeError) { ex.addSuppressed(closeError); }
                throw ex;
            }
        } catch (IOException ex) { throw new UncheckedIOException("无法打开日志", ex); }
    }
    private int repairTail() throws IOException {
        try (var in = new BufferedInputStream(Files.newInputStream(path), 64 * 1024)) {
            var line = new ByteArrayOutputStream();
            long offset = 0, lineNumber = 0;
            int current = in.read();
            while (current != -1) {
                if (current == '\n') {
                    long nextOffset = offset + line.size() + 1;
                    lineNumber++;
                    current = in.read();
                    try {
                        String record = line.toString(StandardCharsets.UTF_8);
                        if (record.endsWith("\r")) record = record.substring(0, record.length() - 1);
                        JournalCodec.decode(record);
                    } catch (JournalCorruptedException ex) {
                        if (current != -1) throw new JournalCorruptedException("日志第 " + lineNumber + " 行损坏", ex);
                        return truncate(offset);
                    }
                    offset = nextOffset;
                    line.reset();
                } else {
                    line.write(current);
                    current = in.read();
                }
            }
            return line.size() == 0 ? 0 : truncate(offset);
        }
    }
    private int truncate(long size) throws IOException {
        channel.truncate(size);
        channel.force(true);
        return 1;
    }
    @Override public void append(JournalRecord record) {
        if (!channel.isOpen()) throw new IllegalStateException("日志已关闭");
        byte[] data = (JournalCodec.encode(record) + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            if (policy == FsyncPolicy.GROUP) pending.writeBytes(data);
            else {
                write(ByteBuffer.wrap(data));
                if (policy == FsyncPolicy.EVERY_RECORD) force();
            }
        } catch (IOException ex) { throw new UncheckedIOException("日志追加失败", ex); }
    }
    @Override public void flush() {
        if (!channel.isOpen()) throw new IllegalStateException("日志已关闭");
        if (policy != FsyncPolicy.GROUP || pending.size() == 0) return;
        try {
            write(pending.buffer());
            force();
            pending.reset();
        } catch (IOException ex) { throw new UncheckedIOException("日志落盘失败", ex); }
    }
    private void force() throws IOException {
        if (forceNanos == null) {
            channel.force(false);
            return;
        }
        long started = System.nanoTime();
        channel.force(false);
        forceNanos.accept(System.nanoTime() - started);
    }
    private void write(ByteBuffer data) throws IOException {
        while (data.hasRemaining()) channel.write(data);
    }
    @Override public List<JournalRecord> readAll() {
        var result = new ArrayList<JournalRecord>();
        try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (String line; (line = in.readLine()) != null;) result.add(JournalCodec.decode(line));
            if (pending.size() > 0) pending.toString(StandardCharsets.UTF_8).lines()
                    .map(JournalCodec::decode).forEach(result::add);
            return List.copyOf(result);
        } catch (IOException ex) { throw new UncheckedIOException("日志读取失败", ex); }
    }
    @Override public int discardedLines() { return discarded; }
    @Override public void close() {
        pending.reset(); // Never turn an uncommitted group into a committed one during close.
        try { channel.close(); }
        catch (IOException ex) { throw new UncheckedIOException("日志关闭失败", ex); }
    }
    private static final class Pending extends ByteArrayOutputStream {
        Pending() { super(64 * 1024); }
        ByteBuffer buffer() { return ByteBuffer.wrap(buf, 0, count); }
    }
}
