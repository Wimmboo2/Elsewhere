#!/usr/bin/env python3
"""Builds app/src/main/assets/cities.tsv.gz from GeoNames.

Inputs (downloaded by tools/fetch_sources.sh into tools/cache/):
  cities15000.txt       https://download.geonames.org/export/dump/cities15000.zip
  admin1CodesASCII.txt  https://download.geonames.org/export/dump/admin1CodesASCII.txt
GeoNames data is licensed CC BY 4.0 (credited in Settings > About).

Output format (UTF-8, gzip):
  #CC                     starts a country block (ISO 3166-1 alpha-2)
  @region|region|...      admin1 names used by that country, referenced by index
  id<TAB>name<TAB>regionIndex<TAB>lat<TAB>lon<TAB>elevationMeters
Cities are sorted by population, descending. regionIndex -1 means no admin1 name.
Country names are not stored: the app takes English names from java.util.Locale.
"""
import gzip, os, sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "cache")
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "cities.tsv.gz")

admin1 = {}
with open(os.path.join(CACHE, "admin1CodesASCII.txt"), encoding="utf-8") as f:
    for line in f:
        p = line.rstrip("\n").split("\t")
        if len(p) >= 2:
            admin1[p[0]] = p[1]

by_country = defaultdict(list)
with open(os.path.join(CACHE, "cities15000.txt"), encoding="utf-8") as f:
    for line in f:
        p = line.rstrip("\n").split("\t")
        gid, name, lat, lon, cc, a1, pop, elev, dem = p[0], p[1], p[4], p[5], p[8], p[10], p[14], p[15], p[16]
        if not cc or len(cc) != 2:
            continue
        region = admin1.get(f"{cc}.{a1}", "")
        e = elev if elev.strip() else dem
        try:
            e = str(int(float(e)))
            if int(e) < -1000:  # GeoNames uses -9999 for "no data"
                e = "0"
        except ValueError:
            e = "0"
        by_country[cc].append((int(pop or 0), int(gid), name.replace("\t", " "), region, lat, lon, e))

lines = []
total = 0
for cc in sorted(by_country):
    cities = sorted(by_country[cc], key=lambda c: (-c[0], c[2]))
    regions = []
    ridx = {}
    for c in cities:
        if c[3] and c[3] not in ridx:
            ridx[c[3]] = len(regions)
            regions.append(c[3])
    lines.append("#" + cc)
    lines.append("@" + "|".join(regions))
    for pop, gid, name, region, lat, lon, e in cities:
        lines.append(f"{gid}\t{name}\t{ridx.get(region, -1)}\t{lat}\t{lon}\t{e}")
        total += 1

data = ("\n".join(lines) + "\n").encode("utf-8")
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with gzip.GzipFile(OUT, "wb", compresslevel=9, mtime=0) as g:
    g.write(data)
print(f"{len(by_country)} countries, {total} cities, {len(data)} bytes raw, {os.path.getsize(OUT)} bytes gz", file=sys.stderr)
