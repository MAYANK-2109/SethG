"""
Builds a binary e-waste / not-e-waste image folder from the downloaded sources.

  data/prepared/ewaste/      GIZ scrapyard photos, PCB boards, batteries
  data/prepared/not_ewaste/  bottles, cans, paper, cardboard, clothes, shoes,
                             glass, food waste, general trash

Images are resized so the long side is 320 px to keep training fast.
"""
import io
import random
from pathlib import Path

import pyarrow.parquet as pq
from PIL import Image, ImageOps

DATA = Path(__file__).parent / "data"
OUT = DATA / "prepared"
LONG_SIDE = 320
MAX_PER_CLASS = 350   # keeps any single source (e.g. batteries) from dominating

random.seed(42)


def save(img: Image.Image, dest: Path):
    img = ImageOps.exif_transpose(img).convert("RGB")
    img.thumbnail((LONG_SIDE, LONG_SIDE))
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.save(dest, "JPEG", quality=90)


def from_files(paths, label, prefix, limit=None):
    paths = sorted(paths)
    random.shuffle(paths)
    n = 0
    for p in paths[:limit]:
        try:
            with Image.open(p) as img:
                save(img, OUT / label / f"{prefix}_{n:05d}.jpg")
            n += 1
        except Exception as e:  # corrupt / unreadable image
            print("  skip", p.name, e)
    print(f"  {label:<11} {prefix:<24} {n}")
    return n


def main():
    counts = {"ewaste": 0, "not_ewaste": 0}

    # ── Positives ────────────────────────────────────────────────────────────
    counts["ewaste"] += from_files(list((DATA / "giz").glob("*.jpg")), "ewaste", "giz")

    for i, pf in enumerate(sorted(DATA.glob("pcb_*.parquet"))):
        n = 0
        for row in pq.read_table(pf).to_pylist():
            img = row.get("image")
            if isinstance(img, dict) and img.get("bytes"):
                save(Image.open(io.BytesIO(img["bytes"])), OUT / "ewaste" / f"pcb{i}_{n:05d}.jpg")
                n += 1
        print(f"  ewaste      pcb {pf.name:<20} {n}")
        counts["ewaste"] += n

    # ── Garbage 12-class: battery → e-waste, everything else → not e-waste ──
    class_dirs = [d for d in (DATA / "garbage12").rglob("*") if d.is_dir() and any(d.glob("*.jpg"))]
    for d in sorted(class_dirs):
        imgs = list(d.glob("*.jpg"))
        if d.name.lower() == "battery":
            counts["ewaste"] += from_files(imgs, "ewaste", "battery", limit=MAX_PER_CLASS)
        else:
            counts["not_ewaste"] += from_files(imgs, "not_ewaste", d.name.replace(" ", "_"),
                                              limit=MAX_PER_CLASS)

    print("TOTAL", counts)


if __name__ == "__main__":
    main()
