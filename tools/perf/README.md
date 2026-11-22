# tools/perf - load tests (NFR-001, NFR-003)

Run against a **throwaway** stack only: `seed.sql` writes hundreds of thousands of fake photos.
Python from `tools/ml/.venv` (needs `requests`, `numpy`, `pillow`). `data/` (generated JPEGs) is gitignored.

```bash
# 1. throwaway backend: fresh Testcontainers Postgres + Garage, VPS-like limits (3 workers, 2 GB heap)
cd backend && ADMIN_EMAIL=perf-admin@fotozielnik.test ADMIN_INITIAL_PASSWORD=perf-admin-password \
  FOTOZIELNIK_JOBS_WORKERS=3 JAVA_TOOL_OPTIONS=-Xmx2g \
  ./gradlew bootTestRun --args='--server.port=8082 --spring.docker.compose.enabled=false' &
# 2. its Postgres container
PG=$(docker ps --filter label=org.testcontainers=true --filter ancestor=fotozielnik/postgres:18 --format '{{.ID}}' | head -1)
cd tools/perf
../ml/.venv/bin/python seed.py --base http://localhost:8082 --container $PG \
  --admin perf-admin@fotozielnik.test --admin-password perf-admin-password     # 300k + 2 x 50k photos, ~10 s
../ml/.venv/bin/python bench.py --base http://localhost:8082 --clients 3       # API latency p50/p95
../ml/.venv/bin/python upload_bench.py --base http://localhost:8082 --n 1000 --pid $(pgrep -f TestApplication)
../ml/.venv/bin/python upload_bench.py --base http://localhost:8082 --n 200 --size 6000x4000 --pid ...
```

Note: Gradle runs the JVM with `-XX:TieredStopAtLevel=1` (no optimising compiler), so CPU-heavy numbers
are pessimistic compared with production.

## Results 2026-11-19 (i5-13600KF, 3 workers, 2 GB heap)

| What | Result |
|---|---|
| API with 300k photos, 3 concurrent users | grid first/deep page p95 6-7 ms, photo details 7 ms, hash check (200) 8 ms, import progress 9 ms, import history 54 ms |
| Import history before V6 | p95 145 ms: per-batch counts scanned the owner's whole index (fixed: V6 index + one grouped query) |
| Upload, 1000 x 6 MP (2.4 GB), 3 parallel | 154 MB/s server-side; processed 12.9 photos/s; JVM RSS max 1.1 GB |
| Upload, 200 x 24 MP (9.5 MB), 3 parallel | processed 7.2 photos/s (0.42 s per photo per worker); JVM RSS max 0.84 GB |

Estimate for the VPS (cores ~2x slower, 24 MP originals): ~3.5 photos/s -> 30k photos in ~2.5 h; with the M3
model (+0.7 s per photo) ~4-5 h. The real limit is the uploader's internet connection: 20 GB takes ~4.5 h at
10 Mbit/s upload, ~1 h at 50 Mbit/s.
