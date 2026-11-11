// Client portal: profiles (self + family/friends), astrologer chat, chart and reports.
import express from 'express';
import { one, many, insert, update, query } from '../db.js';
import { ApiError, requireUser, publicUser } from '../auth.js';
import * as consult from '../consult.js';
import { lang as findLang } from '../i18n.js';
import { h, clean, birth, prices } from './util.js';

const r = express.Router();
const me = (req) => requireUser(req, 'client');

/** Builds interrupted on a serverless host (instance stopped) are restarted when the portal polls. */
const STALE_MS = 6 * 60000;
function restartStale(reports) {
  for (const x of reports) {
    if (x.status !== 'generating') continue;
    const at = Date.parse(x.updatedAt || x.createdAt || 0);
    if (Date.now() - at > STALE_MS) { update('reports', x.id, { error: null }).catch(() => {}); consult.generate(x.id); }
  }
}

r.get('/dashboard', h(async (req) => {
  const u = await me(req);
  const reports = await many('SELECT * FROM reports WHERE user_id=$1 ORDER BY created_at DESC', [u.id]);
  restartStale(reports);
  return {
    user: publicUser(u),
    profiles: await consult.profiles(u),
    relations: consult.RELATIONS.slice(1),
    reports: await Promise.all(reports.map(consult.view)),
    consultations: await many('SELECT * FROM consultations WHERE user_id=$1 ORDER BY created_at DESC', [u.id]),
    payments: await many("SELECT * FROM payments WHERE user_id=$1 AND status='paid' ORDER BY created_at DESC", [u.id]),
    prices: await prices(),
  };
}));

// ---------------------------------------------------------------- profiles
r.get('/profiles', h(async (req) => consult.profiles(await me(req))));

r.post('/profiles', h(async (req) => {
  const u = await me(req);
  const b = req.body || {};
  const relation = clean(b.relation, 20);
  if (!consult.RELATIONS.includes(relation) || relation === 'Self') throw new ApiError(400, 'Choose who this profile is for (mother, father, spouse, son, daughter, friend...)');
  const name = clean(b.fullName, 80);
  if (!name) throw new ApiError(400, "Please enter the person's full name");
  if (Number((await query('SELECT count(*)::int AS n FROM profiles WHERE user_id=$1', [u.id]))[0].n) >= 20) throw new ApiError(400, 'You can add up to 20 profiles');
  const bd = await birth(b);
  return insert('profiles', 'prf', { userId: u.id, relation, fullName: name, gender: clean(b.gender, 10), ...bd, chatSession: 1 });
}));

r.delete('/profiles/:id', h(async (req) => {
  const p = await consult.profile(await me(req), req.params.id);
  if (p.relation === 'Self') throw new ApiError(400, 'Your own profile cannot be deleted');
  await query('DELETE FROM profiles WHERE id=$1', [p.id]);
  return { ok: true };
}));

// ---------------------------------------------------------------- chart
r.get('/chart', h(async (req) => consult.chart(await consult.profile(await me(req), req.query.profileId))));

// ---------------------------------------------------------------- chat
r.get('/chat', h(async (req) => { const u = await me(req); return consult.open(u, await consult.profile(u, req.query.profileId)); }));
r.post('/chat', h(async (req) => { const u = await me(req); return consult.send(u, await consult.profile(u, req.body?.profileId), req.body?.message); }));
r.post('/chat/new', h(async (req) => { const u = await me(req); return consult.newSession(u, await consult.profile(u, req.body?.profileId)); }));
r.delete('/memory/:id', h(async (req) => { await consult.forget(await me(req), req.params.id); return { ok: true }; }));

// ---------------------------------------------------------------- reports
r.post('/reports', h(async (req) => {
  const u = await me(req);
  return consult.view(await consult.createReport(u, await consult.profile(u, req.body?.profileId), req.body?.language || 'en'));
}));

/** Get an existing report in another language (PDF only). */
r.post('/reports/:id/translate', h(async (req) => consult.view(await consult.translateReport(await me(req), req.params.id, req.body?.language))));

/** English texts of a report that is waiting to be translated in the browser. */
r.get('/reports/:id/texts', h(async (req) => {
  const rep = await consult.myReport(await me(req), req.params.id);
  if (rep.status !== 'translating') throw new ApiError(400, 'This report is not waiting for translation');
  return { language: rep.language, texts: await consult.textsToTranslate(rep) };
}));

r.post('/reports/:id/translations', h(async (req) => {
  const rep = await consult.myReport(await me(req), req.params.id);
  await consult.acceptTranslations(rep, req.body?.translations);
  return consult.view({ ...rep, status: 'generating' });
}));

/** Website language chosen in My Profile. */
r.post('/language', h(async (req) => {
  const u = await me(req);
  const code = req.body?.language;
  if (!findLang(code)) throw new ApiError(400, 'Unknown language');
  await update('users', u.id, { language: code });
  return { ok: true, language: code };
}));

r.get('/reports/:id', h(async (req) => consult.view(await consult.myReport(await me(req), req.params.id))));

r.post('/reports/:id/retry', h(async (req) => {
  const rep = await consult.myReport(await me(req), req.params.id);
  if (rep.status === 'failed' || rep.status === 'generating') {
    if (rep.language && rep.language !== 'en') await update('reports', rep.id, { translationsJson: null });
    await update('reports', rep.id, { status: 'generating', error: null });
    consult.generate(rep.id);
  }
  return { ok: true };
}));

/** Streams a stored PDF. Served as octet-stream for the in-page viewer so download managers don't hijack it. */
export async function sendPdf(res, reportId, kind, name, download, viewer) {
  const data = await consult.file(reportId, kind);
  if (!data) throw new ApiError(404, 'Report is still being prepared');
  res.set('Content-Type', viewer ? 'application/octet-stream' : 'application/pdf');
  res.set('Content-Disposition', `${download ? 'attachment' : 'inline'}; filename="${name}"`);
  res.set('Cache-Control', 'private, max-age=300');
  res.send(data);
}

r.get('/reports/:id/preview.pdf', h(async (req, res) => {
  const rep = await consult.myReport(await me(req), req.params.id);
  await consult.ensureFiles(rep);
  await sendPdf(res, rep.id, 'preview', 'kp-report-preview.pdf', false, req.query.view != null);
}));

r.get('/reports/:id/full.pdf', h(async (req, res) => {
  const rep = await consult.myReport(await me(req), req.params.id);
  if (!rep.unlocked) throw new ApiError(402, 'Please unlock the full report');
  await consult.ensureFiles(rep);
  await sendPdf(res, rep.id, 'full', `kp-horoscope-${rep.id}.pdf`, req.query.download != null, req.query.view != null);
}));

export default r;
