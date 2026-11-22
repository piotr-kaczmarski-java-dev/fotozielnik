"""Turns the identify.py report into a small, self-contained HTML + PDF (e.g. to send by e-mail).

The report links to the original photos (often 3-10 MB each), so printing it to PDF embeds them at full size.
This resizes every photo (EXIF rotation applied), embeds it in the HTML and prints a PDF with Chromium
(Playwright from frontend/node_modules).

Usage: .venv/bin/python report_pdf.py [data/identify.html] [--edge 1000] [--quality 75]
       -> data/identify-small.html, data/identify.pdf
"""
import argparse
import base64
import io
import re
import subprocess
import urllib.parse
from pathlib import Path

from PIL import Image, ImageOps

HERE = Path(__file__).parent
PRINT_CSS = """
<style>
.c img{height:auto;max-height:420px;object-fit:contain;background:#f0f0f0}
@media print{
  @page{size:A4;margin:12mm}
  body{margin:0;background:#fff;font-size:9.5pt}
  /* grid rows do not fragment well across pages in Chromium: plain inline blocks do */
  .g{display:block}
  .c{display:inline-block;vertical-align:top;width:calc(33.3% - 4mm);margin:0 2mm 3mm;box-sizing:border-box;padding:.3rem;
     break-inside:avoid;page-break-inside:avoid}
  .c img{max-height:42mm}
  .c p,.c ol{margin:.15rem 0}
  ol{padding-left:1.1rem}
  h1{font-size:16pt;margin:0 0 2mm}
}
</style>
"""


def small(path: Path, edge: int, quality: int) -> str:
    img = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
    img.thumbnail((edge, edge), Image.Resampling.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=quality, optimize=True, progressive=True)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("report", nargs="?", default=str(HERE / "data" / "identify.html"))
    ap.add_argument("--edge", type=int, default=1000, help="long edge of embedded photos in px")
    ap.add_argument("--quality", type=int, default=75)
    a = ap.parse_args()
    src = Path(a.report)
    html = src.read_text(encoding="utf-8")

    def replace(m):
        path = Path(urllib.parse.unquote(urllib.parse.urlparse(m.group(1)).path))
        return f'<img src="{small(path, a.edge, a.quality)}"'

    html = re.sub(r'<img src="(file://[^"]+)"', replace, html)
    html = html.replace("</style>", "</style>" + PRINT_CSS, 1)
    out_html = src.with_name(src.stem + "-small.html")
    out_html.write_text(html, encoding="utf-8")
    out_pdf = src.with_suffix(".pdf")
    subprocess.run(["node", str(HERE / "html_to_pdf.mjs"), str(out_html.absolute()), str(out_pdf.absolute())],
                   check=True, cwd=HERE.parent.parent / "frontend")
    for f in (out_html, out_pdf):
        print(f"{f}  {f.stat().st_size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
