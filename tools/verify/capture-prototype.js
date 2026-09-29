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

(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const page = await browser.newPage({ viewport: { width: 1280, height: 1100 }, deviceScaleFactor: 2.625 });
  await routes(page);
  await page.goto('http://localhost:8765/Elsewhere%20Prototype.dc.html', { waitUntil: 'networkidle' });
  await page.waitForSelector('div[data-theme="light"]', { timeout: 60000 });
  await wait(1500);
  const phone = page.locator('div[data-theme="light"], div[data-theme="dark"]').first();
  const side = (label) => page.locator('aside').first().locator('button', { hasText: label }).first();
  const inPhone = (label) => phone.locator('button', { hasText: label }).first();
  const shot = async (name) => { await wait(1300); await phone.screenshot({ path: path.join(OUT, name + '.png') }); console.log(name); };

  for (const theme of ['light', 'dark']) {
    const t = theme;
    await side('Onboarding').click(); await shot(`${t}-onboarding-1`);
    await inPhone('Get started').click(); await shot(`${t}-onboarding-2`);
    await inPhone('Open About phone').click(); await shot(`${t}-onboarding-3`);
    await side('Home').click(); await shot(`${t}-home`);
    await side('Start / Stop').click(); await shot(`${t}-home-active`);
    await side('Start / Stop').click(); await wait(600);
    await side('Location permission').click(); await shot(`${t}-card-location`);
    await side('Location permission').click(); await wait(800);
    await side('Mock location app').click(); await shot(`${t}-card-mock`);
    await side('Mock location app').click(); await wait(800);
    await side('System kills service').click(); await shot(`${t}-card-killed`);
    await side('System kills service').click(); await wait(800);
    await side('Notifications').click(); await shot(`${t}-card-notifications`);
    await side('Notifications').click(); await wait(800);
    await side('Map tiles').click(); await shot(`${t}-home-maploading`);
    await side('Map tiles').click(); await wait(800);
    await side('Quick switch').click(); await shot(`${t}-sheet-favorites`);
    await phone.locator('button', { hasText: 'Recent' }).first().click(); await shot(`${t}-sheet-recent`);
    await side('Home').click(); await wait(500);
    await side('Favorites').click(); await shot(`${t}-home-nofavorites`);
    await side('Quick switch').click(); await shot(`${t}-sheet-favorites-empty`);
    await side('Home').click(); await wait(500);
    await side('Favorites').click(); await wait(300);
    await side('Country picker').click(); await shot(`${t}-country`);
    await side('No results').click(); await shot(`${t}-country-noresults`);
    await side('City picker').click(); await shot(`${t}-city`);
    await phone.locator('input[aria-label="Search cities"]').fill('zzz'); await shot(`${t}-city-noresults`);
    await side('Settings').click(); await shot(`${t}-settings`);
    await side('Home').click(); await wait(400);
    if (theme === 'light') { await side('Theme').click(); await wait(800); }
  }
  await browser.close();
  server.close();
})().catch((e) => { console.error(e); process.exit(1); });
