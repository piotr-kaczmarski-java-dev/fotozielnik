"""ML spike (R1): a test set from your own identified photos, without copying them.

Folder layout: one subfolder per species, named with the Latin or the Polish name, e.g.
    zielnik/Pulsatilla patens/IMG_0012.jpg
    zielnik/Sasanka otwarta/IMG_0040.JPG        (Polish names work too)
Names are matched (case-insensitively) against data/labels.json from fetch_inat.py; unknown names are listed.

Usage: .venv/bin/python make_testset.py /path/to/zielnik  ->  data/own/manifest.json
then:  .venv/bin/python evaluate.py --models bioclip-2 --manifest data/own/manifest.json
"""
import json
import sys
from pathlib import Path

from evaluate import DATA

JPEG = {".jpg", ".jpeg", ".jpe"}


def main(root: Path):
    labels = json.loads((DATA / "labels.json").read_text())
    by_name = {}
    for label in labels:
        by_name[label["name"].lower()] = label
        if label["polish"]:
            by_name.setdefault(label["polish"].lower(), label)
    manifest, unknown = [], []
    for folder in sorted(p for p in root.iterdir() if p.is_dir()):
        label = by_name.get(folder.name.strip().lower())
        photos = sorted(p for p in folder.rglob("*") if p.suffix.lower() in JPEG)
        if label is None:
            unknown.append(f"{folder.name} ({len(photos)} photos)")
            continue
        manifest += [{"file": str(p.absolute()), "taxon_id": label["id"], "name": label["name"]} for p in photos]
    out = DATA / "own" / "manifest.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(manifest, ensure_ascii=False, indent=1))
    print(f"{len(manifest)} photos of {len({m['taxon_id'] for m in manifest})} species -> {out}")
    if unknown:
        print("Not in the label set (check spelling, or the species is not on the Polish list):")
        print("  " + "\n  ".join(unknown))


if __name__ == "__main__":
    main(Path(sys.argv[1]))
