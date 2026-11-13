# Local verification report

November 13, 2026. Apple M1 MacBook Air with 8 GB RAM, macOS 15.7.5, Temurin 21.0.2, Gradle 9.9.1, Node.js 25.9.0, and npm 11.12.1.

## Functional verification

- `./gradlew build` passed all 72 tests, including 62 core tests and 10 application tests. Coverage includes order book and ledger invariants, five market scenarios, journal replay, CRC32 verification, legacy journal parsing, and eight local regression fingerprints. A temporary Spring runtime dependency confirmed that `checkNoFramework` rejects direct and transitive Spring dependencies.
- The frontend build and lint passed. The executable JAR served the homepage, JavaScript, and CSS over HTTP.
- HTTP checks covered health, partial fills, cancel ownership, business and format errors, the admin token, concurrent acknowledgements after journal writes, and cash and inventory conservation. Restarting with the same journal preserved the order book, balances, recent trades, postings, and idempotency results.
- Reconciliation found zero differences without injection. Three independent runs at 1% injection detected all injected differences with no misses or false positives. A deterministic online test at 100% injection triggered the reconciliation alert.
- In group commit mode, the surge test submitted 5,000 orders per second for 20 seconds with admission controls enabled and disabled. Each run submitted 100,000 orders. The protected run rate-limited 79,505 requests, and the unprotected run had zero fast rejections. Both runs had zero timeouts and zero reconciliation differences. Metrics can include cancellation requests used to clear orders before the surge.
- A later SIGKILL drill recovered 64 of 64 acknowledged orders. All 64 retries returned their original results, balances matched, and reconciliation reported zero differences. [Crash drill output](portfolio/crash-recovery.json)

## Performance

The matched-workload comparison uses the same 4,096 generated orders and 512 concurrent workers for three runs per policy. It measures the in-process admission, matching, accounting, and persistence path. [Results and reproduction command](portfolio/benchmark.md)

The following table records the earlier benchmarks alongside the original project's reference results. Workload generators, hardware, and sample sizes differ.

| Measurement | Local result | Original reference |
| --- | ---: | ---: |
| Core matching across five market regimes | 214–304 ns per command | 208–270 ns per command |
| Direct core, EVERY_RECORD | 259 commands/s | 2,100–2,800 commands/s |
| Direct core, GROUP with batches of up to 256 | 55,135 commands/s | 128,000–260,000 commands/s |
| Direct core, OS_DEFAULT | 281,565 commands/s | — |
| In-process pipeline, EVERY_RECORD | 250 commands/s | 1,450–2,100 commands/s |
| In-process pipeline, GROUP | 61,659 commands/s | 120,000–188,000 commands/s |

Core matching used 300,000 pregenerated commands per regime, one warmup and three timed runs. The five median times were 304 ns for static, 229 ns for normal, 238 ns for large swing, 214 ns for a 40% flash crash, and 248 ns for a 60% flash crash. The direct-core benchmark used 4,000 commands for EVERY_RECORD and 80,000 for GROUP and OS_DEFAULT. The earlier pipeline benchmark used 512 concurrent workers, with 4,096 commands for EVERY_RECORD and 65,536 for GROUP. Both pipeline runs ended with zero reconciliation differences. OS_DEFAULT relies on operating-system buffering. The local persistence and pipeline figures were below the original reference ranges.

## Original fingerprints

The original workload generator and output serialization were unavailable. The project's eight regression fingerprints lock down this implementation's behavior. The original compatibility targets remain unverified.

| Workload | Original SHA-256 target |
| --- | --- |
| Standard, seed 1 | `95a07bb62c61574ce61e67760ee31f055d18ea84f47fde96e1ad7dacdab8a958` |
| Standard, seed 2 | `c48254ad8398904fed7fdd043ccf0e0ba8cbd91ab7dcdc873c0713bc47ed7bb2` |
| Standard, seed 3 | `43ef96b9a3b119e0814bd7a443ee490be315f36a530bde08fea031df48b0757e` |
| Static | `d0baba98e27169a0cdb0cebfc7e35fd14d700bdd312c90b6be51cd11e4273d12` |
| Normal | `49e2e9fbf29150ef7df9d2571c0e5fe7b7386f564543e15bd8100b32f4cc90a8` |
| Large swing | `291475a4503ae93a989220e664f5bfd9d8f1c04de06c80d4cbb5dc3de46fea13` |
| 40% flash crash | `b199eca721106095a2a3c0b31b14674a138c041b2a75731e198addec64deabca` |
| 60% flash crash | `7460860d094916368db4b867ec8210bd5f71cf976ff188201565b0c2fd7e2a7c` |

## Remaining checks

- The original test sources were unavailable. The 72 tests in this repository were written from the briefs.
- The live dashboard loaded in the local browser. Full click-through coverage of all trading, ledger, and operations workflows is pending.
- Docker execution, external k6 load testing, AWS deployment, and infrastructure failover were outside the recorded runs.
