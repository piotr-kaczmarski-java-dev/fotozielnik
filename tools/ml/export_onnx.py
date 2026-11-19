"""ML spike (ADR-0010): export the BioCLIP image encoder to ONNX for ONNX Runtime in the JVM.

Writes models/<model>-image.onnx (fp32) and models/<model>-image-int8.onnx (dynamic int8 quantization),
models/<model>-preprocess.json (what Java must do before inference) and checks ONNX vs PyTorch.
The app never runs the text encoder: text prototypes are computed offline (evaluate.py saves them).

Usage: .venv/bin/python export_onnx.py bioclip
"""
import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
import open_clip
import torch
from onnxruntime.quantization import QuantType, quantize_dynamic
from PIL import Image

from evaluate import DATA, MODELS

OUT = Path(__file__).parent / "models"


class ImageEncoder(torch.nn.Module):
    """pixels (N,3,H,W), already normalised -> L2-normalised embedding (N,D)"""

    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, pixels):
        return torch.nn.functional.normalize(self.model.encode_image(pixels), dim=-1)


def describe(preprocess):
    steps = {}
    for t in preprocess.transforms:
        n = type(t).__name__
        if n == "Resize":
            steps["resize"] = t.size if isinstance(t.size, int) else list(t.size)
            steps["interpolation"] = str(t.interpolation).split(".")[-1].lower()
        elif n == "CenterCrop":
            steps["crop"] = list(t.size)
        elif n == "Normalize":
            steps["mean"], steps["std"] = list(t.mean), list(t.std)
    return steps


def main(name):
    OUT.mkdir(exist_ok=True)
    model, _, preprocess = open_clip.create_model_and_transforms(MODELS[name])
    model.eval()
    pre = describe(preprocess)
    (OUT / f"{name}-preprocess.json").write_text(json.dumps(pre, indent=1))
    print("preprocess:", pre)
    size = pre["crop"][0]
    fp32 = OUT / f"{name}-image.onnx"
    torch.onnx.export(ImageEncoder(model), torch.randn(1, 3, size, size), fp32, input_names=["pixels"],
                      output_names=["embedding"], dynamic_axes={"pixels": {0: "n"}, "embedding": {0: "n"}},
                      opset_version=17, dynamo=False)
    int8 = OUT / f"{name}-image-int8.onnx"
    quantize_dynamic(str(fp32), str(int8), weight_type=QuantType.QInt8)

    files = json.loads((DATA / f"{name}-files.json").read_text())[:16]
    pixels = torch.stack([preprocess(Image.open(DATA / f).convert("RGB")) for f in files])
    with torch.no_grad():
        ref = ImageEncoder(model)(pixels).numpy()
    for path in (fp32, int8):
        sess = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
        out = sess.run(None, {"pixels": pixels.numpy()})[0]
        cos = (out * ref).sum(axis=1)
        print(f"{path.name}: {path.stat().st_size / 1e6:.0f} MB, cosine to PyTorch min {cos.min():.4f} mean {cos.mean():.4f}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "bioclip")
