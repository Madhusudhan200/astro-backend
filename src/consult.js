// Profiles (self + family/friends), astrologer chat with long-term memory per profile, and reports.
// Report PDFs are stored in the database (report_files) and rebuilt on demand if missing.
import { query, one, many, insert, update, camel } from './db.js';
import { ApiError } from './auth.js';
import { computeChart } from './kp/engine.js';
import * as astro from './astrologer.js';
import * as ai from './ai.js';
import * as notify from './notify.js';
import * as env from './env.js';
import { lang as findLang, hasWords, serverTranslationAvailable, translate } from './i18n.js';
import { build as buildBlocks, texts as blockTexts, applyTranslations } from './report/builder.js';
import { LABELS, renderFull, renderPreview } from './report/pdf.js';
import { background } from './background.js';

export const RELATIONS = ['Self', 'Mother', 'Father', 'Spouse', 'Son', 'Daughter', 'Brother', 'Sister', 'Friend', 'Other'];

// one-at-a-time work per key (e.g. so a chat greeting is never sent twice)
const locks = new Map();
function locked(key, fn) {
  const prev = locks.get(key) || Promise.resolve();
  const next = prev.catch(() => {}).then(fn);
  const tail = next.catch(() => {});
  locks.set(key, tail);
  tail.then(() => { if (locks.get(key) === tail) locks.delete(key); });
  return next;
}

// ---------------------------------------------------------------- profiles
/** The account holder's own profile (created on first use from their registration details). */
export async function self(u) {
  const p = await one("SELECT * FROM profiles WHERE user_id=$1 AND relation='Self' ORDER BY created_at LIMIT 1", [u.id]);
  if (p) return p;
  return locked('self:' + u.id, async () => {
    const again = await one("SELECT * FROM profiles WHERE user_id=$1 AND relation='Self' ORDER BY created_at LIMIT 1", [u.id]);
    if (again) return again;
    return insert('profiles', 'prf', { userId: u.id, relation: 'Self', fullName: u.fullName, dob: u.dob, tob: u.tob, gender: u.gender, city: u.city,
      lat: u.lat, lon: u.lon, tz: u.tz, tzName: u.tzName, chatSession: 1 });
  });
}

export async function profiles(u) {
  await self(u);
  return many('SELECT * FROM profiles WHERE user_id=$1 ORDER BY created_at ASC', [u.id]);
}

export async function profile(u, profileId) {
  if (!profileId || profileId === 'self') return self(u);
  const p = await one('SELECT * FROM profiles WHERE id=$1 AND user_id=$2', [profileId, u.id]);
  if (!p) throw new ApiError(404, 'Profile not found');
  return p;
}

const chartCache = new Map();
export function chart(p) {
  const key = [p.id, p.dob, p.tob, p.lat, p.lon, p.tz].join('|');
  let c = chartCache.get(key);
  if (!c) {
    c = computeChart({ dob: p.dob, tob: p.tob, lat: Number(p.lat), lon: Number(p.lon), tz: Number(p.tz) });
    if (chartCache.size > 500) chartCache.clear();
    chartCache.set(key, c);
  }
  return c;
}

/** The person a profile describes, shaped like a user for the astrologer and the report. */
export const person = (p) => ({ id: p.id, fullName: p.fullName, gender: p.gender, dob: p.dob, tob: p.tob, city: p.city, lat: Number(p.lat), lon: Number(p.lon), tz: Number(p.tz), tzName: p.tzName });

export async function memory(p) {
  return (await query('SELECT fact FROM memory_facts WHERE profile_id=$1 ORDER BY created_at ASC', [p.id])).map((r) => r.fact);
}

// ---------------------------------------------------------------- chat
async function session(p) {
  const s = await one('SELECT * FROM chat_sessions WHERE profile_id=$1 AND session=$2 ORDER BY created_at LIMIT 1', [p.id, p.chatSession]);
  if (s) return s;
  return insert('chat_sessions', 'ses', { userId: p.userId, profileId: p.id, session: p.chatSession, stage: 0, readyForReport: false });
}

const messages = (p, sess = p.chatSession) => many('SELECT role, text, created_at FROM chat_messages WHERE profile_id=$1 AND session=$2 ORDER BY created_at ASC, id ASC', [p.id, sess]);

async function saveMessage(p, role, text) {
  await insert('chat_messages', 'msg', { userId: p.userId, profileId: p.id, session: p.chatSession, role, text, createdAt: new Date().toISOString() });
  await new Promise((r) => setTimeout(r, 2)); // keeps created_at strictly increasing
}

async function state(p, s) {
  const msgs = await messages(p);
  const mem = await many('SELECT id, fact FROM memory_facts WHERE profile_id=$1 ORDER BY created_at ASC', [p.id]);
  return {
    profile: p, session: p.chatSession,
    messages: msgs.map((m) => ({ role: m.role, text: m.text, createdAt: m.createdAt })),
    memory: mem.map((m) => ({ id: m.id, fact: m.fact })),
    topic: s.topic || '', readyForReport: !!s.readyForReport, reportId: s.reportId || null,
    userMessages: msgs.filter((m) => m.role === 'user').length,
    aiMode: (await ai.enabled()) ? 'ai' : 'rule-based',
  };
}

/** Opens the chat; on a new consultation the astrologer connects, greets and the admin is notified with the client's details. */
export function open(account, p) {
  return locked('chat:' + p.id, async () => {
    const s = await session(p);
    const n = (await query('SELECT count(*)::int AS n FROM chat_messages WHERE profile_id=$1 AND session=$2', [p.id, p.chatSession]))[0].n;
    if (Number(n) === 0) {
      const c = chart(p);
      let text = astro.greeting(person(p), c, await memory(p));
      if (p.relation !== 'Self') text = text.replace('Tell me — why are you consulting', `This consultation is for ${p.fullName} (your ${p.relation.toLowerCase()}). Tell me — why are you consulting`);
      await saveMessage(p, 'assistant', text);
      background(() => notify.chatStarted(account, p, c));
    }
    return state(p, s);
  });
}

export async function send(account, p, text) {
  text = String(text ?? '').trim();
  if (!text) throw new ApiError(400, 'Please type a message');
  if (text.length > 2000) text = text.slice(0, 2000);
  await open(account, p);
  return locked('chat:' + p.id, async () => {
    const s = await session(p);
    await saveMessage(p, 'user', text);
    let history = (await messages(p)).map((m) => ({ role: m.role, text: m.text }));
    if (history.length > 40) history = history.slice(-40);
    const st = { topicKey: s.topicKey || null, topic: s.topic || null, stage: Number(s.stage) || 0, ready: !!s.readyForReport };
    const turn = await ai.chat(person(p), chart(p), await memory(p), history, st);
    await saveMessage(p, 'assistant', turn.reply && turn.reply.trim() ? turn.reply : "I'm here — could you tell me a little more?");
    for (const f of turn.newMemories || []) {
      const fact = String(f || '').trim();
      if (fact.length < 3 || fact.length > 300) continue;
      const dup = await query('SELECT 1 FROM memory_facts WHERE profile_id=$1 AND lower(fact)=lower($2) LIMIT 1', [p.id, fact]);
      if (!dup.length) await insert('memory_facts', 'mem', { userId: p.userId, profileId: p.id, fact });
    }
    const patch = { topicKey: st.topicKey, stage: st.stage, readyForReport: !!(s.readyForReport || turn.readyForReport || st.ready) };
    if (turn.topic && turn.topic.trim()) patch.topic = turn.topic; else if (st.topic) patch.topic = st.topic;
    await update('chat_sessions', s.id, patch);
    return state(p, { ...s, ...patch });
  });
}

/** Start a new consultation (a new reason) for this profile. */
export async function newSession(account, p) {
  const next = Number(p.chatSession || 1) + 1;
  await update('profiles', p.id, { chatSession: next });
  return open(account, { ...p, chatSession: next });
}

export async function forget(u, memoryId) {
  await query('DELETE FROM memory_facts WHERE id=$1 AND user_id=$2', [memoryId, u.id]);
}

// ---------------------------------------------------------------- reports
const getReport = (id) => one('SELECT * FROM reports WHERE id=$1', [id], true);

export async function myReport(u, id) {
  const r = await getReport(id);
  if (!r || r.userId !== u.id) throw new ApiError(404, 'Report not found');
  return r;
}

/** One report per consultation (reason). A new reason = new consultation = new report. */
export async function createReport(account, p, language) {
  const lang = findLang(language) ? language : 'en';
  const s = await session(p);
  if (s.reportId) {
    const existing = await getReport(s.reportId);
    if (existing) return lang === (existing.language || 'en') ? existing : translateReport(account, existing.id, lang);
  }
  const n = (await query("SELECT count(*)::int AS n FROM chat_messages WHERE profile_id=$1 AND session=$2 AND role='user'", [p.id, p.chatSession]))[0].n;
  if (Number(n) === 0) throw new ApiError(400, 'Please chat with the astrologer first — tell them why you are consulting, then generate the report.');
  const topic = s.topic && s.topic.trim() ? s.topic : 'General life reading';
  const r = await insert('reports', 'rep', { userId: account.id, profileId: p.id, session: p.chatSession, language: lang, topic,
    title: topic + ' - ' + p.fullName + (p.relation === 'Self' ? '' : ` (${p.relation})`), status: 'generating', unlocked: false });
  await update('chat_sessions', s.id, { reportId: r.id });
  generate(r.id);
  return r;
}

/** Same report in another language (only the PDF language changes; unlocked if the original is). */
export async function translateReport(account, reportId, language) {
  const src = await getReport(reportId);
  if (!src || src.userId !== account.id) throw new ApiError(404, 'Report not found');
  const root = src.parentId ? (await getReport(src.parentId)) || src : src;
  const l = findLang(language);
  if (!l) throw new ApiError(400, 'Unknown language');
  if (language === (root.language || 'en')) return root;
  const existing = await one('SELECT * FROM reports WHERE parent_id=$1 AND language=$2 LIMIT 1', [root.id, language], true);
  if (existing) return existing;
  const r = await insert('reports', 'rep', { userId: root.userId, profileId: root.profileId, session: root.session, topic: root.topic,
    title: `${root.title} (${l.name})`, language, parentId: root.id, unlocked: !!root.unlocked, status: 'generating' });
  generate(r.id);
  return r;
}

/** English report content + blocks (content computed once and stored). */
async function english(r) {
  const account = await one('SELECT * FROM users WHERE id=$1', [r.userId]);
  const p = r.profileId ? await one('SELECT * FROM profiles WHERE id=$1', [r.profileId]) : await self(account);
  if (!p) throw new Error('The profile of this report was deleted');
  const who = person(p), c = chart(p);
  const parent = r.parentId ? await getReport(r.parentId) : null;
  let content, source;
  if (parent && parent.contentJson) { content = JSON.parse(parent.contentJson); source = parent.source; }
  else if (r.contentJson) { content = JSON.parse(r.contentJson); source = r.source; }
  else {
    const tx = (await messages(p, r.session)).map((m) => ({ role: m.role, text: m.text }));
    const res = await ai.report(who, c, await memory(p), tx, r.topic);
    content = res.content; source = res.source;
    await update('reports', r.id, { contentJson: JSON.stringify(content), source });
    r.contentJson = JSON.stringify(content); r.source = source;
  }
  return { account, profile: p, person: who, chart: c, content, source, blocks: buildBlocks(who, c, content, r.topic) };
}

/** Texts the browser must translate (only those containing words). */
export async function textsToTranslate(r) {
  const e = await english(r);
  return [...new Set([...LABELS, ...blockTexts(e.blocks, e.content.hook)])].filter((t) => t && hasWords(t));
}

/** Translations produced in the visitor's browser by the free website translator. */
export async function acceptTranslations(r, map) {
  if (!map || typeof map !== 'object' || !Object.keys(map).length) throw new ApiError(400, 'No translations received');
  const expected = (await textsToTranslate(r)).length;
  const got = Object.keys(map).length;
  // Safeguard: never build a "translated" PDF that is still mostly English
  if (got < expected * 0.5) throw new ApiError(400, `The translation did not finish (${got} of ${expected} parts). Please keep the page open — it will try again.`);
  const clean = {};
  for (const [k, v] of Object.entries(map)) if (typeof k === 'string' && typeof v === 'string' && v.length < 20000) clean[k] = v;
  await update('reports', r.id, { translationsJson: JSON.stringify(clean), status: 'generating', error: null });
  generate(r.id);
}

const building = new Map();
/** Build in the background (after the response is sent). */
export function generate(reportId) {
  background(async () => {
    const r = await getReport(reportId);
    if (r) await build(r, true);
  });
}

async function hasFiles(id) {
  return Number((await query('SELECT count(*)::int AS n FROM report_files WHERE report_id=$1', [id]))[0].n) >= 2;
}

/** A missing PDF (e.g. a build interrupted on a serverless host) is rebuilt from the stored content before it is served. */
export async function ensureFiles(r) {
  if (r.status !== 'ready' || (await hasFiles(r.id))) return;
  console.log('rebuilding missing PDF files for report', r.id);
  await build(r, false);
}

export async function file(reportId, kind) {
  const rows = await query('SELECT data FROM report_files WHERE report_id=$1 AND kind=$2', [reportId, kind]);
  return rows.length ? Buffer.from(rows[0].data) : null;
}

async function saveFile(reportId, kind, bytes) {
  await query('INSERT INTO report_files (report_id, kind, data) VALUES ($1,$2,$3) ON CONFLICT (report_id, kind) DO UPDATE SET data=EXCLUDED.data', [reportId, kind, Buffer.from(bytes)]);
}

function build(r, announce) {
  if (building.has(r.id)) return building.get(r.id);
  const job = doBuild(r, announce).finally(() => building.delete(r.id));
  building.set(r.id, job);
  return job;
}

async function doBuild(r, announce) {
  try {
    const lang = r.language || 'en';
    if (announce) await update('reports', r.id, { status: 'generating', error: null });
    const e = await english(r);
    let blocks = e.blocks, hook = e.content.hook, tr = null;
    if (lang !== 'en') {
      const fresh = await getReport(r.id);
      if (fresh.translationsJson) tr = JSON.parse(fresh.translationsJson);
      else if (await serverTranslationAvailable()) {
        tr = await translate([...LABELS, ...blockTexts(blocks, hook)], lang);
        await update('reports', r.id, { translationsJson: JSON.stringify(tr) });
      } else { await update('reports', r.id, { status: 'translating' }); return; } // the visitor's browser translates it
      ({ blocks, hook } = applyTranslations(blocks, hook, tr));
    }
    const site = await env.siteName();
    const full = await renderFull({ person: e.person, chart: e.chart, hook, blocks, site, language: lang, labels: tr });
    const preview = await renderPreview({ person: e.person, chart: e.chart, hook, site, language: lang, labels: tr, pages: full.pages, seed: r.id });
    await saveFile(r.id, 'full', full.pdf);
    await saveFile(r.id, 'preview', preview);
    // Keep the free database small: PDFs of older reports are dropped and rebuilt on demand from the stored text
    const cutoff = new Date(Date.now() - 14 * 86400000).toISOString();
    await query('DELETE FROM report_files WHERE report_id IN (SELECT id FROM reports WHERE coalesce(generated_at, created_at) < $1 AND id <> $2)', [cutoff, r.id]).catch(() => {});
    const patch = { status: 'ready', pages: full.pages, source: e.source, hookJson: JSON.stringify(hook), error: null };
    if (announce) patch.generatedAt = new Date().toISOString();
    await update('reports', r.id, patch);
    if (announce) await notify.reportReady(e.account, { ...r, ...patch });
    console.log(`report ${r.id} ready: ${full.pages} pages (${e.source})`);
  } catch (err) {
    console.error('report', r.id, 'failed', err);
    await update('reports', r.id, { status: 'failed', error: String(err && err.message ? err.message : err).slice(0, 500) }).catch(() => {});
  }
}

/** Report as JSON for the portals (with person, reason and dates). */
export async function view(r) {
  const l = findLang(r.language || 'en');
  const p = r.profileId ? await one('SELECT full_name, relation FROM profiles WHERE id=$1', [r.profileId]) : null;
  let hook = null;
  try { hook = r.hookJson ? JSON.parse(r.hookJson) : null; } catch { hook = null; }
  return {
    id: r.id, title: r.title, topic: r.topic, status: r.status, pages: r.pages, unlocked: !!r.unlocked, source: r.source, error: r.error,
    createdAt: r.createdAt, generatedAt: r.generatedAt, profileId: r.profileId,
    language: l ? l.code : 'en', languageName: l ? l.name : 'English', parentId: r.parentId,
    person: p ? p.fullName : null, relation: p ? p.relation : 'Self', hook,
  };
}

export { getReport, camel };
