"""ML spike: do embeddings from Java (OnnxSpike.java) match PyTorch, and is accuracy the same?

Usage: .venv/bin/python compare_java.py bioclip data/bioclip-java-emb.bin
"""
import json
import sys

import numpy as np

from evaluate import DATA

name, path = sys.argv[1], sys.argv[2]
ref = np.load(DATA / f"{name}-image-emb.npy")
java = np.fromfile(path, dtype="<f4").reshape(-1, ref.shape[1])
ref = ref[: len(java)]
cos = (java * ref).sum(axis=1)
print(f"{len(java)} images: cosine Java vs PyTorch min {cos.min():.4f} mean {cos.mean():.4f}")
text = np.load(DATA / f"{name}-text-emb.npy")
labels = json.loads((DATA / "labels.json").read_text())
manifest = json.loads((DATA / "test" / "manifest.json").read_text())[: len(java)]
idx = {l["id"]: i for i, l in enumerate(labels)}
truth = np.array([idx[m["taxon_id"]] for m in manifest])
for label, emb in (("pytorch", ref), ("java", java)):
    order = np.argsort(-(emb @ text.T), axis=1)
    top1 = np.mean(order[:, 0] == truth)
    top3 = np.mean([t in o[:3] for t, o in zip(truth, order)])
    print(f"{label:8s} top1 {top1:.2f} top3 {top3:.2f}")
