# Trading engine core

The core module owns the order book, ledger, and journal replay. It uses only the JDK. The application gives each book a single writer and calls the core from its engine thread.

The Java sources live under `src/main/java/com/fredlin/trading`. The path after `src/main/java` follows the Java package name. Use the links below to open the code directly.

| Area | Start here |
| --- | --- |
| Matching | [MatchingEngine.java](src/main/java/com/fredlin/trading/engine/MatchingEngine.java) · [OrderBook.java](src/main/java/com/fredlin/trading/engine/OrderBook.java) |
| Command execution and replay | [TradingCore.java](src/main/java/com/fredlin/trading/core/TradingCore.java) · [FileJournal.java](src/main/java/com/fredlin/trading/journal/FileJournal.java) |
| Reservations and reconciliation | [Ledger.java](src/main/java/com/fredlin/trading/ledger/Ledger.java) · [Reconciler.java](src/main/java/com/fredlin/trading/recon/Reconciler.java) |
| Supporting packages | [Operational controls](src/main/java/com/fredlin/trading/ops) · [Workload generators](src/main/java/com/fredlin/trading/sim) |
| Verification | [Matching scenarios](src/test/java/com/fredlin/trading/engine/PromptMatchingScenariosTest.java) · [Ledger scenarios](src/test/java/com/fredlin/trading/core/PromptLedgerScenariosTest.java) · [Journal tests](src/test/java/com/fredlin/trading/journal/JournalTest.java) |
| Benchmarks | [Benchmark sources](src/bench/java/com/fredlin/trading/bench) · [Latest reports](../docs/benchmarks/current) |
