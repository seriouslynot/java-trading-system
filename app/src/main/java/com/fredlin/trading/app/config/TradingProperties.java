package com.fredlin.trading.app.config;

import com.fredlin.trading.journal.FileJournal.FsyncPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.file.Path;

@ConfigurationProperties("trading")
public record TradingProperties(String symbol, Path journalPath, FsyncPolicy journalFsync,
                                int groupCommitMaxBatch, int queueCapacity, long requestTimeoutMs,
                                long rateLimitBurst, long rateLimitPerSecond, String adminToken,
                                boolean seedDemoAccounts) {
    public TradingProperties {
        if (symbol == null || symbol.isBlank() || journalPath == null || journalFsync == null
                || groupCommitMaxBatch < 1 || queueCapacity < 1 || requestTimeoutMs < 1
                || rateLimitBurst < 1 || rateLimitPerSecond < 1)
            throw new IllegalArgumentException("trading 配置不合法");
        if (adminToken == null) adminToken = "";
    }
}
