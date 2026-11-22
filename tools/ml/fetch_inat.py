"""ML spike (R1): label set + test set from iNaturalist (Poland, vascular plants, research grade).

Labels: the most-observed species in Poland with taxonomy and Polish names -> data/labels.json
Test:   a few recent photos (after --since, to stay clear of BioCLIP's training data) of a sample of the
        common species -> data/test/<taxon_id>/<photo_id>.jpg + data/test/manifest.json (licence, attribution)

Local evaluation only: the photos are not committed or redistributed.
Usage: .venv/bin/python fetch_inat.py [--labels 3000] [--species 60] [--per-species 5] [--since 2025-06-01]
Harder set: fetch_inat.py --reuse-labels --species 200 --per-species 10 --pool 0 --order created_at --out test-hard
"""
import argparse
import json
import time
from pathlib import Path

import requests

API = "https://api.inaturalist.org/v1"
POLAND = 7800
TRACHEOPHYTA = 211194
RANKS = ["kingdom", "phylum", "class", "order", "family", "genus"]
DATA = Path(__file__).parent / "data"
session = requests.Session()
session.headers["User-Agent"] = "fotozielnik-ml-spike (research script)"


def get(path, **params):
    for attempt in range(5):
        try:
            r = session.get(f"{API}{path}", params=params, timeout=60)
        except requests.ConnectionError:
            time.sleep(5 * (attempt + 1))
            continue
        if r.status_code == 429 or r.status_code >= 500:
            time.sleep(5 * (attempt + 1))
            continue
        r.raise_for_status()
        time.sleep(1.0)  # iNaturalist asks for <= 1 request/s
        return r.json()
    raise RuntimeError(f"giving up on {path}")


def download(url):
    """Photo bytes; the photo CDN sometimes drops a connection, so retry with backoff."""
    for attempt in range(5):
        try:
            r = session.get(url, timeout=60)
            if r.status_code < 500 and r.status_code != 429:
                r.raise_for_status()
                return r.content
        except requests.ConnectionError:
            pass
        time.sleep(5 * (attempt + 1))
    raise RuntimeError(f"giving up on {url}")


def labels(n):
    species = []
    page = 1
    while len(species) < n:
        d = get("/observations/species_counts", place_id=POLAND, taxon_id=TRACHEOPHYTA,
                quality_grade="research", rank="species", per_page=500, page=page, locale="pl")
        if not d["results"]:
            break
        species += d["results"]
        page += 1
    species = species[:n]
    ancestor_ids = sorted({a for s in species for a in s["taxon"]["ancestor_ids"]})
    names = {}
    for i in range(0, len(ancestor_ids), 30):
        for t in get("/taxa/" + ",".join(map(str, ancestor_ids[i:i + 30])), per_page=30)["results"]:
            names[t["id"]] = (t["rank"], t["name"])
    out = []
    for s in species:
        t = s["taxon"]
        lineage = {names[a][0]: names[a][1] for a in t["ancestor_ids"] if a in names}
        out.append({
            "id": t["id"],
            "name": t["name"],
            "polish": t.get("preferred_common_name"),
            "english": t.get("english_common_name"),
            "observations": s["count"],
            "taxonomy": [lineage.get(r, "") for r in RANKS],
        })
    return out


def test_set(label_list, n_species, per_species, since, pool_size=600, order_by="votes", out="test"):
    # spread over the pool_size most common species (600 = what an amateur botanist photographs most;
    # all labels = including rare species)
    pool = label_list[:pool_size]
    step = max(1, len(pool) // n_species)
    chosen = pool[::step][:n_species]
    manifest = []
    for sp in chosen:
        d = get("/observations", taxon_id=sp["id"], place_id=POLAND, quality_grade="research", d1=since,
                photo_license="cc0,cc-by,cc-by-nc,cc-by-sa,cc-by-nc-sa", per_page=per_species * 2,
                order_by=order_by)
        taken = 0
        for obs in d["results"]:
            if taken >= per_species or not obs.get("photos"):
                continue
            if obs["taxon"]["id"] != sp["id"]:  # identified to a subspecies etc.
                continue
            photo = obs["photos"][0]
            url = photo["url"].replace("/square.", "/medium.")
            target = DATA / out / str(sp["id"]) / f"{photo['id']}.jpg"
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists():
                target.write_bytes(download(url))
                time.sleep(0.5)
            manifest.append({"file": str(target.relative_to(DATA)), "taxon_id": sp["id"], "name": sp["name"],
                             "commonness_rank": label_list.index(sp) + 1,
                             "observation": obs["id"], "license": photo.get("license_code"),
                             "attribution": photo.get("attribution")})
            taken += 1
        print(f"{sp['name']:40s} {taken} photos")
    (DATA / out).mkdir(parents=True, exist_ok=True)
    (DATA / out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1))
    return manifest


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--labels", type=int, default=3000)
    ap.add_argument("--species", type=int, default=60)
    ap.add_argument("--per-species", type=int, default=5)
    ap.add_argument("--since", default="2025-06-01")
    ap.add_argument("--pool", type=int, default=600, help="choose species among the N most common (0 = all)")
    ap.add_argument("--order", default="votes", choices=["votes", "created_at"],
                    help="votes = best-liked photos first; created_at = ordinary recent photos")
    ap.add_argument("--out", default="test", help="folder under data/")
    ap.add_argument("--reuse-labels", action="store_true", help="keep data/labels.json")
    a = ap.parse_args()
    DATA.mkdir(exist_ok=True)
    if a.reuse_labels:
        lbl = json.loads((DATA / "labels.json").read_text())
    else:
        lbl = labels(a.labels)
        (DATA / "labels.json").write_text(json.dumps(lbl, ensure_ascii=False, indent=1))
    print(f"{len(lbl)} labels; {sum(1 for l in lbl if l['polish'])} with a Polish name")
    m = test_set(lbl, a.species, a.per_species, a.since, a.pool or len(lbl), a.order, a.out)
    print(f"{len(m)} test photos")
