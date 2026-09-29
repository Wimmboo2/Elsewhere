// Captures every prototype screen/state in light and dark into verify/ref/.
// Usage: node tools/verify/capture-prototype.js   (serves design/project on :8765 itself)
const { chromium } = require('playwright');
const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '../../design/project');
const OUT = path.resolve(__dirname, '../../verify/ref');
fs.mkdirSync(OUT, { recursive: true });

const server = http.createServer((req, res) => {
  const p = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]));
  fs.readFile(p, (err, data) => {
    if (err) { res.writeHead(404); res.end(); return; }
    const type = p.endsWith('.html') ? 'text/html' : p.endsWith('.js') ? 'text/javascript' : p.endsWith('.css') ? 'text/css' : 'application/octet-stream';
    res.writeHead(200, { 'content-type': type }); res.end(data);
  });
}).listen(8765);

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const { execFile } = require('child_process');
const CACHE = path.resolve(__dirname, '../cache');

// The sandbox's TLS proxy is not trusted by Chromium, so external assets are served locally:
// React from npm, the same font files the app bundles, and tiles fetched with curl (which trusts it).
async function routes(page) {
  await page.route('https://unpkg.com/**', (route) => {
    const u = new URL(route.request().url());
    const m = u.pathname.match(/^\/(react|react-dom)@[^/]+\/(.*)$/);
    if (!m) return route.abort();
    route.fulfill({ contentType: 'text/javascript', body: fs.readFileSync(path.join(__dirname, 'node_modules', m[1], m[2])) });
  });
  await page.route('https://fonts.googleapis.com/**', (route) => route.fulfill({
    contentType: 'text/css',
    body: `@font-face{font-family:'Caprasimo';font-weight:400;src:url(/__font/Caprasimo-Regular.ttf)}
@font-face{font-family:'Figtree';font-weight:300 900;src:url(/__font/Figtree-VF.ttf)}`,
  }));
  await page.route('**/__font/*', (route) => route.fulfill({ contentType: 'font/ttf', body: fs.readFileSync(path.join(CACHE, path.basename(new URL(route.request().url()).pathname))) }));
  // Async so Node never blocks while the page animates (a blocked client delays freezes and clicks).
  await page.route('https://server.arcgisonline.com/**', (route) => {
    execFile('curl', ['-sSf', '--max-time', '20', route.request().url()], { encoding: 'buffer', maxBuffer: 1 << 22 }, (err, body) => {
      if (err) route.abort().catch(() => {}); else route.fulfill({ contentType: 'image/jpeg', body, headers: { 'access-control-allow-origin': '*' } }).catch(() => {});
    });
  });
}


// Freezes every running animation/transition at t ms after the trigger.
const freeze = async (page, t) => { await wait(60); await page.evaluate((t) => document.getAnimations().forEach((a) => { a.pause(); a.currentTime = t; }), t); };
const OUTM = path.resolve(__dirname, '../../verify/ref-motion');
fs.mkdirSync(OUTM, { recursive: true });

(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const page = await browser.newPage({ viewport: { width: 1280, height: 1100 }, deviceScaleFactor: 2.625 });
  await routes(page);
  await page.goto('http://localhost:8765/Elsewhere%20Prototype.dc.html', { waitUntil: 'networkidle' });
  await page.waitForSelector('div[data-theme="light"]', { timeout: 60000 });
  await wait(1500);
  const phone = page.locator('div[data-theme="light"], div[data-theme="dark"]').first();
  const side = (label) => page.locator('aside').first().locator('button', { hasText: label }).first();
  const reset = async () => { await page.reload({ waitUntil: 'networkidle' }); await page.waitForSelector('div[data-theme="light"]'); await wait(800); await side('Home').click(); await wait(1500); };
  const snap = async (name) => { await phone.screenshot({ path: path.join(OUTM, name + '.png'), animations: 'allow' }); console.log(name); };
  // Clicks via DOM so no extra event latency sneaks in before freezing.
  const clickIn = (sel, text) => page.evaluate(([sel, text]) => { const el = [...document.querySelectorAll(sel)].find((e) => !text || e.textContent.trim().startsWith(text)); el.click(); }, [sel, text]);

  const cases = [
    ['country-open', 190, async () => clickIn('[data-home-chip]')],
    ['city-open', 190, async () => clickIn('[data-city-title]')],
    ['sheet-open', 180, async () => clickIn('button[aria-label="All favorites and recents"]')],
    ['start-active', 200, async () => clickIn('button[data-main]')],
  ];
  for (const [name, t, act] of cases) { await reset(); await act(); await freeze(page, t); await wait(100); await snap(name + '-' + t); }
  // country -> city (shared axis + flag flight), then city -> home (container return + flights)
  const search = async () => { await phone.locator('input[aria-label="Search countries"]').fill('Portugal'); await wait(600); };
  await reset(); await clickIn('[data-home-chip]'); await wait(1200); await search();
  await clickIn('[data-layer="country"] button[data-stagger]', 'Portugal'); await freeze(page, 170); await wait(100); await snap('country-to-city-170');
  await reset(); await clickIn('[data-home-chip]'); await wait(1200); await search();
  await clickIn('[data-layer="country"] button[data-stagger]', 'Portugal'); await wait(1200);
  await clickIn('[data-layer="city"] [data-stagger]', 'Porto'); await freeze(page, 200);
  console.log(await page.evaluate(() => document.getAnimations().map((a) => (a.effect.target.dataset ? JSON.stringify(a.effect.target.dataset) : a.effect.target.tagName) + ':' + a.playState + ':' + a.currentTime).join('\n'))); await wait(100); await snap('city-to-home-200');
  await reset(); await clickIn('[data-home-chip]'); await wait(1200);
  await clickIn('[data-layer="country"] button[aria-label="Back"]'); await freeze(page, 160); await wait(100); await snap('country-close-160');
  await browser.close(); server.close();
})().catch((e) => { console.error(e); process.exit(1); });
