"""Upload + background processing throughput (NFR-003: 30k photos overnight on 4 vCPU), browser-like client.

Generates N distinct JPEGs (cached in data/), uploads them like the import page does (check in chunks of 200,
3 parallel uploads) and polls the batch until every photo is processed. Samples the server JVM's memory.

Usage: python upload_bench.py --base http://localhost:8082 [--n 1000] [--size 3000x2000] [--pid <server java pid>]
"""
import argparse
import hashlib
import subprocess
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image

from client import Api, ensure_user

ap = argparse.ArgumentParser()
ap.add_argument("--base", required=True)
ap.add_argument("--n", type=int, default=1000)
ap.add_argument("--size", default="3000x2000")
ap.add_argument("--pid", type=int, help="server JVM pid, for memory sampling")
ap.add_argument("--admin", default="perf-admin@fotozielnik.test")
ap.add_argument("--admin-password", default="perf-admin-password")
a = ap.parse_args()
w, h = map(int, a.size.split("x"))

data = Path(__file__).parent / "data" / f"jpeg-{a.size}"
data.mkdir(parents=True, exist_ok=True)
rng = np.random.default_rng(1)
t0 = time.perf_counter()
for i in range(a.n):
    f = data / f"P{i:05d}.jpg"
    if f.exists():
        continue
    # smooth colour field + sensor-like noise: compresses like a real photo (~0.4 bytes/pixel at q 90)
    base = np.stack(np.meshgrid(np.linspace(0, 1, w // 8), np.linspace(0, 1, h // 8)), -1)
    field = np.concatenate([base, base[..., :1] * base[..., 1:]], -1) * rng.uniform(80, 200, 3) + rng.uniform(0, 50, 3)
    img = np.kron(field, np.ones((8, 8, 1)))[:h, :w] + rng.normal(0, 14, (h, w, 3))
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(f, quality=90)
files = sorted(data.glob("*.jpg"))[: a.n]
mb = sum(f.stat().st_size for f in files) / 1e6
print(f"{len(files)} files, {mb:.0f} MB ({mb / len(files):.1f} MB each), prepared in {time.perf_counter() - t0:.0f} s")

admin = Api(a.base).login(a.admin, a.admin_password)
api, _ = ensure_user(a.base, admin, f"upload-{int(time.time())}@perf.test")

rss = []
stop = threading.Event()


def sample():
    while a.pid and not stop.is_set():
        out = subprocess.run(["ps", "-o", "rss=", "-p", str(a.pid)], capture_output=True, text=True).stdout.strip()
        if out:
            rss.append(int(out) / 1024)
        stop.wait(1)


threading.Thread(target=sample, daemon=True).start()

start = time.perf_counter()
batch = api.send("POST", "/api/imports", json={"label": "perf", "filesFound": len(files), "filesSkipped": 0}).json()["id"]
hashes = {f: hashlib.sha256(f.read_bytes()).hexdigest() for f in files}
missing = set()
for i in range(0, len(files), 200):
    chunk = [hashes[f] for f in files[i:i + 200]]
    missing |= set(api.send("POST", f"/api/imports/{batch}/check", json={"sha256s": chunk}).json()["missing"])


def upload(f):
    r = api.send("PUT", f"/api/imports/{batch}/files/{hashes[f]}", data=f.read_bytes(),
                 headers={"Content-Type": "application/octet-stream", "X-File-Path": f.name,
                          "X-File-Modified": str(int(f.stat().st_mtime * 1000))})
    r.raise_for_status()


with ThreadPoolExecutor(3) as pool:
    list(pool.map(upload, [f for f in files if hashes[f] in missing]))
uploaded_at = time.perf_counter() - start
api.send("POST", f"/api/imports/{batch}/finish")
print(f"uploaded {len(missing)} files in {uploaded_at:.0f} s: {len(missing) / uploaded_at:.1f} files/s, {mb / uploaded_at:.0f} MB/s")

while True:
    b = api.get(f"/api/imports/{batch}").json()
    if b["processingPending"] == 0:
        break
    time.sleep(2)
total = time.perf_counter() - start
stop.set()
print(f"processed: {b['processingDone']} done, {b['processingFailed']} failed; all done {total:.0f} s after start")
print(f"=> {b['processingDone'] / total:.2f} photos/s overall; 30k photos would take {30000 / (b['processingDone'] / total) / 3600:.1f} h")
if rss:
    print(f"server JVM memory (RSS): max {max(rss):.0f} MB, median {sorted(rss)[len(rss) // 2]:.0f} MB")
