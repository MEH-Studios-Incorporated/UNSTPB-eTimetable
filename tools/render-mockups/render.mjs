// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary
//
// Renders the mockups in design/mockups/source to PNG files in
// design/mockups. Usage: npm install && node render.mjs [Name ...]
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const sourceDir = path.join(repo, 'design/mockups/source');
const outDir = path.join(repo, 'design/mockups');
const names = process.argv.slice(2).length
  ? process.argv.slice(2)
  : fs.readdirSync(sourceDir).filter((f) => f.endsWith('.dc.html')).map((f) => f.replace('.dc.html', ''));

const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.png': 'image/png' };
const server = http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  const file = url.endsWith('/support.js') ? path.join(here, 'support.js') : path.join(repo, url);
  if (!file.startsWith(repo) || !fs.existsSync(file)) {
    res.writeHead(404);
    res.end();
    return;
  }
  res.writeHead(200, { 'content-type': types[path.extname(file)] ?? 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const { port } = server.address();

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
page.on('pageerror', (e) => console.error(e.message));
for (const name of names) {
  await page.goto(`http://127.0.0.1:${port}/design/mockups/source/${name}.dc.html`, { waitUntil: 'networkidle' });
  await page.waitForFunction(() => window.__rendered);
  await page.evaluate(() => document.fonts.ready);
  await page.locator('#dc-root > *').first().screenshot({ path: path.join(outDir, `${name}.png`) });
  console.log(`design/mockups/${name}.png`);
}
await browser.close();
server.close();
