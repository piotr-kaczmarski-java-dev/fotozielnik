"""Creates perf users through the API and seeds their libraries with SQL (seed.sql) in a throwaway database.

Usage: python seed.py --base http://localhost:8082 --container <postgres container id> \
           --admin perf-admin@fotozielnik.test --admin-password perf-admin-password
"""
import argparse
import subprocess
from pathlib import Path

from client import Api, ensure_user

ap = argparse.ArgumentParser()
ap.add_argument("--base", required=True)
ap.add_argument("--container", required=True)
ap.add_argument("--db", default="test")
ap.add_argument("--admin", required=True)
ap.add_argument("--admin-password", required=True)
a = ap.parse_args()

admin = Api(a.base).login(a.admin, a.admin_password)
sql = (Path(__file__).parent / "seed.sql").read_text()
for email, photos in (("big@perf.test", 300_000), ("other1@perf.test", 50_000), ("other2@perf.test", 50_000)):
    _, uid = ensure_user(a.base, admin, email)
    print(f"seeding {photos} photos for {email} ({uid}) ...", flush=True)
    subprocess.run(["docker", "exec", "-i", a.container, "psql", "-q", "-U", a.db, "-d", a.db,
                    "-v", f"owner={uid}", "-v", f"photos={photos}", "-v", "batches=30", "-f", "-"],
                   input=sql, text=True, check=True)
print("done")
