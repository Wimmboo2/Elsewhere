// Renders each original flag-icons SVG next to its generated VectorDrawable (converted back to SVG)
// at 128x96 and reports the mean colour difference. Writes verify/flags.png (contact sheet).
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const RES = path.resolve(__dirname, '../../app/src/main/res/drawable');
const SRC = path.resolve(__dirname, '../cache/package/flags/4x3');
let uid = 0;
function vdToSvg(xml) {
  let s = xml.replace(/<vector[^>]*>/, '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 48" width="128" height="96">').replace('</vector>', '</svg>');
  let n = 0;
  s = s.replace(/<group><clip-path android:pathData="([^"]*)"\/>/g, (_, d) => { n = ++uid; return `<clipPath id="c${n}"><cp d="${d}"/></clipPath><g clip-path="url(#c${n})">`; }).replace(/<\/group>/g, '</g>');
  s = s.replace(/<path ([^>]*)\/>/g, (_, a) => {
    const g = (k) => (a.match(new RegExp('android:' + k + '="([^"]*)"')) || [])[1];
    const col = (c) => { if (!c) return 'none'; if (c.length === 9) { return `rgba(${parseInt(c.slice(3,5),16)},${parseInt(c.slice(5,7),16)},${parseInt(c.slice(7,9),16)},${parseInt(c.slice(1,3),16)/255})`; } return c; };
    return `<path d="${g('pathData')}" fill="${col(g('fillColor'))}" fill-rule="${g('fillType') === 'evenOdd' ? 'evenodd' : 'nonzero'}" stroke="${col(g('strokeColor'))}" stroke-width="${g('strokeWidth') || 0}" stroke-linejoin="${g('strokeLineJoin') || 'miter'}"/>`;
  });
  return s.replace(/<cp /g, '<path ');
}
(async () => {
  const codes = fs.readdirSync(RES).filter((f) => f.startsWith('flag_')).map((f) => f.slice(5, -4));
  const cells = codes.map((c) => {
    const orig = fs.readFileSync(path.join(SRC, c + '.svg'), 'utf8').replace('<svg ', '<svg width="128" height="96" preserveAspectRatio="none" ');
    return `<div class="c" data-c="${c}"><div class="o">${orig}</div><div class="v">${vdToSvg(fs.readFileSync(path.join(RES, 'flag_' + c + '.xml'), 'utf8'))}</div><b>${c}</b></div>`;
  });
  const html = `<style>body{margin:0;display:flex;flex-wrap:wrap;background:#fff;font:10px sans-serif}.c{display:flex;gap:2px;padding:3px;align-items:center}.o,.v{width:128px;height:96px;overflow:hidden}svg{display:block}</style>${cells.join('')}`;
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const p = await b.newPage({ viewport: { width: 1600, height: 1000 } });
  await p.setContent(html);
  const res = await p.evaluate(async () => {
    const out = [];
    for (const cell of document.querySelectorAll('.c')) {
      const px = async (el) => {
        const svg = el.querySelector('svg'); const data = new XMLSerializer().serializeToString(svg);
        const img = new Image(); img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(data))); await img.decode();
        const cv = document.createElement('canvas'); cv.width = 128; cv.height = 96; const x = cv.getContext('2d'); x.fillStyle = '#fff'; x.fillRect(0, 0, 128, 96); x.drawImage(img, 0, 0, 128, 96); return x.getImageData(0, 0, 128, 96).data;
      };
      const a = await px(cell.querySelector('.o')), v = await px(cell.querySelector('.v'));
      let d = 0; for (let i = 0; i < a.length; i += 4) d += Math.abs(a[i] - v[i]) + Math.abs(a[i + 1] - v[i + 1]) + Math.abs(a[i + 2] - v[i + 2]);
      out.push([cell.dataset.c, d / (128 * 96 * 3)]);
    }
    return out;
  });
  res.sort((x, y) => y[1] - x[1]);
  console.log('worst:', res.slice(0, 15).map(([c, d]) => c + ' ' + d.toFixed(1)).join(', '));
  console.log('mean:', (res.reduce((s, r) => s + r[1], 0) / res.length).toFixed(2));
  fs.writeFileSync(path.resolve(__dirname, '../../verify/flag-diff.txt'), res.map(([c, d]) => c + '\t' + d.toFixed(2)).join('\n'));
  const worst = new Set(res.slice(0, 24).map((r) => r[0]));
  await p.evaluate((w) => document.querySelectorAll('.c').forEach((c) => { if (!w.includes(c.dataset.c)) c.remove(); }), [...worst]);
  await p.screenshot({ path: path.resolve(__dirname, '../../verify/flags-worst.png'), fullPage: true });
  await b.close();
})();
