// Copies the server code into the frontend repo (../frontend/api/_lib) so the website and the API run
// from ONE Vercel project (same address, no backend URL needed). Run after changing anything in src/:
//   npm run sync:frontend
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
const backend = path.join(here, '..');
const frontend = path.resolve(process.argv[2] || path.join(backend, '..', 'frontend'));
const lib = path.join(frontend, 'api', '_lib');

if (!fs.existsSync(path.join(frontend, 'index.html'))) { console.error('Frontend folder not found:', frontend); process.exit(1); }
fs.rmSync(lib, { recursive: true, force: true });
fs.cpSync(path.join(backend, 'src'), lib, { recursive: true });

// Only what the deployed API needs (no local database, no import tools)
const pkg = JSON.parse(fs.readFileSync(path.join(backend, 'package.json'), 'utf8'));
const skip = new Set(['@electric-sql/pglite', 'adm-zip', 'pdfjs-dist']);
const deps = Object.fromEntries(Object.entries(pkg.dependencies).filter(([k]) => !skip.has(k)));
fs.writeFileSync(path.join(frontend, 'package.json'), JSON.stringify({
  name: 'astro-frontend', version: pkg.version, private: true,
  description: 'Astrology Talk website + API (one Vercel project). The API code in api/_lib is copied from the astro-backend repo.',
  type: 'module', engines: pkg.engines, dependencies: deps,
}, null, 2) + '\n');
console.log('Server code copied to', lib);
