#!/usr/bin/env bash
# Downloads every third-party input the generators need into tools/cache/ (git-ignored).
#   GeoNames cities15000 + admin1 (CC BY 4.0), flag-icons (MIT), Caprasimo + Figtree (OFL).
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p cache && cd cache
curl -sSLO https://download.geonames.org/export/dump/cities15000.zip
curl -sSLO https://download.geonames.org/export/dump/admin1CodesASCII.txt
unzip -o -q cities15000.zip
npm pack flag-icons@7.5.0 --silent >/dev/null && tar xzf flag-icons-7.5.0.tgz
curl -sSLo Caprasimo-Regular.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/caprasimo/Caprasimo-Regular.ttf"
curl -sSLo Figtree-VF.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/figtree/Figtree%5Bwght%5D.ttf"
# Static Figtree instances (pip install fonttools)
for w in 400:regular 600:semibold 700:bold; do
  fonttools varLib.instancer Figtree-VF.ttf wght=${w%%:*} -o figtree_${w##*:}.ttf --static
done
cp Caprasimo-Regular.ttf ../../app/src/main/res/font/caprasimo_regular.ttf
cp figtree_regular.ttf figtree_semibold.ttf figtree_bold.ttf ../../app/src/main/res/font/
echo "Now run: python3 tools/build_cities.py && python3 tools/build_flags.py"
