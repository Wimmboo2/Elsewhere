#!/usr/bin/env python3
"""Side-by-side prototype vs app screenshots.

  python3 tools/verify/compare.py      -> verify/compare/<name>.png + verify/compare/report.md

The prototype's fake status bar (top 32dp) and gesture bar (bottom 24dp) are masked out.
The metric is the mean absolute difference per channel (0-255) over the phone area,
plus the share of pixels that differ by more than 24 levels; both are only a hint,
the images are what gets reviewed (content such as GeoNames coordinates differs by design).
"""
import os, sys
from PIL import Image, ImageChops, ImageDraw, ImageStat

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "verify")
REF, APP, OUT = (os.path.join(ROOT, d) for d in ("ref", "app", "compare"))
os.makedirs(OUT, exist_ok=True)
DP = 2.625

rows = []
for name in sorted(os.listdir(REF)):
    if not name.endswith(".png"):
        continue
    ap = os.path.join(APP, name)
    if not os.path.exists(ap):
        rows.append((name, None, None))
        continue
    r = Image.open(os.path.join(REF, name)).convert("RGB")
    a = Image.open(ap).convert("RGB").resize(r.size, Image.LANCZOS)
    top, bot = int(32 * DP), r.size[1] - int(24 * DP)
    rc, ac = r.crop((0, top, r.size[0], bot)), a.crop((0, top, r.size[0], bot))
    diff = ImageChops.difference(rc, ac)
    mean = sum(ImageStat.Stat(diff).mean) / 3
    g = diff.convert("L").point(lambda v: 255 if v > 24 else 0)
    share = ImageStat.Stat(g).mean[0] / 255 * 100
    heat = Image.merge("RGB", (g, Image.new("L", g.size, 0), Image.new("L", g.size, 0)))
    w, h = r.size
    sheet = Image.new("RGB", (w * 3 + 40, h), (255, 255, 255))
    sheet.paste(r, (0, 0)); sheet.paste(a, (w + 20, 0))
    blend = Image.blend(rc, heat, 0.6)
    sheet.paste(blend, (2 * w + 40, top))
    d = ImageDraw.Draw(sheet)
    d.text((10, 10), "prototype", fill=(255, 0, 0)); d.text((w + 30, 10), "app", fill=(255, 0, 0)); d.text((2 * w + 50, 10), "diff > 24", fill=(255, 0, 0))
    sheet.resize((sheet.size[0] // 2, sheet.size[1] // 2)).save(os.path.join(OUT, name))
    rows.append((name, mean, share))

with open(os.path.join(OUT, "report.md"), "w") as f:
    f.write("| state | mean abs diff | pixels > 24 |\n|---|---|---|\n")
    for n, m, s in rows:
        f.write(f"| {n[:-4]} | {'missing' if m is None else f'{m:.1f}'} | {'' if s is None else f'{s:.1f}%'} |\n")
print(open(os.path.join(OUT, "report.md")).read())
