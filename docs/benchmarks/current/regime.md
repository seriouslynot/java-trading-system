# Matching engine by market regime

JDK 21.0.2+13-LTS, Mac OS X 15.7.5, aarch64, 8 logical processors.

Seed 1. Two warmup rounds and eleven timed rounds per regime. Each run starts with an empty book and consumes order, trade, and sequence results. Latency is measured in a separate pass, sampling every 32nd command. The dispatch-only baseline is reported without subtraction. Values are medians across runs.

| Regime | Steps | Commands | Commands/s | Mean ns/command | Dispatch ns/command | P50 ns | P99 ns | P99.9 ns |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| STATIC | 300000 | 593310 | 4382752 | 228.2 | 7.0 | 166 | 1250 | 4125 |
| NORMAL | 300000 | 593310 | 3516566 | 284.4 | 20.5 | 166 | 2708 | 12292 |
| LARGE_SWING | 300000 | 593310 | 3453637 | 289.5 | 22.4 | 166 | 2000 | 6375 |
| FLASH_CRASH_40 | 300000 | 593310 | 3428921 | 291.6 | 15.3 | 166 | 2459 | 8625 |
| FLASH_CRASH_60 | 300000 | 593310 | 2936867 | 340.5 | 5.1 | 166 | 2708 | 8041 |

Consumed result checksum: 479713519464
