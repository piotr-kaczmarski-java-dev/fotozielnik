#!/usr/bin/env bash
# ML spike: Java/ONNX inference for a model variant, then compare with PyTorch.
# Usage: tools/ml/run_java.sh <model> <variant: image|image-int8> <threads> [files.json]
set -euo pipefail
cd "$(dirname "$0")"
model=$1 variant=$2 threads=$3 files=${4:-data/$1-files.json}
out="data/$model-java-$variant-t$threads.bin"
java -cp models/onnxruntime-1.31.1.jar java/OnnxSpike.java "models/$model-$variant.onnx" \
    "models/$model-preprocess.json" "$files" data "$out" "$threads"
.venv/bin/python compare_java.py "$model" "$out"
