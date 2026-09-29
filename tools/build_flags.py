#!/usr/bin/env python3
"""Converts the flag-icons 4x3 set (MIT, github.com/lipis/flag-icons) into small VectorDrawables.

  python3 tools/build_flags.py            # writes app/src/main/res/drawable/flag_xx.xml + Flags.kt

Flags are drawn at 20-32dp in the app, so every shape is flattened, simplified with
Ramer-Douglas-Peucker (0.25 viewport units = 0.12dp at 32dp wide) and anything smaller
than about half a dp is dropped. The viewport is 64 x 48 with one decimal place.
Only countries present in the city dataset are converted.

Needs: pip install svgelements; flag-icons unpacked in tools/cache/package (tools/fetch_sources.sh).
"""
import gzip, math, os, re, sys
import xml.etree.ElementTree as ET
from svgelements import (SVG, Shape, Path, Move, Line, Close, CubicBezier, QuadraticBezier, Arc,
                         Color, Matrix, Group, Use)

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "cache", "package", "flags", "4x3")
RES = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
KT = os.path.join(HERE, "..", "app", "src", "main", "java", "app", "elsewhere", "ui", "flags", "FlagResources.kt")
ASSET = os.path.join(HERE, "..", "app", "src", "main", "assets", "cities.tsv.gz")

SCALE = 0.1          # 640x480 -> 64x48
TOL = 2.5            # RDP tolerance in source units (0.25 output units)
MIN_AREA = 60.0      # drop subpaths whose bbox is below this in source units^2 (0.6 output units^2)
MIN_STROKE = 2.0     # drop strokes thinner than this in source units
# (tolerance, min bbox area) passes, finest first: 0.12dp / 0.2dp / 0.3dp / 0.4dp at 32dp wide
LEVELS = [(2.5, 60.0), (4.0, 150.0), (6.0, 300.0), (8.0, 500.0)]


def num(v):
    s = f"{v:.1f}"
    if s.endswith(".0"):
        s = s[:-2]
    if s.startswith("0."):
        s = s[1:]
    elif s.startswith("-0."):
        s = "-" + s[2:]
    if s == "-0":
        s = "0"
    return s


def flatten(path):
    """Returns a list of (points, closed) subpaths in absolute coordinates."""
    subs, cur, closed = [], [], False
    for seg in path:
        if isinstance(seg, Move):
            if len(cur) > 1:
                subs.append((cur, closed))
            cur, closed = [(seg.end.x, seg.end.y)], False
        elif isinstance(seg, Close):
            closed = True
            if len(cur) > 1:
                subs.append((cur, True))
            cur = [cur[0]] if cur else []
            closed = False
        elif isinstance(seg, Line):
            if not cur:
                cur = [(seg.start.x, seg.start.y)]
            cur.append((seg.end.x, seg.end.y))
        elif isinstance(seg, (CubicBezier, QuadraticBezier, Arc)):
            if not cur:
                cur = [(seg.start.x, seg.start.y)]
            try:
                ln = seg.length(error=1e-2)
            except Exception:
                ln = 50
            n = max(2, min(64, int(ln / 3) + 2))
            for i in range(1, n + 1):
                p = seg.point(i / n)
                cur.append((p.x, p.y))
    if len(cur) > 1:
        subs.append((cur, closed))
    return subs


def rdp(pts, eps):
    if len(pts) < 3:
        return pts
    (x1, y1), (x2, y2) = pts[0], pts[-1]
    dx, dy = x2 - x1, y2 - y1
    d = math.hypot(dx, dy)
    best, idx = -1.0, 0
    for i in range(1, len(pts) - 1):
        px, py = pts[i]
        if d == 0:
            dist = math.hypot(px - x1, py - y1)
        else:
            dist = abs(dy * px - dx * py + x2 * y1 - y2 * x1) / d
        if dist > best:
            best, idx = dist, i
    if best > eps:
        return rdp(pts[: idx + 1], eps)[:-1] + rdp(pts[idx:], eps)
    return [pts[0], pts[-1]]


def simplify(sub, closed):
    if closed and len(sub) > 3:
        # split the ring at the point farthest from the start so RDP keeps the shape
        far = max(range(len(sub)), key=lambda i: (sub[i][0] - sub[0][0]) ** 2 + (sub[i][1] - sub[0][1]) ** 2)
        a = rdp(sub[: far + 1], TOL)
        b = rdp(sub[far:] + [sub[0]], TOL)
        return a[:-1] + b[:-1]
    return rdp(sub, TOL)


def bbox(pts):
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)


def encode(subs):
    out = []
    for pts, closed in subs:
        q = [(round(x * SCALE, 1), round(y * SCALE, 1)) for x, y in pts]
        dedup = [q[0]]
        for p in q[1:]:
            if p != dedup[-1]:
                dedup.append(p)
        if closed and len(dedup) < 3:
            continue
        if not closed and len(dedup) < 2:
            continue
        s = "M" + num(dedup[0][0]) + " " + num(dedup[0][1])
        px, py = dedup[0]
        body = []
        for x, y in dedup[1:]:
            ddx, ddy = round(x - px, 1), round(y - py, 1)
            if ddy == 0:
                body.append("h" + num(ddx))
            elif ddx == 0:
                body.append("v" + num(ddy))
            else:
                a, b = num(ddx), num(ddy)
                body.append("l" + a + ("" if b.startswith("-") else " ") + b)
            px, py = x, y
        # join commands; implicit repeated 'l' keeps it short
        txt, last = "", ""
        for cmd in body:
            c, rest = cmd[0], cmd[1:]
            if c == last:
                txt += ("" if rest.startswith("-") else " ") + rest
            else:
                txt += cmd
            last = c
        out.append(s + txt + ("z" if closed else ""))
    return "".join(out)


def argb(color, alpha=1.0):
    a = int(round(255 * alpha * (color.alpha / 255.0)))
    return "#%02X%02X%02X%02X" % (a, color.red, color.green, color.blue) if a < 255 else "#%02X%02X%02X" % (color.red, color.green, color.blue)



NS = "{http://www.w3.org/2000/svg}"
XL = "{http://www.w3.org/1999/xlink}href"
SHAPES = ("path", "rect", "circle", "ellipse", "polygon", "polyline", "line")
SKIP = ("defs", "clipPath", "marker", "symbol", "linearGradient", "radialGradient", "mask", "pattern", "title", "desc", "metadata", "style")
INHERIT = ("fill", "fill-rule", "fill-opacity", "stroke", "stroke-width", "stroke-opacity", "stroke-linejoin", "clip-rule",
           "marker-start", "marker-mid", "marker-end", "visibility")


def tag(el):
    return el.tag.split("}")[-1]


def attrs(el):
    a = dict(el.attrib)
    st = a.pop("style", None)
    if st:
        for part in st.split(";"):
            if ":" in part:
                k, v = part.split(":", 1)
                a[k.strip()] = v.strip()
    return a


def length(v, default=1.0):
    if v is None:
        return default
    m = re.match(r"\s*([-+]?[\d.]+(?:e[-+]?\d+)?)\s*([a-z%]*)", str(v))
    if not m:
        return default
    x = float(m.group(1))
    return x * {"pt": 4 / 3, "px": 1, "": 1, "mm": 3.7795, "cm": 37.795, "in": 96}.get(m.group(2), 1)


def color_of(v, grads):
    if v is None or v == "none" or v == "transparent":
        return None
    if v.startswith("url("):
        m = re.search(r"#([^)'\"]+)", v)
        return grads.get(m.group(1)) if m else None
    if v == "currentColor":
        return Color("black")
    c = Color(v)
    return c if c.value is not None else None


def shape_path(el):
    a = dict(el.attrib)
    a.pop("transform", None)
    a.pop("style", None)
    inner = "<%s %s/>" % (tag(el), " ".join('%s="%s"' % (k.split("}")[-1], v) for k, v in a.items()))
    from io import StringIO
    s = SVG.parse(StringIO('<svg xmlns="http://www.w3.org/2000/svg">' + inner + "</svg>"), reify=True)
    for e in s.elements():
        if isinstance(e, Shape):
            p = Path(e)
            p.reify()
            return p
    return None


def transformed(path, m):
    p = Path(path) * m
    p.reify()
    return p


def gradient_colors(root_xml):
    grads, hrefs = {}, {}
    for g in root_xml.iter():
        if tag(g) in ("linearGradient", "radialGradient"):
            stops = []
            for s in g.iter(NS + "stop"):
                c = attrs(s).get("stop-color")
                if c:
                    stops.append(Color(c))
            gid = g.get("id")
            href = g.get(XL) or g.get("href")
            if stops:
                grads[gid] = Color(int(sum(c.red for c in stops) / len(stops)), int(sum(c.green for c in stops) / len(stops)),
                                   int(sum(c.blue for c in stops) / len(stops)))
            elif href:
                hrefs[gid] = href.lstrip("#")
    for gid, ref in hrefs.items():
        if ref in grads:
            grads[gid] = grads[ref]
    return grads


class Walker:
    def __init__(self, root):
        self.root = root
        self.ids = {el.get("id"): el for el in root.iter() if el.get("id")}
        self.grads = gradient_colors(root)
        self.items = []  # (clipStack, kind, color, rule/join, strokeWidth, subs)

    def clip_data(self, cid, ctm):
        cp = self.ids.get(cid)
        if cp is None:
            return None
        subs = []
        def rec(el, m):
            for ch in el:
                t = tag(ch)
                mm = m * Matrix(ch.get("transform")) if ch.get("transform") else m
                # svgelements composes as (child then parent): Matrix(child) * parent
                mm = (Matrix(ch.get("transform")) * m) if ch.get("transform") else m
                if t in SHAPES:
                    p = shape_path(ch)
                    if p is not None:
                        for pts, closed in flatten(transformed(p, mm)):
                            subs.append((simplify(pts, True), True))
                elif t == "use":
                    ref = self.ids.get((ch.get(XL) or ch.get("href") or "").lstrip("#"))
                    if ref is not None:
                        um = Matrix("translate(%s %s)" % (length(ch.get("x"), 0), length(ch.get("y"), 0))) * mm
                        rec_one(ref, um)
                elif t == "g":
                    rec(ch, mm)
        def rec_one(el, m):
            wrapper = ET.Element("g")
            wrapper.append(el)
            rec(wrapper, m)
        rec(cp, ctm)
        return encode(subs) if subs else None

    def walk(self, el, ctm, style, clips, opacity):
        t = tag(el)
        if t in SKIP:
            return
        a = attrs(el)
        if a.get("display") == "none":
            return
        st = dict(style)
        for k in INHERIT:
            if k in a:
                st[k] = a[k]
        m = (Matrix(a["transform"]) * ctm) if "transform" in a else ctm
        op = opacity * (float(a["opacity"]) if "opacity" in a else 1.0)
        cl = clips
        if "clip-path" in a and a["clip-path"] != "none":
            mm = re.search(r"#([^)'\"]+)", a["clip-path"])
            if mm:
                d = self.clip_data(mm.group(1), m)
                if d:
                    cl = clips + (d,)
        if t in ("svg", "g", "a", "switch"):
            for ch in el:
                self.walk(ch, m, st, cl, op)
            return
        if t == "use":
            ref = self.ids.get((el.get(XL) or el.get("href") or "").lstrip("#"))
            if ref is not None:
                um = Matrix("translate(%s %s)" % (length(el.get("x"), 0), length(el.get("y"), 0))) * m
                if tag(ref) == "symbol":
                    for ch in ref:
                        self.walk(ch, um, st, cl, op)
                else:
                    self.walk(ref, um, st, cl, op)
            return
        if t not in SHAPES:
            return
        if st.get("visibility") == "hidden":
            return
        p = shape_path(el)
        if p is None:
            return
        tp = transformed(p, m)
        subs = flatten(tp)
        fill = color_of(st.get("fill", "black"), self.grads)
        if t in ("line", "polyline"):
            fill = fill if t == "polyline" else None
        if fill is not None and subs:
            fop = float(st.get("fill-opacity", 1))
            kept = []
            for pts, closed in subs:
                x0, y0, x1, y1 = bbox(pts)
                if (x1 - x0) * (y1 - y0) < MIN_AREA:
                    continue
                kept.append((simplify(pts, True), True))
            if kept:
                self.items.append((cl, "fill", argb(fill, op * fop), st.get("fill-rule", "nonzero"), 0, kept))
        stroke = color_of(st.get("stroke"), self.grads)
        k = math.sqrt(abs(m.a * m.d - m.b * m.c)) or 1.0
        sw_user = length(st.get("stroke-width"), 1.0)
        if stroke is not None and subs:
            sw = sw_user * k
            if sw >= MIN_STROKE:
                kept = []
                for pts, closed in subs:
                    x0, y0, x1, y1 = bbox(pts)
                    if max(x1 - x0, y1 - y0) < 4:
                        continue
                    kept.append((simplify(pts, closed), closed))
                if kept:
                    sop = float(st.get("stroke-opacity", 1))
                    self.items.append((cl, "stroke", argb(stroke, op * sop), st.get("stroke-linejoin", "miter"), sw, kept))
        # markers (used by us, um, bo)
        for which in ("marker-start", "marker-mid", "marker-end"):
            v = st.get(which)
            if not v or v == "none":
                continue
            mk = self.ids.get(re.search(r"#([^)'\"]+)", v).group(1))
            if mk is None:
                continue
            verts = [(seg.end.x, seg.end.y) for seg in p if not isinstance(seg, Close) and seg.end is not None]
            if which == "marker-start":
                verts = verts[:1]
            elif which == "marker-end":
                verts = verts[-1:]
            else:
                verts = verts[1:-1]
            scale = sw_user if mk.get("markerUnits", "strokeWidth") == "strokeWidth" else 1.0
            rx, ry = length(mk.get("refX"), 0), length(mk.get("refY"), 0)
            mst = {kk: vv for kk, vv in st.items() if not kk.startswith("marker")}
            for vx, vy in verts:
                mm = Matrix("translate(%f %f) scale(%f) translate(%f %f)" % (vx, vy, scale, -rx, -ry)) * m
                for ch in mk:
                    self.walk(ch, mm, mst, cl, op)


def convert(code):
    svg_path = os.path.join(SRC, code.lower() + ".svg")
    root = ET.parse(svg_path).getroot()
    w = Walker(root)
    vb = [float(x) for x in re.split(r"[ ,]+", root.get("viewBox", "0 0 640 480").strip())]
    base = Matrix("scale(%f %f) translate(%f %f)" % (640 / vb[2], 480 / vb[3], -vb[0], -vb[1]))
    base = Matrix("translate(%f %f)" % (-vb[0], -vb[1])) * Matrix("scale(%f %f)" % (640 / vb[2], 480 / vb[3]))
    w.walk(root, base, {}, (), 1.0)
    items = w.items

    merged = []
    for it in items:
        if merged:
            last = merged[-1]
            if last[0] == it[0] and last[1] == it[1] == "fill" and last[2] == it[2] and last[3] == it[3]:
                lb = bbox([p for s, _ in last[5] for p in s])
                nb = bbox([p for s, _ in it[5] for p in s])
                overlap = not (nb[0] > lb[2] or nb[2] < lb[0] or nb[1] > lb[3] or nb[3] < lb[1])
                if not overlap or it[3] == "evenodd":
                    merged[-1] = (last[0], last[1], last[2], last[3], last[4], last[5] + it[5])
                    continue
        merged.append(it)

    body = []
    open_stack = ()
    for clips, kind, color, rule, sw, subs in merged:
        d = encode(subs)
        if not d:
            continue
        # clip paths that cover the whole viewport are no-ops
        clips = tuple(c for c in clips if c not in ("M0 0h64v48h-64z", "M0 0v48h64v-48z"))
        common = 0
        while common < min(len(open_stack), len(clips)) and open_stack[common] == clips[common]:
            common += 1
        for _ in range(len(open_stack) - common):
            body.append("</group>")
        for c in clips[common:]:
            body.append('<group><clip-path android:pathData="%s"/>' % c)
        open_stack = clips
        if kind == "fill":
            ft = ' android:fillType="evenOdd"' if rule == "evenodd" else ""
            body.append(f'<path android:fillColor="{color}"{ft} android:pathData="{d}"/>')
        else:
            join = {"round": "round", "bevel": "bevel"}.get(rule, "miter")
            body.append(f'<path android:strokeColor="{color}" android:strokeWidth="{num(max(0.1, sw * SCALE))}" android:strokeLineJoin="{join}" android:pathData="{d}"/>')
    for _ in open_stack:
        body.append("</group>")
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="32dp" android:height="24dp" '
            'android:viewportWidth="64" android:viewportHeight="48">' + "".join(body) + "</vector>\n")


def main():
    codes = [l[1:].strip() for l in gzip.open(ASSET, "rt", encoding="utf-8") if l.startswith("#")]
    if len(sys.argv) > 1:
        codes = [c.upper() for c in sys.argv[1:]]
    os.makedirs(RES, exist_ok=True)
    sizes = []
    for cc in codes:
        global TOL, MIN_AREA
        try:
            # Detailed emblems get coarser passes until they fit the 2KB budget (or the last level).
            for TOL, MIN_AREA in LEVELS:
                xml = convert(cc)
                if len(xml) <= 2048:
                    break
        except Exception as ex:
            print("FAILED", cc, ex, file=sys.stderr)
            raise
        with open(os.path.join(RES, f"flag_{cc.lower()}.xml"), "w", encoding="utf-8") as f:
            f.write(xml)
        sizes.append((len(xml), cc))
    sizes.sort()
    over = [s for s in sizes if s[0] > 2048]
    print(f"{len(sizes)} flags, median {sizes[len(sizes)//2][0]} B, max {sizes[-1]}, over 2KB: {len(over)} {[c for _, c in over]}", file=sys.stderr)
    if len(sys.argv) == 1:
        os.makedirs(os.path.dirname(KT), exist_ok=True)
        with open(KT, "w", encoding="utf-8") as f:
            f.write("// Generated by tools/build_flags.py. Do not edit.\npackage app.elsewhere.ui.flags\n\nimport app.elsewhere.R\n\n")
            f.write("internal fun flagRes(code: String): Int = when (code) {\n")
            for cc in sorted(codes):
                f.write(f'    "{cc}" -> R.drawable.flag_{cc.lower()}\n')
            f.write("    else -> 0\n}\n")


if __name__ == "__main__":
    main()
