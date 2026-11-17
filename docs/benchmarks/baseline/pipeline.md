# In-process order pipeline benchmark

512 concurrent workers used the same input sequence generated with seed 1. Each policy ran three times in alternating order, with a fresh journal and seeded accounts for each run. The benchmark includes queueing, matching, accounting, and journal persistence. It excludes HTTP.

| Policy | Run | Commands | Commands/s | P99 (ms) | Successful | Business rejections | Fast rejections | Timeouts | Reconciliation differences |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| EVERY_RECORD | 1 | 4096 | 258 | 2083.35 | 4096 | 0 | 0 | 0 | 0 |
| EVERY_RECORD | 2 | 4096 | 257 | 2046.90 | 4096 | 0 | 0 | 0 | 0 |
| EVERY_RECORD | 3 | 4096 | 258 | 2034.21 | 4096 | 0 | 0 | 0 | 0 |
| GROUP | 1 | 4096 | 52875 | 11.00 | 4096 | 0 | 0 | 0 | 0 |
| GROUP | 2 | 4096 | 49509 | 13.06 | 4096 | 0 | 0 | 0 | 0 |
| GROUP | 3 | 4096 | 55908 | 12.65 | 4096 | 0 | 0 | 0 | 0 |

| Policy | Median commands/s | Median P99 (ms) |
|---|---:|---:|
| EVERY_RECORD | 258 | 2046.90 |
| GROUP | 52875 | 12.65 |
