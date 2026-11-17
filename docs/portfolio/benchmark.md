# Historical in-process order pipeline benchmark

This November 13 run used the earlier orders-only workload and a fixed count per round. Reproduce it at commit `7c38618`. The current benchmark uses time windows with mixed place/cancel traffic. [Current reports](../benchmarks/current)

512 concurrent workers used the same input sequence generated with seed 1. Each policy ran three times in alternating order, with a fresh journal and seeded accounts for each run. The benchmark includes queueing, matching, accounting, and journal persistence. It excludes HTTP.

| Policy | Run | Commands | Commands/s | P99 (ms) | Successful | Business rejections | Fast rejections | Timeouts | Reconciliation differences |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| EVERY_RECORD | 1 | 4096 | 254 | 2128.86 | 4096 | 0 | 0 | 0 | 0 |
| EVERY_RECORD | 2 | 4096 | 254 | 2167.51 | 4096 | 0 | 0 | 0 | 0 |
| EVERY_RECORD | 3 | 4096 | 257 | 2492.89 | 4096 | 0 | 0 | 0 | 0 |
| GROUP | 1 | 4096 | 45419 | 15.35 | 4096 | 0 | 0 | 0 | 0 |
| GROUP | 2 | 4096 | 47418 | 15.07 | 4096 | 0 | 0 | 0 | 0 |
| GROUP | 3 | 4096 | 36798 | 19.48 | 4096 | 0 | 0 | 0 | 0 |

| Policy | Median commands/s | Median P99 (ms) |
|---|---:|---:|
| EVERY_RECORD | 254 | 2167.51 |
| GROUP | 45419 | 15.35 |

Environment: Apple M1, macOS 15.7.5, Temurin 21.0.2. The benchmark generates the same inputs for each policy. Arrival order at the engine can vary with thread scheduling. Request P99 includes time spent waiting in the queue and excludes fast admission rejections.

```sh
./gradlew :app:pipelineBench -PpipelineCommands=4096
```
