// Database: Postgres (Neon on Vercel, via DATABASE_URL / POSTGRES_URL) or an embedded
// Postgres (PGlite, stored in ./data/pglite) for local development - same SQL for both.
import crypto from 'crypto';
import pg from 'pg';

let ready = null;
let q = null;     // (text, params) => rows
let ex = null;    // (sqlWithManyStatements) => void

export const DB_URL = () => process.env.DATABASE_URL || process.env.POSTGRES_URL || process.env.DB_URL || '';

const SCHEMA = `
CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY, created_at TEXT, updated_at TEXT, role TEXT, full_name TEXT, email TEXT UNIQUE, username TEXT,
  phone TEXT, password_hash TEXT, dob TEXT, tob TEXT, gender TEXT, city TEXT, lat DOUBLE PRECISION, lon DOUBLE PRECISION, tz DOUBLE PRECISION, tz_name TEXT,
  chat_session INTEGER DEFAULT 1, language TEXT DEFAULT 'en');
CREATE INDEX IF NOT EXISTS users_username ON users (lower(username));
CREATE TABLE IF NOT EXISTS profiles (id TEXT PRIMARY KEY, created_at TEXT, updated_at TEXT, user_id TEXT, relation TEXT, full_name TEXT, dob TEXT, tob TEXT,
  gender TEXT, city TEXT, lat DOUBLE PRECISION, lon DOUBLE PRECISION, tz DOUBLE PRECISION, tz_name TEXT, chat_session INTEGER DEFAULT 1);
CREATE INDEX IF NOT EXISTS profiles_user ON profiles (user_id);
CREATE TABLE IF NOT EXISTS reports (id TEXT PRIMARY KEY, created_at TEXT, updated_at TEXT, user_id TEXT, profile_id TEXT, session INTEGER, title TEXT, topic TEXT,
  status TEXT, pages INTEGER, source TEXT, hook_json TEXT, error TEXT, unlocked BOOLEAN DEFAULT FALSE, generated_at TEXT, language TEXT DEFAULT 'en',
  parent_id TEXT, content_json TEXT, translations_json TEXT);
CREATE INDEX IF NOT EXISTS reports_user ON reports (user_id);
CREATE TABLE IF NOT EXISTS report_files (report_id TEXT, kind TEXT, data BYTEA, PRIMARY KEY (report_id, kind));
CREATE TABLE IF NOT EXISTS payments (id TEXT PRIMARY KEY, created_at TEXT, updated_at TEXT, user_id TEXT, purpose TEXT, amount INTEGER, order_id TEXT, status TEXT,
  razorpay_payment_id TEXT, signature TEXT, demo BOOLEAN DEFAULT FALSE, report_id TEXT, preferred_date TEXT, preferred_time TEXT, topic TEXT, paid_at TEXT);
CREATE INDEX IF NOT EXISTS payments_order ON payments (order_id);
CREATE TABLE IF NOT EXISTS consultations (id TEXT PRIMARY KEY, created_at TEXT, updated_at TEXT, user_id TEXT, payment_id TEXT, status TEXT, duration_min INTEGER DEFAULT 30,
  preferred_date TEXT, preferred_time TEXT, topic TEXT, scheduled_at TEXT, meeting_link TEXT, notes TEXT);
CREATE TABLE IF NOT EXISTS notifications (id TEXT PRIMARY KEY, created_at TEXT, updated_at TEXT, channel TEXT, to_addr TEXT, subject TEXT, text TEXT, status TEXT, error TEXT);
CREATE TABLE IF NOT EXISTS chat_messages (id TEXT PRIMARY KEY, created_at TEXT, updated_at TEXT, user_id TEXT, profile_id TEXT, session INTEGER, role TEXT, text TEXT);
CREATE INDEX IF NOT EXISTS chat_messages_profile ON chat_messages (profile_id, session);
CREATE TABLE IF NOT EXISTS chat_sessions (id TEXT PRIMARY KEY, created_at TEXT, updated_at TEXT, user_id TEXT, profile_id TEXT, session INTEGER, topic TEXT, topic_key TEXT,
  stage INTEGER DEFAULT 0, ready_for_report BOOLEAN DEFAULT FALSE, report_id TEXT);
CREATE INDEX IF NOT EXISTS chat_sessions_profile ON chat_sessions (profile_id, session);
CREATE TABLE IF NOT EXISTS memory_facts (id TEXT PRIMARY KEY, created_at TEXT, updated_at TEXT, user_id TEXT, profile_id TEXT, fact TEXT);
CREATE INDEX IF NOT EXISTS memory_profile ON memory_facts (profile_id);
CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT);
CREATE TABLE IF NOT EXISTS password_resets (id TEXT PRIMARY KEY, created_at TEXT, updated_at TEXT, email TEXT, otp_hash TEXT, expires_at BIGINT, attempts INTEGER DEFAULT 0, used BOOLEAN DEFAULT FALSE);
CREATE TABLE IF NOT EXISTS places (id SERIAL PRIMARY KEY, name TEXT, ascii TEXT, district TEXT, state TEXT, country TEXT, cc TEXT, lat DOUBLE PRECISION, lon DOUBLE PRECISION,
  tz TEXT, pop INTEGER, k1 TEXT, k2 TEXT);
CREATE INDEX IF NOT EXISTS places_k1 ON places (k1 text_pattern_ops);
CREATE INDEX IF NOT EXISTS places_k2 ON places (k2 text_pattern_ops);
CREATE INDEX IF NOT EXISTS places_lat ON places (lat);
`;

async function init() {
  const url = DB_URL();
  if (url) {
    const pool = new pg.Pool({ connectionString: url, ssl: /localhost|127\.0\.0\.1/.test(url) ? false : { rejectUnauthorized: false }, max: 3, idleTimeoutMillis: 10000 });
    q = async (t, p) => (await pool.query(t, p)).rows;
    ex = async (s) => { await pool.query(s); };
  } else {
    if (process.env.VERCEL) throw new Error('DATABASE_URL is not set. In Vercel open Storage, connect a Neon Postgres database to this project, then redeploy.');
    const pkg = '@electric-sql/pglite'; // local development only (kept out of the Vercel bundle)
    const { PGlite } = await import(pkg);
    const fs = await import('fs');
    fs.mkdirSync('./data', { recursive: true });
    const lite = new PGlite('./data/pglite');
    q = async (t, p) => (await lite.query(t, p)).rows;
    ex = async (s) => { await lite.exec(s); };
  }
  await ex(SCHEMA);
}

export function connect() {
  if (!ready) ready = init().catch((e) => { ready = null; throw e; });
  return ready;
}

export async function query(text, params = []) {
  await connect();
  return q(text, params);
}

export async function exec(sql) { await connect(); return ex(sql); }

// ---------------------------------------------------------------- object mapping (camelCase <-> snake_case)
const snake = (k) => k.replace(/[A-Z]/g, (c) => '_' + c.toLowerCase());
const camelKey = (k) => k.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
// Columns kept out of API responses
const HIDDEN = new Set(['passwordHash', 'contentJson', 'translationsJson', 'otpHash']);

export function camel(row, keepHidden = false) {
  if (!row) return null;
  const o = {};
  for (const [k, v] of Object.entries(row)) {
    const ck = camelKey(k);
    if (!keepHidden && HIDDEN.has(ck)) continue;
    o[ck === 'toAddr' ? 'to' : ck] = v;
  }
  return o;
}

export const newId = (prefix) => `${prefix}_${crypto.randomBytes(8).toString('hex')}`;
const now = () => new Date().toISOString();

/** Insert an object (camelCase keys) and return it with id/createdAt. */
export async function insert(table, prefix, obj) {
  const rec = { id: obj.id || newId(prefix), createdAt: obj.createdAt || now(), ...obj };
  const keys = Object.keys(rec).filter((k) => rec[k] !== undefined);
  const cols = keys.map((k) => (k === 'to' ? 'to_addr' : snake(k)));
  await query(`INSERT INTO ${table} (${cols.join(',')}) VALUES (${keys.map((_, i) => '$' + (i + 1)).join(',')})`, keys.map((k) => rec[k]));
  return rec;
}

/** Update fields (camelCase) of a row by id. */
export async function update(table, id, patch) {
  const p = { ...patch, updatedAt: now() };
  const keys = Object.keys(p).filter((k) => p[k] !== undefined && k !== 'id');
  await query(`UPDATE ${table} SET ${keys.map((k, i) => `${k === 'to' ? 'to_addr' : snake(k)}=$${i + 1}`).join(',')} WHERE id=$${keys.length + 1}`, [...keys.map((k) => p[k]), id]);
}

export async function one(sql, params = [], keepHidden = false) { const r = await query(sql, params); return r.length ? camel(r[0], keepHidden) : null; }
export async function many(sql, params = [], keepHidden = false) { return (await query(sql, params)).map((r) => camel(r, keepHidden)); }

// ---------------------------------------------------------------- settings
export async function getSetting(key, def = null) { const r = await query('SELECT value FROM settings WHERE key=$1', [key]); return r.length ? r[0].value : def; }
export async function setSetting(key, value) { await query('INSERT INTO settings (key, value) VALUES ($1,$2) ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value', [key, value]); }
export async function deleteSetting(key) { await query('DELETE FROM settings WHERE key=$1', [key]); }
