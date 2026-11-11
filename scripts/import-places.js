// One-time import of the birth-place database (GeoNames, CC-BY 4.0) into the database.
//   Local (embedded Postgres):          npm run places:import
//   Neon / Vercel Postgres:             set DATABASE_URL=postgresql://...   then   npm run places:import
// Every populated place in India (villages included) + all world towns/cities with population >= 500.
// Optional: pass a ready places.tsv (from the old Java version) to skip the download:
//   node scripts/import-places.js ../backend-java/data/places/places.tsv
import fs from 'fs';
import path from 'path';
import AdmZip from 'adm-zip';
import { query, exec, DB_URL } from '../src/db.js';
import { norm } from '../src/places.js';

const BASE = 'https://download.geonames.org/export/dump/';
const CACHE = path.join('data', 'geonames');

async function download(file) {
  fs.mkdirSync(CACHE, { recursive: true });
  const dest = path.join(CACHE, file);
  if (fs.existsSync(dest) && fs.statSync(dest).size > 0) return dest;
  process.stdout.write(`  downloading ${file} ... `);
  const r = await fetch(BASE + file);
  if (!r.ok) throw new Error(`${file}: HTTP ${r.status}`);
  fs.writeFileSync(dest, Buffer.from(await r.arrayBuffer()));
  console.log((fs.statSync(dest).size / 1e6).toFixed(1) + ' MB');
  return dest;
}
const table = (file, k, v) => Object.fromEntries(fs.readFileSync(file, 'utf8').split('\n').filter((l) => l && !l.startsWith('#')).map((l) => l.split('\t')).filter((f) => f.length > Math.max(k, v)).map((f) => [f[k], f[v]]));
const clean = (s) => String(s || '').replace(/[\t\r\n]/g, ' ').trim();

async function* fromGeoNames(countries) {
  const countryNames = table(await download('countryInfo.txt'), 0, 4);
  const admin1 = table(await download('admin1CodesASCII.txt'), 0, 1);
  const admin2 = table(await download('admin2Codes.txt'), 0, 1);
  const seen = new Set();
  const rows = function* (text, skip) {
    for (const line of text.split('\n')) {
      const f = line.split('\t');
      if (f.length < 18 || f[6] !== 'P' || (skip && skip.has(f[8])) || seen.has(f[0])) continue;
      seen.add(f[0]);
      const cc = f[8];
      yield [clean(f[1]), clean(f[2]), clean(admin2[`${cc}.${f[10]}.${f[11]}`]), clean(admin1[`${cc}.${f[10]}`]), clean(countryNames[cc] || cc), cc, Number(f[4]), Number(f[5]), f[17], Number(f[14]) || 0];
    }
  };
  for (const cc of countries) yield* rows(new AdmZip(await download(cc + '.zip')).readAsText(cc + '.txt', 'utf8'));
  yield* rows(new AdmZip(await download('cities500.zip')).readAsText('cities500.txt', 'utf8'), new Set(countries));
}

function* fromTsv(file) {
  const lines = fs.readFileSync(file, 'utf8').split('\n');
  for (const line of lines.slice(1)) {
    const f = line.split('\t');
    if (f.length < 11) continue;
    yield [f[0], f[1], f[3], f[4], f[5], f[6], Number(f[7]), Number(f[8]), f[9], Number(f[10]) || 0];
  }
}

async function main() {
  console.log('Importing birth places into ' + (DB_URL() ? 'the hosted Postgres database (DATABASE_URL)' : 'the local embedded database (./data/pglite)') + ' ...');
  const src = process.argv[2] && fs.existsSync(process.argv[2]) ? fromTsv(process.argv[2]) : fromGeoNames(['IN']);
  await exec('TRUNCATE places RESTART IDENTITY');
  let batch = [], total = 0;
  const flush = async () => {
    if (!batch.length) return;
    const params = [], values = [];
    batch.forEach((r, i) => {
      const k1 = norm(r[0]), k2 = norm(r[1]);
      params.push(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], k1, k2 === k1 ? null : k2);
      values.push('(' + Array.from({ length: 12 }, (_, j) => '$' + (i * 12 + j + 1)).join(',') + ')');
    });
    await query('INSERT INTO places (name, ascii, district, state, country, cc, lat, lon, tz, pop, k1, k2) VALUES ' + values.join(','), params);
    total += batch.length; batch = [];
    if (total % 50000 < 800) process.stdout.write(`\r  ${total.toLocaleString('en-IN')} places imported`);
  };
  for await (const r of src) { batch.push(r); if (batch.length >= 800) await flush(); }
  await flush();
  console.log(`\nDone: ${total.toLocaleString('en-IN')} places.`);
  process.exit(0);
}
main().catch((e) => { console.error('Import failed:', e.message); process.exit(1); });
