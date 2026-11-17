# In-process order pipeline benchmark

JDK 21.0.2+13-LTS, Mac OS X 15.7.5, aarch64, 8 logical processors.

512 closed-loop virtual-thread workers, seed 1. Each worker has up to 8 candidate resting orders and attempts a cancel on 20% of iterations with an order available. Workers cancel instead of placing when their own limit is reached. Each policy uses a fresh journal and 3 s warmup followed by 10 s measurement. HTTP, rate limiting, and queue capacity enforcement are excluded. Request timeout is 30 s. Measured operations drain at the end, and wall time includes that drain. Latency includes queue time and excludes fast rejections.

| Policy | Elapsed s | Place | Cancel | Completed/s | Successful | Business rejected | Unadmitted | Timeout | P50 ms | P99 ms | P99.9 ms | Latency samples | Recon differences |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| EVERY_RECORD | 12.039 | 2092 | 440 | 210 | 2313 | 219 | 0 | 0 | 2401.883 | 2613.835 | 2617.883 | 2532 | 0 |
| GROUP 64 | 10.032 | 94679 | 53801 | 14800 | 117117 | 31363 | 0 | 0 | 45.067 | 63.912 | 74.515 | 148480 | 0 |
| GROUP 256 | 10.014 | 306735 | 174173 | 48025 | 381023 | 99885 | 0 | 0 | 10.396 | 19.749 | 41.144 | 480908 | 0 |
| GROUP 1024 | 10.009 | 296563 | 168098 | 46426 | 368179 | 96482 | 0 | 0 | 10.185 | 25.742 | 462.890 | 464661 | 0 |
| OS_DEFAULT | 10.002 | 1322271 | 750437 | 207221 | 1644156 | 428552 | 0 | 0 | 3.109 | 11.585 | 24.139 | 2072708 | 0 |
