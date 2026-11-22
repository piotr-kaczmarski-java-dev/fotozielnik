"""ML spike: suggest species for your own photos (no labels needed) - a quick look at how BioCLIP 2 does.

Prints the top 5 suggestions per photo (Polish + Latin name, confidence) and writes an HTML page with each
photo next to its suggestions. Needs data/labels.json (fetch_inat.py) and data/bioclip-2-text-emb.npy
(evaluate.py) - both already there after the spike.

Usage: .venv/bin/python identify.py /path/to/photos [more files or folders ...]
       -> data/identify.html (open it in a browser)
"""
import html
import json
import sys
from pathlib import Path

import numpy as np
import open_clip
import torch
from PIL import Image, ImageOps

DATA = Path(__file__).parent / "data"
JPEG = {".jpg", ".jpeg", ".jpe", ".png"}


def photos(args):
    for a in map(Path, args):
        if a.is_dir():
            yield from sorted(p for p in a.rglob("*") if p.suffix.lower() in JPEG)
        elif a.suffix.lower() in JPEG:
            yield a


@torch.no_grad()
def main(args):
    labels = json.loads((DATA / "labels.json").read_text())
    text = torch.from_numpy(np.load(DATA / "bioclip-2-text-emb.npy"))
    model, _, preprocess = open_clip.create_model_and_transforms("hf-hub:imageomics/bioclip-2")
    model.eval()
    rows = []
    files = list(photos(args))
    print(f"{len(files)} photos")
    for f in files:
        img = ImageOps.exif_transpose(Image.open(f)).convert("RGB")  # phone/camera photos may be rotated
        emb = torch.nn.functional.normalize(model.encode_image(preprocess(img).unsqueeze(0)), dim=-1)
        p = torch.softmax((emb @ text.T)[0] * 100, dim=0)
        top = torch.topk(p, 5)
        sugg = [(labels[i], float(s)) for s, i in zip(top.values, top.indices.tolist())]
        genus = {l["name"].split()[0] for l, _ in sugg[:3]}
        print(f"\n{f}")
        for l, s in sugg:
            print(f"  {s:5.0%}  {l['polish'] or '-':35s} {l['name']}")
        if len(genus) == 1 and sugg[0][1] < 0.9:
            print(f"  (top suggestions share the genus {genus.pop()} - genus is likely right)")
        rows.append((f, sugg))

    out = DATA / "identify.html"
    cells = []
    for f, sugg in rows:
        lis = "".join(
            f"<li><b>{s:.0%}</b> {html.escape(l['polish'] or '')} <i>{html.escape(l['name'])}</i></li>" for l, s in sugg
        )
        tier = "auto" if sugg[0][1] >= 0.9 else "review" if sugg[0][1] >= 0.5 else "unknown"
        cells.append(
            f'<div class="c {tier}"><img src="{f.absolute().as_uri()}"><p>{html.escape(f.name)} - {tier}</p><ol>{lis}</ol></div>'
        )
    out.write_text(
        "<!doctype html><meta charset=utf-8><title>BioCLIP 2 - propozycje</title><style>"
        "body{font:16px system-ui;margin:1rem;background:#f6f8f4}.g{display:grid;grid-template-columns:repeat(auto-fill,minmax(320px,1fr));gap:1rem}"
        ".c{background:#fff;border:3px solid #ccc;border-radius:8px;padding:.5rem}.c img{width:100%;height:240px;object-fit:cover}"
        ".auto{border-color:#2f6b34}.review{border-color:#c8a200}.unknown{border-color:#a33}ol{margin:.3rem 0;padding-left:1.4rem}"
        "</style><h1>BioCLIP 2 - propozycje gatunków</h1><p>Ramka: zielona = pewne (&ge; 90%), żółta = do przejrzenia, "
        "czerwona = nieznane (&lt; 50%).</p><div class=g>" + "".join(cells) + "</div>"
    )
    print(f"\nreport: {out}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
