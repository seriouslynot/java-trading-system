# Baseline runs

Rerun of commit `7c38618` on November 17, 2026. Apple M1 MacBook Air (8 GB), macOS
15.7.5, Temurin 21.0.2, Gradle 9.9.1. These reports use the original workloads and
should be read separately from the revised benchmark design.

```sh
./gradlew clean check
(cd frontend && npm ci && npm run build && npm run lint)
./gradlew :engine-core:regimeBench :engine-core:journalBench :app:pipelineBench
```

Java tests: 62 core and 10 app passed, with no failures, errors, or skipped tests.
Frontend installation, build, and lint passed. The three benchmark reports in
this directory are from that same checkout.
