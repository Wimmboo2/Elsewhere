// Renders a tile with the prototype's CSS filter in Chromium and compares it with the app's
// per-pixel port (MapFilterTransformation), re-implemented here line for line.
const { chromium } = require('playwright'); const fs = require('fs');
const F = { light: [['sepia', .38], ['saturate', .95], ['contrast', .94], ['brightness', 1.02]], dark: [['sepia', .4], ['saturate', .8], ['brightness', 1.15], ['contrast', .95]] };
function mat(k, v) {
  if (k === 'sepia') { const s = 1 - v; return [0.393 + 0.607 * s, 0.769 - 0.769 * s, 0.189 - 0.189 * s, 0, 0.349 - 0.349 * s, 0.686 + 0.314 * s, 0.168 - 0.168 * s, 0, 0.272 - 0.272 * s, 0.534 - 0.534 * s, 0.131 + 0.869 * s, 0]; }
  if (k === 'saturate') return [0.213 + 0.787 * v, 0.715 - 0.715 * v, 0.072 - 0.072 * v, 0, 0.213 - 0.213 * v, 0.715 + 0.285 * v, 0.072 - 0.072 * v, 0, 0.213 - 0.213 * v, 0.715 - 0.715 * v, 0.072 + 0.928 * v, 0];
  if (k === 'contrast') { const o = 0.5 - 0.5 * v; return [v, 0, 0, o, 0, v, 0, o, 0, 0, v, o]; }
  return [v, 0, 0, 0, 0, v, 0, 0, 0, 0, v, 0];
}
const cl = (x) => Math.min(1, Math.max(0, x));
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const p = await b.newPage();
  for (const t of ['light', 'dark']) {
    const img = 'data:image/jpeg;base64,' + fs.readFileSync(`/tmp/claude-0/tile-${t}.jpg`).toString('base64');
    const css = F[t].map(([k, v]) => `${k}(${v})`).join(' ');
    await p.setContent(`<body style="margin:0"><img id=a src="${img}" style="display:block"><img id=b src="${img}" style="display:block;filter:${css}"></body>`);
    await p.waitForTimeout(300);
    const ss = await p.screenshot({ clip: { x: 0, y: 0, width: 256, height: 512 } });
    fs.writeFileSync('/tmp/claude-0/f.png', ss);
    const { execFileSync } = require('child_process');
    const out = execFileSync('python3', ['-c', `
from PIL import Image
import json,sys
im=Image.open('/tmp/claude-0/f.png').convert('RGB'); px=im.load()
print(json.dumps([[px[x,y] for x in range(0,256,4)] for y in range(0,512,4)]))`]).toString();
    const g = JSON.parse(out); const ms = F[t].map(([k, v]) => mat(k, v));
    let d = 0, n = 0, worst = 0;
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      let [r, gg, bb] = g[y][x].map((c) => c / 255);
      for (const m of ms) { const nr = m[0] * r + m[1] * gg + m[2] * bb + m[3], ng = m[4] * r + m[5] * gg + m[6] * bb + m[7], nb = m[8] * r + m[9] * gg + m[10] * bb + m[11]; r = cl(nr); gg = cl(ng); bb = cl(nb); }
      const ours = [r, gg, bb].map((c) => Math.round(c * 255)), chrome = g[y + 64][x];
      for (let i = 0; i < 3; i++) { const e = Math.abs(ours[i] - chrome[i]); d += e; n++; worst = Math.max(worst, e); }
    }
    console.log(t, 'mean abs error', (d / n).toFixed(2), 'worst', worst);
  }
  await b.close();
})();
