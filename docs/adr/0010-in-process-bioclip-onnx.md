# ADR-0010: Identify species with BioCLIP via ONNX Runtime inside the JVM

- Status: Accepted
- Date: 2026-11-18
- Related: FR-010, FR-014, FR-015, FR-016, NFR-003, NFR-006, NFR-013, R1, R2

## Context
Photos must not leave the server; no Python sidecar (owner requirement). The species list is Central
European vascular plants. Embeddings are also useful for grouping (FR-010) and kNN on confirmed IDs.

## Decision
- Run the BioCLIP **image encoder** exported to ONNX (int8-quantized if accuracy holds) with ONNX Runtime
  for Java; preprocessing (resize/crop 224, normalise) in Java. One shared `OrtSession`, bounded batch size.
- **Text prototypes are precomputed offline** for every in-scope taxon (BioCLIP text encoder, run once
  with a script in `tools/ml/`) and loaded into `taxon_prototype`; the app never runs the text encoder.
- Score = combination of zero-shot cosine to prototypes and kNN over the user's confirmed photo embeddings
  (pgvector HNSW). Thresholds for auto-accept / review / unknown in config.
- Model file downloaded to a volume on first start, verified by SHA-256; `ml_model` table versions
  models so a new one can be backfilled next to the old one.

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| Python sidecar (PyTorch/open_clip) | Easiest model handling | Second runtime/image; owner rejected it |
| Cloud API (Pl@ntNet, iNaturalist) | Strong accuracy | Photos leave the server; quotas/cost; commercial terms |
| DJL | Higher-level Java API | Extra abstraction over the same ONNX Runtime |

## Consequences
- Offline tooling (Python allowed there) for export and prototypes; runtime stays pure JVM.
- Early spike to confirm accuracy (R1) and CPU time (R2); BioCLIP vs BioCLIP 2 and model licences are open (Q2).

## Spike results (2026-11-19, `tools/ml/`)
Test set: 300 iNaturalist research-grade photos from Poland (60 of the 600 most-observed vascular species,
5 photos each, observed after 2025-06-01 to stay clear of the models' training data). Label set: all 2533
vascular species with research-grade observations in Poland. Both models are MIT-licensed (Q2, NFR-012).

| Model | Labels | Top-1 | Top-3 | Genus top-1 |
|---|---|---|---|---|
| BioCLIP (ViT-B/16) | 2533 | 0.68 | 0.88 | 0.78 |
| **BioCLIP 2 (ViT-L/14)** | 1000 | 0.96 | 0.99 | 0.98 |
| **BioCLIP 2 (ViT-L/14)** | 2533 | 0.93 | 0.98 | 0.96 |

- Prompt style hardly matters; "a photo of <kingdom..family> <species> with common name <English>" is used.
- **Java + ONNX Runtime 1.31.1 works**: Java preprocessing (bicubic resize 224, center crop, CLIP mean/std)
  gives the same accuracy as PyTorch (fp32 0.94/0.99, int8 0.94/0.98; embedding cosine to PyTorch 0.99 mean).
- **int8 dynamic quantization**: 1.2 GB -> 0.3 GB, no accuracy loss on this set, 3x faster.
  CPU, one thread (i5-13600KF): fp32 1.1 s, **int8 0.36 s per photo**; 4 threads int8 0.10 s.
  With thumbnails (~0.4 s) and a slower VPS core, ~1.4 s per photo per worker: 30k photos in ~4 h on 3
  workers (NFR-003 allows 8 h).
- Confidence (softmax of logits x100) for FR-015: p >= 0.9 covers 67% of photos at 98.5% precision (auto-accept
  candidate); p < 0.5 is 5% of photos at 60% correct (-> "unknown"/review).
- kNN on confirmed photos (simulated with other test photos of the same species): nearest confirmed photo
  with cosine >= 0.8 is right 99% of the time and lifts top-1 from 0.93 to 0.97.
- Polish names on iNaturalist: 100% of the 1000 most common species, 91% of all 2533 (R4 looks small).

**Decision:** BioCLIP 2, int8, image encoder in the JVM; preprocessing as in `tools/ml/java/OnnxSpike.java`.
**Caveats:** iNaturalist photos sorted by votes are well framed and show one clear subject; the father's
archive (many photos per plant, leaves/buds, poor light) will score lower - re-run `evaluate.py` on ~200 of
his identified photos (`make_testset.py`) before M3 thresholds are set. Only common species were tested.
Training-data overlap is unlikely (date filter) but not proven.

### Harder test set (2026-11-19)
1153 ordinary recent photos (newest first, not best-voted) of 165 species chosen across the whole list,
including rare ones; same 2533 labels. BioCLIP 2:

| Species (by number of observations in Poland) | Photos | Top-1 | Top-3 |
|---|---|---|---|
| ranks 1-300 | 250 | 0.93 | 0.98 |
| ranks 301-1000 | 590 | 0.86 | 0.95 |
| ranks 1001-2533 | 313 | 0.78 | 0.89 |
| **all** | 1153 | **0.86** | **0.94** (genus top-1 0.93) |

- Java + ONNX int8 gives the same: top-1 0.86, top-3 0.95.
- Confidence >= 0.9: 58% of photos at 98.5% precision - still a safe auto-accept tier; 0.5-0.9 belongs in review.
- Nearly all hard cases are siblings within a genus (Carex, Alchemilla, Orobanche, Anthoxanthum, Brassica):
  the review UI should offer "genus only" (e.g. *Carex* sp.) when the top suggestions share a genus.
- kNN on confirmed photos (cosine >= 0.8) lifts top-1 to 0.94 - the library gets better as the user confirms.
