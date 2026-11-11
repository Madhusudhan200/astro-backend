// Birth-place search: database of every Indian village + world towns (GeoNames, imported once with
// `npm run places:import`), with OpenStreetMap (Photon) fallback. UTC offsets are exact for the birth date.
import { query } from './db.js';

export const norm = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9 ]+/g, ' ').replace(/\s+/g, ' ').trim();

export const label = (name, district, state, country) => [name, district && district !== name ? district : '', state, country].filter((s) => s && String(s).trim()).join(', ');

function toPlace(r) {
  return { name: r.name, district: r.district, state: r.state, country: r.country, cc: r.cc, lat: r.lat, lon: r.lon, tzName: r.tz, pop: Number(r.pop) || 0, label: label(r.name, r.district, r.state, r.country), source: 'local' };
}

export async function searchLocal(queryText, limit = 15) {
  const parts = String(queryText || '').split(',').map(norm).filter(Boolean);
  if (!parts.length || parts[0].length < 2) return [];
  const q = parts[0];
  const params = [q, q + '%'];
  let filter = '';
  parts.slice(1).forEach((f) => { params.push('%' + f + '%'); filter += ` AND lower(coalesce(district,'') || ' ' || coalesce(state,'') || ' ' || coalesce(country,'') || ' ' || coalesce(cc,'')) LIKE $${params.length}`; });
  params.push(limit);
  const rows = await query(
    `SELECT name, district, state, country, cc, lat, lon, tz, pop FROM places
     WHERE (k1 LIKE $2 OR k2 LIKE $2)${filter}
     ORDER BY (k1 = $1 OR coalesce(k2, '') = $1) DESC, (cc = 'IN') DESC, pop DESC, length(name)
     LIMIT $${params.length}`, params);
  return rows.map(toPlace);
}

export async function nearestTz(lat, lon) {
  try {
    const r = await query('SELECT tz FROM places WHERE lat BETWEEN $1 - 1.5 AND $1 + 1.5 ORDER BY (lat - $1) ^ 2 + (lon - $2) ^ 2 LIMIT 1', [lat, lon]);
    if (r.length) return r[0].tz;
  } catch { /* ignore */ }
  const off = Math.round(lon / 15);
  return off === 0 ? 'UTC' : 'Etc/GMT' + (off > 0 ? '-' : '+') + Math.abs(off);
}

export async function searchOnline(q, limit = 8) {
  try {
    const r = await fetch(`https://photon.komoot.io/api/?q=${encodeURIComponent(q)}&limit=${limit}&osm_tag=place`, { headers: { 'User-Agent': 'AstrologyTalk/3.0' }, signal: AbortSignal.timeout(6000) });
    if (!r.ok) return [];
    const data = await r.json();
    const out = [];
    for (const f of data.features || []) {
      const pr = f.properties || {};
      if (!pr.name) continue;
      const [lon, lat] = f.geometry.coordinates;
      const district = pr.county || pr.district || '';
      out.push({ name: pr.name, district, state: pr.state || '', country: pr.country || '', cc: (pr.countrycode || '').toUpperCase(), lat, lon,
        tzName: await nearestTz(lat, lon), pop: 0, type: pr.osm_value, label: label(pr.name, district, pr.state, pr.country), source: 'osm' });
    }
    return out;
  } catch { return []; }
}

export async function search(q) {
  const local = await searchLocal(q, 15);
  if (local.length >= 8) return local;
  const online = await searchOnline(q, 8);
  const near = (a, b) => Math.abs(a.lat - b.lat) < 0.05 && Math.abs(a.lon - b.lon) < 0.05;
  return [...local, ...online.filter((o) => !local.some((l) => near(l, o)))].slice(0, 15);
}

export async function placesCount() {
  try { return Number((await query('SELECT count(*)::int AS n FROM places'))[0].n); } catch { return 0; }
}

// ---------------------------------------------------------------- time zones
export function validTz(tz) {
  if (!tz) return false;
  try { new Intl.DateTimeFormat('en-US', { timeZone: tz }); return true; } catch { return false; }
}
function offsetAt(tzName, utcMs) {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: tzName, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).formatToParts(new Date(utcMs));
  const v = Object.fromEntries(parts.map((p) => [p.type, Number(p.value)]));
  return (Date.UTC(v.year, v.month - 1, v.day, v.hour, v.minute, v.second) - Math.floor(utcMs / 1000) * 1000) / 3600000;
}
/** UTC offset (hours) for a local wall-clock birth time in that zone. */
export function offsetForLocal(tzName, dob, tob) {
  const [y, m, d] = dob.split('-').map(Number);
  const [h, mi, s] = (tob || '12:00:00').split(':').map(Number);
  const local = Date.UTC(y, m - 1, d, h, mi, s || 0);
  let off = offsetAt(tzName, local);
  off = offsetAt(tzName, local - off * 3600000);
  return Math.round(off * 3600) / 3600;
}
