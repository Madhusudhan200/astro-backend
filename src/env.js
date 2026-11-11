// Settings: values saved by the admin in Admin > Settings > Integrations (database) win over
// environment variables (Vercel > Settings > Environment Variables, or .env locally).
import { query } from './db.js';

export const PREFIX = 'cfg.';
let cache = null;
let cacheAt = 0;

async function overrides() {
  if (cache && Date.now() - cacheAt < 15000) return cache;
  const rows = await query("SELECT key, value FROM settings WHERE key LIKE 'cfg.%'");
  cache = Object.fromEntries(rows.filter((r) => r.value).map((r) => [r.key.slice(PREFIX.length), r.value]));
  cacheAt = Date.now();
  return cache;
}

export function reload() { cache = null; }

function clean(v) {
  if (v == null) return '';
  v = String(v).trim();
  if (v.length >= 2 && ((v[0] === '"' && v.endsWith('"')) || (v[0] === "'" && v.endsWith("'")))) v = v.slice(1, -1);
  return v;
}

/** Async getter (reads admin overrides). */
export async function get(key, def = '') {
  const o = await overrides();
  const v = clean(o[key] ?? process.env[key]);
  return v === '' ? def : v;
}

export async function has(key) { return (await get(key)) !== ''; }

export async function source(key) {
  const o = await overrides();
  if (o[key]) return 'admin';
  return clean(process.env[key]) ? 'env' : '';
}

export const siteName = () => get('SITE_NAME', 'Astrology Talk');
export const siteUrl = () => get('SITE_URL', 'http://localhost:' + (process.env.PORT || 8090));
