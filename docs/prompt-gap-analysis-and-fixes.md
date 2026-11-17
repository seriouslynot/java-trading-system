# Prompt gap analysis and fixes

The baseline is commit `7c38618` of this repository. This pass follows the supplied
behavioral requirements and checks outcomes with rule scenarios, fixed-seed
invariants, direct result comparisons, recovery drills, and reconciliation.
Baseline and revised benchmarks use their respective workload definitions.

| ID | Requirement | Baseline gap | Change and files | Verification |
| --- | --- | --- | --- | --- |
| G01 | At least 20 named matching scenarios and IOC/amend coverage | Five combined scenarios | `PromptMatchingScenariosTest` adds 24 cases. `PromptIocAndModifyScenariosTest` adds 14 | Named suites passed |
| G02 | At least 10,000 steps with seed and step on failure | 1,500 random or 2,000 market steps | `OrderBookInvariantTest` extends both random suites and five regimes, checks IOC and maker/taker prices | Ten seeded tests and five market tests passed |
| G03 | At least 16 ledger scenarios | Behaviors combined in a few tests | `PromptLedgerScenariosTest` covers reservation, settlement, release, postings and amendments | 16 named tests plus existing invariants passed |
| G04 | Rejected orders are queryable, but can't be cancelled or amended | Rejected order absent from lookup | `MatchingEngine` saves rejected views in the bounded terminal cache and returns NOT_FOUND to write commands | `MatchingScenarioTest` and `EngineServicePromptTest` |
| G05 | REJECTED has zero filled, remaining and cancelled | Rejection marked all quantity cancelled | `OrderView` represents the three counters as zero | Rejected-order scenario and restart regression |
| G06 | Duplicate internal ID fails explicitly | Insertion failure returned false and could be ignored | `OrderBook` rejects duplicates and nonpositive resting quantities | Duplicate-ID test checks index and depth remain consistent |
| G07 | Duplicate returns the committed current order | Replayed first response could stay NEW | `EngineService` updates the order view kept with the idempotency key after commit | NEW-to-FILLED regression and concurrent duplicate test |
| G08 | Same duplicate semantics after restart | Startup built a historical view from the place record | `TradingCore` exposes a replay observer, used by `EngineService` to update keyed views after each replayed record | Rejected, filled and evicted-view cases plus the SIGKILL drill |
| G09 | Success rate includes normal business decisions, P99 samples queued work only | Rejections counted as system failures and cached retries entered the latency sample | `SecondStats`, `MetricsWindow`, `EngineService` | Formula and sample population tests |
| G10 | Alert thresholds, sample floors, and recovery streaks | Shorter windows and delayed reconciliation alerts | `AlertRule`, `AlertEvaluator`, `EngineService` | Thresholds, minimum traffic, trigger/recovery streak and immediate reconciliation tests |
| G11 | Repeatable 80/20 experiment by command count | Experiment only placed orders | `MixedOrderWorkload`, `ReconExperiment`, frontend `LedgerPage.tsx` and `api.ts` | 5,000-command replay plus packaged-service validation |
| G12 | Injection and detection scored by event and type | One affected posting per trade and no per-type score | `AnomalyInjector` applies the fault to four postings. `InjectionScore` groups by trade and type | One injected trade produces four differences and one detection. Seed 1 produced 19 of 19 detections and 76 differences |
| G13 | Large standard orders are within 500–2,000 | Existing range was 101–1,000 | `OrderGenerator` uses the specified size band | Volume bands and sampled 5% proportion |
| G14 | 50 customers, new order every tick, specified order and expiry distributions | Uniform spreads, 20 customers, arrivals skipped when work expired | `MarketWorkload` emits due commands first and models IOC, price distance, power-law tails, and exponential lifetimes | Five-regime structure comparison, order ownership, aggressive/passive offsets and ratios |
| G15 | Flash-crash bottom plateau and shared random shape | Immediate recovery after the drop | `MarketRegime` defines the plateau. `MidPricePath` preserves the separate Gaussian stream | Shape assertions at 30%, 32%, 34%, 35%, and 60% |
| G16 | Compare replay results directly | Local output hashes obscured the first divergence | `DeterministicExecutionTest` replaces the two former golden-output files | Three mixed seeds and five regimes compare every result and final book |
| G17 | Consumed results and warmed matching measurements | No result use or isolated latency measurements | `RegimeBenchmark`, `BenchSupport` | [Regime report](benchmarks/current/regime.md) |
| G18 | Same-input journal variants and actual force percentiles | Policies used different sample sizes and no force metric | `JournalBenchmark` uses matched and steady groups. `FileJournal` optionally observes force latency | [Journal report](benchmarks/current/journal.md) |
| G19 | Sustained virtual-thread pipeline mixed workload | Fixed count and only place commands | `PipelineBenchmark`, `app/build.gradle.kts` | [Pipeline report](benchmarks/current/pipeline.md) with post-run reconciliation |
| G20 | Strict LF/CRLF | CRLF could be rejected or truncated | `FileJournal` removes one CR at the line boundary before CRC validation | Mixed CRLF/LF open, append and reopen |
| G21 | Four codec records and tamper rejection | Direct round-trip coverage incomplete | `JournalTest` covers all variants and tampering | Four records round-trip and fail after field modification |

## Baseline and current runs

The [baseline](benchmarks/baseline) used the previous commit of the same
repository. On the same Apple M1 and Temurin 21.0.2, 62 core tests and 10 app
tests passed. Frontend installation, build, and lint passed, and all three
previous benchmarks generated reports. The old results are kept with the
original commands and methodology. Running the revised regression cases against
that baseline exposed all three targeted failures: valid CRLF was discarded,
a retry after a fill returned NEW, and the rejected order was absent from
the order lookup.

The revised `./gradlew clean check` passed 126 core tests and 14 app tests
with no failures, errors, or skips. The core runtime classpath is empty.
`npm ci`, build, and lint passed. A Boot JAR with bundled frontend returned
health UP and HTTP 200. The SIGKILL drill recovered all 64 acknowledged orders,
and retries returned their committed states without writing extra orders.

The revised [matching](benchmarks/current/regime.md),
[journal](benchmarks/current/journal.md), and [pipeline](benchmarks/current/pipeline.md)
reports use different methods from the baseline. A comparison between their
headline rates alone cannot establish a speedup. The packaged HTTP checks
exercise endpoint behavior, not external load. The k6 script remains available.

[Verification details and known limits](verification.md)
