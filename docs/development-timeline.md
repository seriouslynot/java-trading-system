# Development timeline / 开发时间线

The original project was developed on a company computer over approximately two to three months, as reported by the author. The phase order below follows the original implementation notes. The original Git history and exact milestone dates are unavailable.

据作者回顾，原版在公司电脑上开发了约两到三个月。下表按原版实施笔记中的顺序整理。原版 Git 历史和各阶段的确切日期未随复刻资料提供。

| 阶段 / Phase | 主要工作 / Scope |
| --- | --- |
| 1. 核心撮合 / Matching | 搭建 Gradle 多模块，完成限价订单簿、价格时间优先、撤单、IOC 和改单。 / Set up Gradle modules and implement the limit order book, price-time priority, cancel, IOC, and amend. |
| 2. 账本与恢复 / Accounting and recovery | 加入资金与持仓冻结、复式分录、事件日志和重放。 / Add reservations, double-entry postings, the event journal, and replay. |
| 3. 对账与接入 / Reconciliation and admission | 加入逐笔对账、异常注入、幂等、限流、有界队列和 Spring Boot API。 / Add posting reconciliation, fault injection, idempotency, rate limiting, bounded queues, and Spring Boot APIs. |
| 4. 展示与性能 / Dashboard and performance | 完成交易、账本和运营页面，分层测量撮合、持久化与完整接入链路。用组提交减少逐条落盘的开销。 / Build trading, ledger, and operations views. Measure matching, persistence, and admission separately. Use group commit to amortize journal force calls. |

## Public reconstruction / 公开复刻

**2026-11-13.** This repository was reconstructed from the project briefs and interface specification. Its commits record the work in this repository. The reconstruction adds a matched-workload comparison of the two journal policies and a repeatable process-crash drill.

**2026-11-13。** 根据项目说明和接口规格在当前仓库复刻。Git 提交记录对应此仓库的工作。复刻版还补充了两种落盘策略的同口径对比，以及可复现的进程崩溃恢复演练。

- [本机验收记录 / Local verification](验收记录.md)
- [同口径性能基准 / Matched-workload benchmark](portfolio/benchmark.md)
- [恢复演练输出 / Crash recovery output](portfolio/crash-recovery.json)
