"""ML spike (R1): zero-shot accuracy of BioCLIP / BioCLIP 2 on the iNaturalist test set (fetch_inat.py).

For each model and prompt style: top-1/3/5 species and top-1 genus accuracy against the N most common
species in Poland, plus a simulation of kNN on confirmed photos (FR-016): the other photos of the same
species in the test set play the user's confirmed references. Writes data/results-<model>.json and the
embeddings needed by the Java/ONNX check (data/<model>-image-emb.npy, data/<model>-files.json).

Usage: .venv/bin/python evaluate.py [--models bioclip bioclip-2] [--sizes 1000 3000] [--manifest data/own/manifest.json]
"""
import argparse
import json
import time
from pathlib import Path

import numpy as np
import open_clip
import torch
from PIL import Image

DATA = Path(__file__).parent / "data"
MODELS = {"bioclip": "hf-hub:imageomics/bioclip", "bioclip-2": "hf-hub:imageomics/bioclip-2"}


def prompts(label, style):
    taxo = " ".join(t for t in label["taxonomy"][:-1] if t)  # kingdom .. family
    sci = label["name"]
    if style == "scientific":
        return f"a photo of {sci}."
    if style == "taxonomic":
        return f"a photo of {taxo} {sci}."
    common = label.get("english")
    return f"a photo of {taxo} {sci} with common name {common}." if common else f"a photo of {taxo} {sci}."


@torch.no_grad()
def encode_texts(model, tokenizer, texts, batch=256):
    out = []
    for i in range(0, len(texts), batch):
        out.append(torch.nn.functional.normalize(model.encode_text(tokenizer(texts[i:i + batch])), dim=-1))
    return torch.cat(out)


@torch.no_grad()
def encode_images(model, preprocess, files, batch=32):
    out = []
    t0 = time.perf_counter()
    for i in range(0, len(files), batch):
        imgs = torch.stack([preprocess(Image.open(DATA / f).convert("RGB")) for f in files[i:i + batch]])
        out.append(torch.nn.functional.normalize(model.encode_image(imgs), dim=-1))
    per_image = (time.perf_counter() - t0) / len(files)
    return torch.cat(out), per_image


def accuracy(scores, truth_idx, label_genus, truth_genus):
    order = torch.argsort(scores, dim=1, descending=True)
    res = {}
    for k in (1, 3, 5):
        res[f"top{k}"] = float(np.mean([truth_idx[i] in order[i, :k].tolist() for i in range(len(truth_idx))]))
    res["genus_top1"] = float(np.mean([label_genus[order[i, 0]] == truth_genus[i] for i in range(len(truth_idx))]))
    return res


def breakdown(scores, truth, manifest, labels):
    order = torch.argsort(scores, dim=1, descending=True)[:, :3].tolist()
    ranks = [m.get("commonness_rank", t + 1) for m, t in zip(manifest, truth)]
    out = {"by_commonness": {}, "confidence": {}, "worst_species": []}
    for lo, hi in ((1, 300), (301, 1000), (1001, 10**6)):
        sel = [i for i, r in enumerate(ranks) if lo <= r <= hi]
        if sel:
            band = f"{lo}-{hi if hi < 10**6 else 'end'}"
            top1 = np.mean([order[i][0] == truth[i] for i in sel])
            top3 = np.mean([truth[i] in order[i] for i in sel])
            out["by_commonness"][band] = {"photos": len(sel), "species": len({truth[i] for i in sel}),
                                          "top1": float(top1), "top3": float(top3)}
            print(f"    species ranked {band:9s}: {len(sel):4d} photos  top1 {top1:.2f}  top3 {top3:.2f}")
    p = torch.softmax(scores * 100, dim=1)
    conf, pred = p.max(dim=1)
    ok = (pred == torch.tensor(truth)).numpy()
    conf = conf.numpy()
    for th in (0.9, 0.7, 0.5):
        sel = conf >= th
        out["confidence"][f">={th}"] = {"share": float(sel.mean()), "precision": float(ok[sel].mean()) if sel.any() else None}
        print(f"    confidence >= {th}: {sel.mean():.0%} of photos, {ok[sel].mean():.1%} correct")
    per = {}
    for i, t in enumerate(truth):
        per.setdefault(t, []).append(order[i][0] == t)
    worst = sorted(per.items(), key=lambda kv: np.mean(kv[1]))[:10]
    for t, hits in worst:
        wrong = [labels[order[i][0]]["name"] for i in range(len(truth)) if truth[i] == t and order[i][0] != t]
        common = max(set(wrong), key=wrong.count) if wrong else "-"
        out["worst_species"].append({"species": labels[t]["name"], "top1": float(np.mean(hits)), "confused_with": common})
        print(f"    worst: {labels[t]['name']:32s} top1 {np.mean(hits):.1f}  mostly taken for {common}")
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", nargs="+", default=list(MODELS))
    ap.add_argument("--sizes", nargs="+", type=int, default=[1000, 3000])
    ap.add_argument("--manifest", default=str(DATA / "test" / "manifest.json"),
                    help="test set: data/test (iNaturalist) or data/own (make_testset.py)")
    a = ap.parse_args()
    torch.set_num_threads(max(1, torch.get_num_threads()))
    labels = json.loads((DATA / "labels.json").read_text())
    manifest = json.loads(Path(a.manifest).read_text())
    tag = "" if Path(a.manifest).parent.name == "test" else f"-{Path(a.manifest).parent.name}"
    id_to_idx = {l["id"]: i for i, l in enumerate(labels)}
    files = [m["file"] for m in manifest]
    truth = [id_to_idx[m["taxon_id"]] for m in manifest]
    genus = [l["name"].split()[0] for l in labels]
    truth_genus = [genus[t] for t in truth]
    print(f"{len(files)} photos of {len(set(truth))} species; labels: {len(labels)}")

    for name in a.models:
        model, _, preprocess = open_clip.create_model_and_transforms(MODELS[name])
        tokenizer = open_clip.get_tokenizer(MODELS[name])
        model.eval()
        img, per_image = encode_images(model, preprocess, files)
        np.save(DATA / f"{name}{tag}-image-emb.npy", img.numpy())
        (DATA / f"{name}{tag}-files.json").write_text(json.dumps(files))
        result = {"model": name, "photos": len(files), "cpu_seconds_per_image_pytorch": per_image, "zero_shot": {}}
        for style in ("scientific", "taxonomic", "taxonomic+common"):
            text = encode_texts(model, tokenizer, [prompts(l, style) for l in labels])
            for n in a.sizes:
                n = min(n, len(labels))
                scores = img @ text[:n].T
                inside = [i for i, t in enumerate(truth) if t < n]
                r = accuracy(scores[inside], [truth[i] for i in inside], genus[:n], [truth_genus[i] for i in inside])
                result["zero_shot"][f"{style}@{n}"] = r
                print(f"{name:10s} {style:17s} @{n:5d}: " + "  ".join(f"{k} {v:.2f}" for k, v in r.items()))
            if style == "taxonomic+common":
                np.save(DATA / f"{name}-text-emb.npy", text.numpy())
        # where does it fail? by how common the species is, confidence tiers (FR-015), worst species
        text = torch.from_numpy(np.load(DATA / f"{name}-text-emb.npy"))
        result["breakdown"] = breakdown(img @ text.T, truth, manifest, labels)

        # kNN simulation (FR-016): the user's confirmed photos = the other test photos (leave-one-out).
        # Rule: if the nearest confirmed photo is closer than tau, take its species, else zero-shot top-1.
        text = torch.from_numpy(np.load(DATA / f"{name}-text-emb.npy"))
        zs_top1 = torch.argmax(img @ text.T, dim=1).tolist()
        sim = img @ img.T
        sim.fill_diagonal_(-1)
        nn_sim, nn_idx = sim.max(dim=1)
        result["knn"] = {}
        for tau in (0.6, 0.7, 0.8, 0.9):
            pred, used, used_ok = [], 0, 0
            for i in range(len(files)):
                if nn_sim[i] >= tau:
                    used += 1
                    p = truth[nn_idx[i]]
                    used_ok += p == truth[i]
                else:
                    p = zs_top1[i]
                pred.append(p)
            acc = float(np.mean([p == t for p, t in zip(pred, truth)]))
            result["knn"][f"tau{tau}"] = {"top1": acc, "used": used / len(files), "precision_when_used": used_ok / max(used, 1)}
            print(f"{name:10s} zero-shot + kNN tau {tau}: top1 {acc:.2f}  (kNN used {used / len(files):.0%}, right {used_ok / max(used, 1):.0%})")
        print(f"{name:10s} PyTorch CPU {per_image * 1000:.0f} ms/image (batch 32, {torch.get_num_threads()} threads)")
        (DATA / f"results-{name}{tag}.json").write_text(json.dumps(result, indent=1))


if __name__ == "__main__":
    main()
