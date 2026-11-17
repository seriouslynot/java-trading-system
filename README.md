# Java Trading System

A single-symbol limit-order trading system independently rebuilt from a requirements prompt using Java 21, Spring Boot, and React. It matches orders by price and time, settles trades in a double-entry ledger, and rebuilds state by replaying its journal.

The verification suite covers matching scenarios, seeded invariants, direct comparison of two engines running the same commands, journal recovery, and event-level reconciliation. [Behavioral verification](docs/verification.md) · [Prompt requirements and changes](docs/prompt-gap-analysis-and-fixes.md)

The original project took approximately two to three months to develop. This repository records the public reconstruction and verification on November 13, 2026. [Development timeline](docs/development-timeline.md)

## Architecture

- `engine-core` implements price-time matching, immediate-or-cancel orders, amendments, cash and inventory reservations, settlement, reconciliation, and journal replay in pure Java.
- `app` provides the REST API, idempotency, rate limiting, and a bounded queue with optional cancel priority. One engine thread owns the order book and ledger. With group commit enabled, it forces the journal before acknowledging orders and publishing updated read snapshots.
- `frontend` provides trading, ledger and reconciliation, and operations dashboards. It polls the read APIs once per second.

## Build and run

Use JDK 21. Building the dashboard also requires Node.js and npm. On Windows, use `gradlew.bat` in place of `./gradlew`.

On macOS or Linux, build the dashboard and executable JAR together:

```sh
./scripts/build-bundle.sh
java -jar app/build/libs/app-0.1.0-SNAPSHOT.jar --server.address=127.0.0.1
```

Open <http://localhost:8080>. The first run seeds accounts A, B, C, D, and S01–S20. The journal defaults to `data/journal.log` relative to the launch directory. Use `--trading.journal-path=data/another.log` to start with a separate journal. If `TRADING_ADMIN_TOKEN` is set, supply it through the dashboard's admin token control or the `X-Admin-Token` request header.

For a partial fill, submit a sell order for five shares at 100.00 as A and a buy order for three shares at the same price as B. Then cancel A's remaining two shares. The REST API represents prices in integer cents.

```sh
curl http://localhost:8080/actuator/health
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"A","side":"SELL","price":10000,"quantity":5,"clientOrderId":"demo-1"}'
curl -X POST http://localhost:8080/api/recon/run
```

For frontend development, run the API and Vite separately:

```sh
./gradlew :app:bootRun
```

```sh
cd frontend
npm ci
npm run dev -- --port 5173
```

Open <http://localhost:5173>. Vite proxies API requests to the backend.

## Verification and benchmarks

```sh
./gradlew clean check
./gradlew :engine-core:regimeBench
./gradlew :engine-core:journalBench
./gradlew :app:pipelineBench -PpipelineWarmupSeconds=3 -PpipelineMeasureSeconds=10
./gradlew :app:bootJar
python3 scripts/crash-recovery.py
```

The benchmarks write reports under each module's `build/bench/` directory.
`regimeBench` measures the standalone matching engine. `journalBench` measures the core with matching, accounting, and journal persistence. `pipelineBench` measures the in-process admission path under 512 concurrent workers. These numbers exclude HTTP. [Current reports](docs/benchmarks/current) · [Baseline reports](docs/benchmarks/baseline) The default journal policy is `GROUP`, with up to 256 commands per batch. Use `--trading.journal-fsync=EVERY_RECORD` to force each record or `OS_DEFAULT` to rely on operating-system buffering. The default queue capacity is 2,000, and the default request timeout is 2,000 ms. [Verification report](docs/verification.md)

## Container

```sh
docker build -t trading-system .
docker volume create trading-data
docker run --rm -p 127.0.0.1:8080:8080 -v trading-data:/data trading-system
```

The image bundles the API and dashboard and runs as user 10001. The external HTTP load-test script is `k6 run scripts/loadtest/orders.js`. Docker and k6 were not exercised in the recorded local run.

## Reconstruction scope

The current project follows the supplied requirements and interface specification. It verifies
determinism by comparing order, cancel and modify results from two engines given the
same input, then comparing their final books. Scenario and invariant tests separately
check correctness. IOC and amendment are available through the core Java interface.

External HTTP load testing with k6, Docker execution, and AWS deployment remain
unverified. The journal is a single local file. The project has no primary/replica
failover or cross-region recovery. [Known limits](docs/verification.md#known-limits)
