"""Plant photos with species names from a Facebook data export ("Download your information", JSON format).

Facebook keeps the photos plus the caption text, but no EXIF, GPS or comments. This script:
  1. collects every photo from albums, posts and "uncategorized photos", with its caption, the post text
     and the album name (Facebook's mojibake - UTF-8 read as Latin-1 - is repaired);
  2. finds species names in that text, matched against data/labels.json (fetch_inat.py): Latin binomials
     and Polish names, tolerating Polish inflection ("trędownika skrzydlatego" -> Trędownik skrzydlaty);
  3. writes JPEGs (AVIF converted) into one folder per species, Latin name - the layout make_testset.py
     reads - with the capture time from Facebook's metadata written back as EXIF DateTimeOriginal;
  4. writes photos.csv with every photo, its text and what was matched, for review.

Usage: python3 -I facebook_export.py <export.zip or extracted folder> <output folder>
"""

import csv
import io
import json
import re
import sys
import zipfile
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from PIL import Image

LABELS = Path(__file__).resolve().parent / "data" / "labels.json"
WARSAW = ZoneInfo("Europe/Warsaw")
WORD = re.compile(r"[^\W\d_]+")
MULTI = "_kilka gatunków"


def fix(text: str) -> str:
    try:
        return text.encode("latin-1").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return text


# --- reading the export ---------------------------------------------------------------------------


class Export:
    def __init__(self, path: Path):
        self.zip = zipfile.ZipFile(path) if path.is_file() else None
        self.root = path

    def names(self):
        if self.zip:
            return self.zip.namelist()
        return [str(p.relative_to(self.root)) for p in self.root.rglob("*") if p.is_file()]

    def read(self, name: str) -> bytes:
        return self.zip.read(name) if self.zip else (self.root / name).read_bytes()


def photo_record(media: dict, **context) -> dict:
    exif = (media.get("media_metadata", {}).get("photo_metadata", {}).get("exif_data") or [{}])[0]
    return {
        "uri": media["uri"],
        "taken": exif.get("taken_timestamp"),
        "uploaded": media.get("creation_timestamp"),
        "camera": " ".join(fix(exif[k]) for k in ("camera_make", "camera_model") if exif.get(k)),
        "description": fix(media.get("description", "")).strip(),
        **{k: v for k, v in context.items()},
    }


def collect(export: Export) -> list[dict]:
    """All photos; a photo found in several places keeps every piece of text."""
    photos: dict[str, dict] = {}

    def add(record: dict):
        if not record["uri"].lower().endswith((".jpg", ".jpeg", ".avif", ".png", ".webp")):
            return
        known = photos.setdefault(record["uri"], record)
        for key in ("description", "post", "album", "album_description"):
            if record.get(key) and not known.get(key):
                known[key] = record[key]

    for name in export.names():
        if not name.endswith(".json") or "/posts/" not in name:
            continue
        data = json.loads(export.read(name))
        if "/album/" in name:
            album, album_desc = fix(data.get("name", "")), fix(data.get("description", ""))
            for media in data.get("photos", []):
                add(photo_record(media, album=album, album_description=album_desc))
        elif name.endswith("your_uncategorized_photos.json"):
            for media in data.get("other_photos_v2", []):
                add(photo_record(media))
        elif "your_posts" in name:
            for post in data:
                text = " ".join(fix(d["post"]) for d in post.get("data", []) if d.get("post"))
                for attachment in post.get("attachments", []):
                    for item in attachment.get("data", []):
                        if "media" in item and "uri" in item["media"]:
                            add(photo_record(item["media"], post=text))
    return sorted(photos.values(), key=lambda p: (p["taken"] or p["uploaded"] or 0, p["uri"]))


# --- finding species names -------------------------------------------------------------------------


def stem(word: str) -> str:
    """Polish names inflect at the end: compare all but the last two letters of longer words."""
    return word[:-2] if len(word) >= 6 else word


def word_matches(token: str, word: str) -> bool:
    return token == word or (len(word) >= 6 and token.startswith(stem(word)) and len(token) <= len(word) + 4)


class Matcher:
    def __init__(self, labels: list[dict]):
        self.names: dict[str, list[tuple[list[str], dict, str]]] = {}
        for label in labels:
            for kind, name in (("latin", label["name"]), ("polish", label.get("polish") or "")):
                words = WORD.findall(name.lower())
                if len(words) >= 2:
                    self.names.setdefault(words[0][:3], []).append((words, label, kind))

    def find(self, text: str) -> list[tuple[dict, str]]:
        tokens = WORD.findall(text.lower())
        found: dict[int, tuple[dict, str, int]] = {}
        for i, token in enumerate(tokens):
            for words, label, kind in self.names.get(token[:3], ()):
                if i + len(words) > len(tokens):
                    continue
                if all(word_matches(tokens[i + j], w) for j, w in enumerate(words)):
                    exact = sum(tokens[i + j] == w for j, w in enumerate(words))
                    if label["id"] not in found or exact > found[label["id"]][2]:
                        found[label["id"]] = (label, kind, exact)
        # two labels matching the same words via stems (e.g. siblings differing in an ending): keep exact ones
        best = max((e for _, _, e in found.values()), default=0)
        return [(label, kind) for label, kind, e in found.values() if e == best or e == len(label["name"].split())]


ABBREVIATION = re.compile(r"(?<![^\W\d_])([^\W\d_]{1,2})\.\s*([^\W\d_]{3,})")


def resolve_abbreviations(text: str, recent_genera: list[str], labels: list[dict]) -> list[tuple[dict, str]]:
    """'O. mniejszy', 'Ch. driakiewnik' - a series of photos names the genus once, then abbreviates it.
    The genus is taken from the nearest earlier photo whose genus fits; failing that, it must be unique."""
    hits = []
    for letters, epithet in ABBREVIATION.findall(text):
        letters, epithet = letters.lower(), epithet.lower()
        candidates = []
        for label in labels:
            for kind, name in (("latin", label["name"]), ("polish", label.get("polish") or "")):
                words = WORD.findall(name.lower())
                if len(words) == 2 and words[0].startswith(letters) and word_matches(epithet, words[1]):
                    candidates.append((label, kind, words[1] == epithet))
        # an exact epithet beats an inflected one ('okrągłolistny' is not 'okrągłolistna')
        if any(exact for _, _, exact in candidates):
            candidates = [c for c in candidates if c[2]]
        candidates = [(label, kind) for label, kind, _ in candidates]
        genera = {label["name"].split()[0] for label, _ in candidates}
        chosen = next((g for g in recent_genera if g in genera), None)
        if chosen is None and len(genera) == 1:
            chosen = genera.pop()
        hits += [(label, kind) for label, kind in candidates if label["name"].split()[0] == chosen][:1]
    return hits


def identify(photos: list[dict], matcher: Matcher, labels: list[dict]):
    """Text priority: the photo's own caption, then the post, then the album name (not the album
    description: it describes the whole album, e.g. 'orchid meadow', not each photo)."""
    recent: list[str] = []
    for photo in sorted(photos, key=lambda p: (p["uploaded"] or 0, p["uri"])):
        photo["species"], photo["source"] = [], ""
        for source in ("description", "post", "album"):
            hits = matcher.find(photo.get(source) or "")
            if hits:
                photo["species"], photo["source"] = hits, source
                break
        if not photo["species"] and photo["description"]:
            hits = resolve_abbreviations(photo["description"], recent, labels)
            if hits:
                photo["species"], photo["source"] = hits, "abbreviation"
        for label, _ in photo["species"]:
            genus = label["name"].split()[0]
            recent = [genus] + [g for g in recent if g != genus][:30]


# --- writing ---------------------------------------------------------------------------------------


def to_jpeg(data: bytes, uri: str, taken: int | None) -> bytes:
    """JPEGs stay byte-identical apart from an inserted EXIF block; other formats are re-encoded."""
    exif = Image.Exif()
    if taken:
        stamp = datetime.fromtimestamp(taken, WARSAW)
        exif[0x0132] = stamp.strftime("%Y:%m:%d %H:%M:%S")
        sub = exif.get_ifd(0x8769)
        sub[0x9003] = stamp.strftime("%Y:%m:%d %H:%M:%S")
        sub[0x9011] = stamp.strftime("%z")[:3] + ":" + stamp.strftime("%z")[3:]
    exif_bytes = exif.tobytes() if taken else b""
    if uri.lower().endswith((".jpg", ".jpeg")):
        if not exif_bytes or b"Exif\x00\x00" in data[:64 * 1024]:
            return data
        segment = b"Exif\x00\x00" + exif_bytes
        return data[:2] + b"\xff\xe1" + (len(segment) + 2).to_bytes(2, "big") + segment + data[2:]
    image = Image.open(io.BytesIO(data)).convert("RGB")
    out = io.BytesIO()
    image.save(out, "JPEG", quality=92, exif=exif_bytes)
    return out.getvalue()


def safe(name: str) -> str:
    return re.sub(r'[/\\:*?"<>|]', "_", name).strip() or "_"


def main(source: Path, target: Path):
    export = Export(source)
    photos = collect(export)
    labels = json.loads(LABELS.read_text())
    identify(photos, Matcher(labels), labels)
    target.mkdir(parents=True, exist_ok=True)
    written = 0
    for photo in photos:
        if not photo["species"]:
            continue
        names = sorted({label["name"] for label, _ in photo["species"]})
        folder = target / (safe(names[0]) if len(names) == 1 else MULTI)
        folder.mkdir(exist_ok=True)
        stem_name = Path(photo["uri"]).stem if len(names) == 1 else f"{Path(photo['uri']).stem} - {', '.join(names)}"
        out = folder / f"{safe(stem_name)}.jpg"
        out.write_bytes(to_jpeg(export.read(photo["uri"]), photo["uri"], photo["taken"]))
        photo["file"] = str(out.relative_to(target))
        written += 1

    with open(target / "photos.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["file", "latin", "polish", "matched_in", "taken", "camera", "description", "post", "album",
                    "facebook_uri"])
        for p in photos:
            taken = datetime.fromtimestamp(p["taken"], WARSAW).strftime("%Y-%m-%d %H:%M") if p["taken"] else ""
            w.writerow([p.get("file", ""), "; ".join(l["name"] for l, _ in p["species"]),
                        "; ".join(l.get("polish") or "" for l, _ in p["species"]), p["source"], taken, p["camera"],
                        p["description"], p.get("post", ""), p.get("album", ""), p["uri"]])

    species = {l["name"] for p in photos for l, _ in p["species"]}
    with_text = sum(1 for p in photos if p["description"] or p.get("post"))
    print(f"{len(photos)} photos, {with_text} with a caption or post text")
    print(f"{written} photos with a species name ({len(species)} species) -> {target}")
    print(f"review: {target / 'photos.csv'}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(Path(sys.argv[1]).expanduser(), Path(sys.argv[2]).expanduser())
