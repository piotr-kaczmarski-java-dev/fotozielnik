"""API latency for a big library (NFR-001: p95 < 500 ms for interactive reads). Run seed.py first.

Usage: python bench.py --base http://localhost:8082 [--email big@perf.test] [--clients 1]
"""
import argparse
import hashlib
import random
import statistics
import threading
import time

from client import Api

ap = argparse.ArgumentParser()
ap.add_argument("--base", required=True)
ap.add_argument("--email", default="big@perf.test")
ap.add_argument("--password", default="perf-user-password")
ap.add_argument("--clients", type=int, default=1)
ap.add_argument("--rounds", type=int, default=30)
a = ap.parse_args()

results = {}
lock = threading.Lock()


def timed(name, fn):
    t0 = time.perf_counter()
    r = fn()
    ms = (time.perf_counter() - t0) * 1000
    if r.status_code >= 400:
        raise RuntimeError(f"{name}: HTTP {r.status_code} {r.text[:200]}")
    with lock:
        results.setdefault(name, []).append(ms)
    return r


def worker(seed):
    rnd = random.Random(seed)
    api = Api(a.base).login(a.email, a.password)
    batch = api.send("POST", "/api/imports", json={"label": "bench", "filesFound": 1, "filesSkipped": 0}).json()["id"]
    imports = api.get("/api/imports?size=20").json()["items"]
    for _ in range(a.rounds):
        timed("session", lambda: api.get("/api/auth/session"))
        page = timed("photos first page (60)", lambda: api.get("/api/photos?size=60")).json()
        cursor = page["nextCursor"]
        for _ in range(rnd.randint(5, 40)):  # scroll down a while
            page = api.get(f"/api/photos?size=60&cursor={cursor}").json()
            cursor = page["nextCursor"]
        page = timed("photos deep page (60)", lambda: api.get(f"/api/photos?size=60&cursor={cursor}")).json()
        pid = rnd.choice(page["items"])["id"]
        timed("photo details", lambda: api.get(f"/api/photos/{pid}"))
        timed("imports list (20 + counts)", lambda: api.get("/api/imports?size=20"))
        bid = rnd.choice(imports)["id"]
        timed("import progress (10k photos)", lambda: api.get(f"/api/imports/{bid}"))
        hashes = [hashlib.sha256(f"x{rnd.random()}".encode()).hexdigest() for _ in range(200)]
        timed("check 200 hashes", lambda: api.send("POST", f"/api/imports/{batch}/check", json={"sha256s": hashes}))


threads = [threading.Thread(target=worker, args=(i,)) for i in range(a.clients)]
t0 = time.perf_counter()
for t in threads:
    t.start()
for t in threads:
    t.join()
print(f"{a.clients} client(s), {a.rounds} rounds each, {time.perf_counter() - t0:.0f} s")
print(f"{'endpoint':32s} {'n':>4s} {'p50':>7s} {'p95':>7s} {'max':>7s}  (ms)")
for name, v in results.items():
    v.sort()
    p95 = v[min(len(v) - 1, int(len(v) * 0.95))]
    flag = "  <-- over 500 ms" if p95 > 500 else ""
    print(f"{name:32s} {len(v):4d} {statistics.median(v):7.1f} {p95:7.1f} {v[-1]:7.1f}{flag}")
