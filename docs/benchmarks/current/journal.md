# Trading core by journal policy

JDK 21.0.2+13-LTS, Mac OS X 15.7.5, aarch64, 8 logical processors.

Seed 1, roughly 80% place and 20% cancel attempts, 20 seeded accounts. The matched policies use the same 4,000-command prefix. The 80,000-command GROUP runs are separate steady-workload measurements. Every policy gets 512 warmup commands on a separate fresh journal, then three timed runs. Encode-only uses the same ledger and matching code with an in-memory text journal. Batch and force columns report medians of the three run-level percentiles.

| Workload | Policy | Commands | Batch | Commands/s | Mean µs/command | Batch P50 µs | Batch P99 µs | Force P50 µs | Force P99 µs |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Matched | ENCODE_ONLY | 4000 | 1 | 221474 | 4.52 | 2.88 | 11.04 | — | — |
| Matched | EVERY_RECORD | 4000 | 1 | 258 | 3874.25 | 3989.58 | 5158.25 | 3842.88 | 4996.88 |
| Matched | GROUP | 4000 | 1 | 258 | 3875.01 | 3990.42 | 5153.92 | 3855.29 | 5101.96 |
| Matched | GROUP | 4000 | 8 | 2059 | 485.68 | 3992.33 | 5048.75 | 3783.29 | 4794.63 |
| Matched | GROUP | 4000 | 64 | 13515 | 73.99 | 4982.13 | 6100.00 | 4310.75 | 5302.21 |
| Matched | GROUP | 4000 | 256 | 50032 | 19.99 | 4995.17 | 6054.88 | 4053.92 | 4744.54 |
| Matched | GROUP | 4000 | 1024 | 190082 | 5.26 | 5038.58 | 6022.96 | 3704.04 | 4605.58 |
| Matched | OS_DEFAULT | 4000 | 1 | 280763 | 3.56 | 3.00 | 10.04 | — | — |
| Steady | GROUP | 80000 | 64 | 15247 | 65.59 | 4009.33 | 7045.25 | 3700.29 | 5719.46 |
| Steady | GROUP | 80000 | 256 | 49232 | 20.31 | 4996.17 | 15036.33 | 3929.42 | 5744.04 |
| Steady | GROUP | 80000 | 1024 | 165964 | 6.03 | 5989.04 | 17908.13 | 4102.38 | 6528.38 |
