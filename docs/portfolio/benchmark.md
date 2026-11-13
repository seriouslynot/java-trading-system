# 进程内接入层基准

512 个并发任务，同一条种子为 1 的输入序列。每个策略 3 次，交替顺序。每轮用新的日志和演示账户。含排队、撮合、记账与落盘，排除 HTTP。

| 策略 | 轮次 | 命令 | 命令/秒 | P99 ms | 成功 | 业务拒绝 | 快速拒绝 | 超时 | 对账差异 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| EVERY_RECORD | 1 | 4096 | 254 | 2128.86 | 4096 | 0 | 0 | 0 | 0 |
| EVERY_RECORD | 2 | 4096 | 254 | 2167.51 | 4096 | 0 | 0 | 0 | 0 |
| EVERY_RECORD | 3 | 4096 | 257 | 2492.89 | 4096 | 0 | 0 | 0 | 0 |
| GROUP | 1 | 4096 | 45419 | 15.35 | 4096 | 0 | 0 | 0 | 0 |
| GROUP | 2 | 4096 | 47418 | 15.07 | 4096 | 0 | 0 | 0 | 0 |
| GROUP | 3 | 4096 | 36798 | 19.48 | 4096 | 0 | 0 | 0 | 0 |

| 策略 | 命令/秒中位数 | P99 ms 中位数 |
|---|---:|---:|
| EVERY_RECORD | 254 | 2167.51 |
| GROUP | 45419 | 15.35 |

Environment: Apple M1, macOS 15.7.5, Temurin 21.0.2. The benchmark generates the same inputs for each policy. Arrival order at the engine can vary with thread scheduling. Request P99 includes time spent waiting in the queue and excludes fast admission rejections.

```sh
./gradlew :app:pipelineBench -PpipelineCommands=4096
```
