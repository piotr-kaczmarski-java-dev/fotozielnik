# tools/ml - offline ML tooling (ADR-0010)

Python lives only here, offline: export the model, compute text prototypes, measure accuracy.
The app itself runs the exported ONNX image encoder in the JVM (no Python at runtime, NFR-013).
`.venv/`, `data/` and `models/` are gitignored (large; test photos are not ours to redistribute).

## Setup (CPU is enough)

```bash
cd tools/ml
python3 -m venv .venv
.venv/bin/pip install --index-url https://download.pytorch.org/whl/cpu torch torchvision
.venv/bin/pip install open_clip_torch onnx onnxruntime onnxscript requests pillow
curl -o models/onnxruntime-1.31.1.jar https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.31.1/onnxruntime-1.31.1.jar
```

Disk: ~1 GB venv, 1.7 GB BioCLIP 2 weights (Hugging Face cache), 1.5 GB ONNX exports.

## Scripts

| Script | What it does |
|---|---|
| `fetch_inat.py` | Label set (all vascular plants observed in Poland on iNaturalist, with taxonomy + Polish names) and a test set of recent research-grade photos |
| `identify.py <folders/files>` | **Quick look on any photos (no labels needed)**: top 5 species with Polish names + confidence, HTML report `data/identify.html` |
| `report_pdf.py` | The identify report as a small self-contained HTML + A4 PDF (photos resized to 1000 px) - for e-mail/print |
| `facebook_export.py <export.zip> <out>` | Photos + species names from a Facebook data export (captions, post text, album names; Polish inflection and abbreviations like "O. mniejszy"): one folder per species + `photos.csv` for review |
| `make_testset.py <folder>` | Test set from **your own identified photos**: one subfolder per species, Latin or Polish name |
| `evaluate.py` | Zero-shot top-1/3/5 + genus accuracy, kNN-on-confirmed-photos simulation, saves embeddings |
| `export_onnx.py bioclip-2` | Image encoder -> ONNX fp32 + int8, preprocessing JSON for Java, check vs PyTorch |
| `run_java.sh bioclip-2 image-int8 1` | Runs `java/OnnxSpike.java` (ONNX Runtime + preprocessing in plain Java), compares with PyTorch |

## Quick look at your own photos

```bash
.venv/bin/python identify.py ~/Zdjęcia/rośliny/2024-05       # any folders or files
xdg-open data/identify.html                                  # green >= 90 %, yellow review, red unknown
.venv/bin/python report_pdf.py                               # -> data/identify.pdf (a few MB, to send)
```

## Testing on the father's photos (R1)

```bash
.venv/bin/python make_testset.py /path/to/zielnik      # zielnik/Pulsatilla patens/*.jpg, zielnik/Sasanka otwarta/*.jpg ...
.venv/bin/python evaluate.py --models bioclip-2 --sizes 1000 2533 --manifest data/own/manifest.json
```

Results of the first spike: ADR-0010, "Spike results".
