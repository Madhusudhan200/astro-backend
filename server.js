// Local server: API + the website from ../frontend on http://localhost:8090
// (on Vercel the API runs from api/index.js and the website is the separate frontend project).
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import express from 'express';

// .env (KEY=value lines) for local development
const here = path.dirname(fileURLToPath(import.meta.url));
const envFile = path.join(here, '.env');
if (fs.existsSync(envFile)) {
  for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].replace(/^(['"])(.*)\1$/, '$2');
  }
}

const { createApp, init } = await import('./src/app.js');
const app = createApp();
const site = process.env.FRONTEND_DIR || path.join(here, '..', 'frontend');
if (fs.existsSync(site)) app.use(express.static(site, { extensions: ['html'] }));

const port = Number(process.env.PORT || 8090);
app.listen(port, async () => {
  try { await init(); } catch (e) { console.error('database start failed:', e); }
  const { placesCount } = await import('./src/places.js');
  const n = await placesCount();
  console.log(`\n  Astrology Talk running at http://localhost:${port}\n  Birth places in database: ${n.toLocaleString('en-IN')}${n ? '' : '  (run: npm run places:import)'}\n`);
});
