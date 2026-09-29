const { chromium } = require('playwright');
const http = require('http'); const fs = require('fs'); const path = require('path');
const ROOT = path.resolve(__dirname, '../../design/project');
const server = http.createServer((req, res) => { const p = path.join(ROOT, decodeURIComponent(req.url.split('?')[0])); fs.readFile(p, (e, d) => { if (e) { res.writeHead(404); res.end(); return; } res.writeHead(200, {'content-type': p.endsWith('.html') ? 'text/html' : p.endsWith('.css') ? 'text/css' : 'text/javascript'}); res.end(d); }); }).listen(8765);
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const p = await b.newPage({ viewport: { width: 1280, height: 1100 } });
  p.on('console', m => console.log('console:', m.type(), m.text().slice(0,200)));
  p.on('pageerror', e => console.log('pageerror:', e.message.slice(0,300)));
  p.on('requestfailed', r => console.log('reqfail:', r.url().slice(0,120), r.failure().errorText));
  await p.goto('http://localhost:8765/Elsewhere%20Prototype.dc.html', { waitUntil: 'networkidle' });
  await new Promise(r => setTimeout(r, 3000));
  await p.screenshot({ path: '/tmp/claude-0/probe.png' });
  console.log((await p.evaluate(() => document.body.innerHTML)).slice(0, 1500));
  const n = await p.evaluate(() => { const all = []; const walk = (r) => { r.querySelectorAll('*').forEach(e => { if (e.shadowRoot) { all.push(e.tagName); walk(e.shadowRoot); } }); }; walk(document); return all; });
  console.log('shadow hosts', n.slice(0,20));
  await b.close(); server.close();
})();
