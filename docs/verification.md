# Verification report

November 17, 2026. Apple M1 MacBook Air with 8 GB RAM, macOS 15.7.5, Temurin
21.0.2, Gradle 9.9.1, Node.js 25.9.0, and npm 11.12.1.

## Functional verification

- `./gradlew clean check` passed **140 tests**. The core module ran 126 and the app ran 14. No failures, errors, or skipped tests. The core runtime classpath has no dependencies.
- The named scenarios cover matching and cancellation, IOC and amendment, and ledger reservations, settlement and postings. Fixed-seed invariant runs execute at least 10,000 steps. Determinism is checked by comparing individual results and the final book from two engines.
- Journal tests cover the four record types, CRC validation, legacy place records, LF and CRLF, tail repair, and damaged interior records. Integration tests check that requests are not acknowledged early and that a duplicate reads the current order after execution and after restart. A retained idempotency key also keeps the current order state when the separate terminal-order lookup cache evicts it.
- The 5,000-command reconciliation experiment uses an approximately 80/20 place/cancel stream. Each selected trade affects four postings. Detection is scored once per event and fault type. With seed 1, all 19 injected events were detected, with zero misses and zero false positives. They produced 76 posting differences. [Smoke and experiment output](benchmarks/current/smoke.json)
- The frontend passed `npm ci`, `npm run build`, and `npm run lint`. A Boot JAR built with the frontend returned `UP` from `/actuator/health` and HTTP 200 from the homepage.
- The process-crash drill checks acknowledged orders, their current order state on retry, and ledger balances after restart. [Current crash drill output](benchmarks/current/crash-recovery.json)

## Performance measurements

| Measurement | What is timed | Results |
| --- | --- | --- |
| Matching | Standalone matching against five generated market regimes with separate throughput and latency passes | [Regime report](benchmarks/current/regime.md) |
| Journal | Matching, ledger and one fixed place/cancel prefix. Long GROUP runs use a separate table section | [Journal report](benchmarks/current/journal.md) |
| Pipeline | 512 closed-loop virtual-thread workers call `EngineService` directly with place/cancel traffic | [Pipeline report](benchmarks/current/pipeline.md) |
| External HTTP | The k6 workload is available for this tier. It has not been run | [k6 script](../scripts/loadtest/orders.js) |

The [baseline rerun](benchmarks/baseline) and [November 13 portfolio report](portfolio/benchmark.md)
use different workloads. In the current journal benchmark, the matched group uses
the same inputs for all policies. `OS_DEFAULT` relies on operating-system buffering
and has a different durability contract from the forcing policies. Pipeline rates
exclude HTTP and include both successful orders and business decisions such as
cancelling an order that already filled.

Posting generation and reconciliation share `PostingRules`. Reconciliation can
detect differences between the journal-derived postings and the books, but a bug
common to both uses may cancel out. Separate tests assert the expected signed
postings for a known trade and deposit.

## Known limits

- The engine is single-instance and single-symbol. It has no leader election, fencing, journal replication, or cross-region recovery. The process drill uses the same host and disk.
- Startup replays the whole local journal. There is no checkpoint or log segmentation, and the books accumulate postings in memory.
- Live reconciliation reads the journal on the engine thread. Long histories can delay new commands.
- Metrics retain latency samples and sort them under a lock. Their space and latency costs need measuring for long-running use.
- The health endpoint shows that the process is responding. Engine readiness and failover health are not modeled separately.
- IOC and amendment are available through core APIs. They are not exposed as full HTTP and dashboard workflows.
- Docker execution, external k6 testing, AWS deployment, and high-availability drills remain unverified.
