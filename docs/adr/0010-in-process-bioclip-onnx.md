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
