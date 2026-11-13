# Development timeline

The original project was developed on a company computer over approximately two to three months. The phase order below follows the original implementation notes. The original Git history and exact milestone dates were unavailable for the public reconstruction.

| Phase | Work |
| --- | --- |
| Matching | Set up the Gradle modules and implement the limit order book, price-time priority, cancellation, immediate-or-cancel orders, and amendments. |
| Accounting and recovery | Add cash and inventory reservations, double-entry postings, the event journal, and deterministic replay. |
| Reconciliation and admission | Add posting reconciliation, fault injection, idempotency, rate limiting, bounded queues, and the Spring Boot API. |
| Dashboard and performance | Build the trading, ledger, and operations views. Benchmark matching, persistence, and admission separately. Use group commit to share journal force calls across concurrent requests. |

## Public reconstruction

**November 13, 2026.** The project was reconstructed from the implementation briefs and interface specification. The reconstruction adds a matched-workload comparison of the journal policies and a reproducible process-crash drill.

- [Local verification](verification.md)
- [Matched-workload benchmark](portfolio/benchmark.md)
- [Crash recovery output](portfolio/crash-recovery.json)
