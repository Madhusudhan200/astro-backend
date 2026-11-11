// Admin portal API: stats, customers, reports, payments, consultations, integrations, Razorpay dashboard.
import express from 'express';
import { one, many, update, query, setSetting, deleteSetting } from '../db.js';
import { ApiError, requireUser, publicUser } from '../auth.js';
import * as env from '../env.js';
import * as rzp from '../razorpay.js';
import * as notify from '../notify.js';
import * as consult from '../consult.js';
import { h, clean, prices } from './util.js';
import { sendPdf } from './client.js';

const r = express.Router();
r.use(h(async (req, res, next) => { await requireUser(req, 'admin'); next(); }));

const names = async (ids) => {
  const list = [...new Set(ids.filter(Boolean))];
  if (!list.length) return {};
  const rows = await query('SELECT id, full_name FROM users WHERE id = ANY($1)', [list]);
  return Object.fromEntries(rows.map((x) => [x.id, x.full_name]));
};
const today = () => new Date().toISOString().slice(0, 10);

r.get('/stats', h(async () => {
  const paid = await many("SELECT * FROM payments WHERE status='paid' ORDER BY paid_at DESC");
  const n = async (sql, p = []) => Number((await query(sql, p))[0].n);
  const who = await names(paid.slice(0, 8).map((p) => p.userId));
  return {
    users: await n("SELECT count(*)::int AS n FROM users WHERE role='client'"),
    usersToday: await n("SELECT count(*)::int AS n FROM users WHERE role='client' AND created_at LIKE $1", [today() + '%']),
    reportsReady: await n("SELECT count(*)::int AS n FROM reports WHERE status='ready'"),
    reportsSold: paid.filter((p) => p.purpose === 'report').length,
    consultations: await n('SELECT count(*)::int AS n FROM consultations'),
    pendingConsultations: await n("SELECT count(*)::int AS n FROM consultations WHERE status IN ('requested','confirmed')"),
    chats: await n('SELECT count(*)::int AS n FROM chat_sessions'),
    revenue: Math.floor(paid.reduce((a, p) => a + Number(p.amount), 0) / 100),
    recent: paid.slice(0, 8).map((p) => ({ user: who[p.userId] || '?', purpose: p.purpose, amount: p.amount, razorpayPaymentId: p.razorpayPaymentId, paidAt: p.paidAt })),
  };
}));

r.get('/users', h(async () => {
  const users = await many("SELECT * FROM users WHERE role='client' ORDER BY created_at DESC");
  const pc = Object.fromEntries((await query('SELECT user_id, count(*)::int AS n FROM profiles GROUP BY user_id')).map((x) => [x.user_id, Number(x.n)]));
  const rc = Object.fromEntries((await query('SELECT user_id, count(*)::int AS n FROM reports GROUP BY user_id')).map((x) => [x.user_id, Number(x.n)]));
  return users.map((u) => ({ id: u.id, fullName: u.fullName, username: u.username, email: u.email, phone: u.phone, dob: u.dob, tob: u.tob, city: u.city, createdAt: u.createdAt,
    profiles: pc[u.id] || 0, reports: rc[u.id] || 0 }));
}));

r.get('/users/:id', h(async (req) => {
  const u = await one("SELECT * FROM users WHERE id=$1 AND role='client'", [req.params.id]);
  if (!u) throw new ApiError(404, 'Not found');
  const profs = [];
  for (const p of await consult.profiles(u)) {
    const msgs = await many('SELECT role, text, session, created_at FROM chat_messages WHERE profile_id=$1 ORDER BY created_at ASC, id ASC', [p.id]);
    profs.push({ profile: p, chart: consult.chart(p), memory: await consult.memory(p), messages: msgs.map((m) => ({ role: m.role, text: m.text, session: m.session, createdAt: m.createdAt })) });
  }
  return {
    user: publicUser(u), profiles: profs,
    reports: await Promise.all((await many('SELECT * FROM reports WHERE user_id=$1 ORDER BY created_at DESC', [u.id])).map(consult.view)),
    payments: await many('SELECT * FROM payments WHERE user_id=$1 ORDER BY created_at DESC', [u.id]),
    consultations: await many('SELECT * FROM consultations WHERE user_id=$1 ORDER BY created_at DESC', [u.id]),
  };
}));

r.post('/reports/:id/regenerate', h(async (req) => {
  const rep = await consult.getReport(req.params.id);
  if (!rep) throw new ApiError(404, 'Not found');
  const patch = { status: 'generating', error: null };
  if (rep.language && rep.language !== 'en') patch.translationsJson = null; // translate again
  await update('reports', rep.id, patch);
  await query('DELETE FROM report_files WHERE report_id=$1', [rep.id]);
  consult.generate(rep.id);
  return { ok: true };
}));

r.post('/reports/:id/unlock', h(async (req) => {
  const rep = await consult.getReport(req.params.id);
  if (!rep) throw new ApiError(404, 'Not found');
  await update('reports', rep.id, { unlocked: req.body?.unlocked === true });
  return { ok: true };
}));

r.get('/reports/:id/report.pdf', h(async (req, res) => {
  const rep = await consult.getReport(req.params.id);
  if (!rep) throw new ApiError(404, 'Not found');
  await consult.ensureFiles(rep);
  await sendPdf(res, rep.id, req.query.preview != null ? 'preview' : 'full', `report-${rep.id}.pdf`, false, req.query.view != null);
}));

r.get('/payments', h(async () => {
  const all = await many('SELECT * FROM payments ORDER BY created_at DESC');
  const who = await names(all.map((p) => p.userId));
  return all.map((p) => ({ user: who[p.userId] || '?', purpose: p.purpose, amount: p.amount, status: p.status, demo: !!p.demo, orderId: p.orderId, razorpayPaymentId: p.razorpayPaymentId, createdAt: p.createdAt }));
}));

r.get('/consultations', h(async () => {
  const all = await many('SELECT * FROM consultations ORDER BY created_at DESC');
  const users = Object.fromEntries((await many('SELECT id, full_name, email, phone FROM users WHERE id = ANY($1)', [[...new Set(all.map((c) => c.userId))]])).map((u) => [u.id, u]));
  return all.map((c) => {
    const u = users[c.userId] || {};
    return { id: c.id, user: u.fullName, email: u.email, phone: u.phone, status: c.status, preferredDate: c.preferredDate, preferredTime: c.preferredTime, topic: c.topic,
      scheduledAt: c.scheduledAt, meetingLink: c.meetingLink, notes: c.notes };
  });
}));

r.patch('/consultations/:id', h(async (req) => {
  const c = await one('SELECT * FROM consultations WHERE id=$1', [req.params.id]);
  if (!c) throw new ApiError(404, 'Not found');
  const b = req.body || {};
  const patch = {};
  if (b.status != null && ['requested', 'confirmed', 'completed', 'cancelled'].includes(String(b.status))) patch.status = String(b.status);
  if ('scheduledAt' in b) patch.scheduledAt = clean(b.scheduledAt, 30);
  if ('meetingLink' in b) { const l = clean(b.meetingLink, 300); patch.meetingLink = /^https?:\/\//.test(l) ? l : ''; }
  if ('notes' in b) patch.notes = clean(b.notes, 1000);
  if (Object.keys(patch).length) await update('consultations', c.id, patch);
  const next = { ...c, ...patch };
  if (b.notify === true) { const u = await one('SELECT * FROM users WHERE id=$1', [c.userId]); if (u) notify.consultationUpdated(u, next); }
  return next;
}));

// ---------------------------------------------------------------- integrations (keys entered from the admin panel)
/** [key, label, group, secret, hint] */
const FIELDS = [
  ['RAZORPAY_KEY_ID', 'Razorpay Key ID', 'razorpay', false, 'Razorpay Dashboard > Account & Settings > API Keys. Starts with rzp_live_ (real money) or rzp_test_ (testing).'],
  ['RAZORPAY_KEY_SECRET', 'Razorpay Key Secret', 'razorpay', true, 'Shown once when you generate the key.'],
  ['RAZORPAY_WEBHOOK_SECRET', 'Razorpay Webhook Secret (optional)', 'razorpay', true, 'Webhook URL: <your site>/api/razorpay/webhook, events payment.captured and order.paid.'],
  ['SMTP_HOST', 'SMTP host', 'email', false, 'Gmail: smtp.gmail.com'],
  ['SMTP_PORT', 'SMTP port', 'email', false, 'Gmail: 587 (or 465)'],
  ['SMTP_USER', 'SMTP username (email address)', 'email', false, 'Your full Gmail address'],
  ['SMTP_PASS', 'SMTP password / App Password', 'email', true, 'Gmail needs a 16-letter App Password: Google Account > Security > 2-Step Verification > App passwords.'],
  ['MAIL_FROM', 'From name & address (optional)', 'email', false, 'e.g. Astrology Talk <yourname@gmail.com>'],
  ['ADMIN_NOTIFY_EMAIL', 'Admin alert email', 'email', false, 'Where new chats, registrations, reports and payments are announced.'],
  ['WHATSAPP_PROVIDER', 'WhatsApp provider', 'whatsapp', false, 'meta (WhatsApp Cloud API) or twilio'],
  ['META_WA_TOKEN', 'Meta access token', 'whatsapp', true, 'Meta for Developers > your app > WhatsApp > API Setup (use a permanent System User token).'],
  ['META_WA_PHONE_ID', 'Meta phone number ID', 'whatsapp', false, 'WhatsApp > API Setup > Phone number ID'],
  ['META_WA_TEMPLATE', 'Meta approved template name', 'whatsapp', false, 'Needed to message customers first. Template body should contain one variable {{1}}.'],
  ['META_WA_TEMPLATE_LANG', 'Template language code', 'whatsapp', false, 'e.g. en or en_US'],
  ['TWILIO_SID', 'Twilio Account SID', 'whatsapp', false, 'console.twilio.com'],
  ['TWILIO_TOKEN', 'Twilio Auth Token', 'whatsapp', true, ''],
  ['TWILIO_WA_FROM', 'Twilio WhatsApp number', 'whatsapp', false, 'e.g. +14155238886 (sandbox)'],
  ['ADMIN_WHATSAPP', 'Admin WhatsApp number', 'whatsapp', false, 'Your number with country code, e.g. +919876543210'],
  ['DEFAULT_COUNTRY_CODE', 'Default country code', 'whatsapp', false, '91 for India'],
  ['GOOGLE_TRANSLATE_API_KEY', 'Google Cloud Translation API key', 'smart', true, 'For reports in Telugu, Hindi and all other languages. Google Cloud Console > APIs & Services > enable Cloud Translation API > Credentials > API key.'],
  ['ANTHROPIC_API_KEY', 'Smart answers key (optional)', 'smart', true, 'Anthropic API key from console.anthropic.com - gives free-form expert answers to any question.'],
];

async function integrations() {
  const fields = [];
  for (const [key, label, group, secret, hint] of FIELDS) {
    const v = await env.get(key);
    fields.push({ key, label, group, secret, hint, set: !!v, source: await env.source(key), value: secret ? (v ? '••••' + v.slice(-4) : '') : v });
  }
  const live = await rzp.live();
  return { fields, status: {
    razorpay: live ? ((await env.get('RAZORPAY_KEY_ID')).startsWith('rzp_live_') ? 'live' : 'test') : 'demo',
    email: (await env.has('SMTP_HOST')) && (await env.has('SMTP_USER')) && (await env.has('SMTP_PASS')),
    whatsapp: await env.has('WHATSAPP_PROVIDER'),
  } };
}

r.get('/integrations', h(integrations));

r.put('/integrations', h(async (req) => {
  const allowed = new Set(FIELDS.map((f) => f[0]));
  for (const [k, raw] of Object.entries(req.body || {})) {
    if (!allowed.has(k) || raw == null) continue;
    const v = String(raw).trim();
    if (!v || v.startsWith('••••')) continue;                      // unchanged
    if (v === '-') { await deleteSetting(env.PREFIX + k); continue; } // clear
    await setSetting(env.PREFIX + k, v);
  }
  env.reload();
  return integrations();
}));

r.post('/integrations/test-email', h(async (req) => {
  const to = clean(req.body?.to, 120);
  if (!to.includes('@')) throw new ApiError(400, 'Enter an email address to send the test to');
  return { result: await notify.testEmail(to) };
}));

r.post('/integrations/test-whatsapp', h(async (req) => {
  const to = clean(req.body?.to, 20);
  if (to.replace(/\D/g, '').length < 10) throw new ApiError(400, 'Enter a WhatsApp number with country code');
  return { result: await notify.testWhatsApp(to) };
}));

r.post('/integrations/test-razorpay', h(async () => {
  if (!(await rzp.live())) return { result: 'Razorpay keys are not set - payments are in demo mode.' };
  try {
    const o = await rzp.createOrder(100, 'keycheck_' + Date.now(), { purpose: 'key check' });
    return { result: 'ok', detail: `Keys are valid (${(await env.get('RAZORPAY_KEY_ID')).startsWith('rzp_live_') ? 'LIVE - real money' : 'TEST mode - no real money'}). Test order ${o.id} created (not charged).` };
  } catch (e) {
    return { result: 'Failed: ' + e.message + (String(e.message).includes('401') ? '  ->  Key ID or Key Secret is wrong.' : '') };
  }
}));

// ---------------------------------------------------------------- payment gateway dashboard (Razorpay)
r.get('/gateway', h(async () => {
  const live = await rzp.live();
  const keyId = await env.get('RAZORPAY_KEY_ID');
  const m = { mode: live ? (keyId.startsWith('rzp_live_') ? 'live' : 'test') : 'demo', keyId: live ? keyId : '', dashboardUrl: 'https://dashboard.razorpay.com/app/dashboard',
    webhookUrl: (await env.siteUrl()) + '/api/razorpay/webhook' };
  // Totals from this website's own payment records
  const all = await many('SELECT * FROM payments ORDER BY created_at DESC');
  const d0 = today(), month = d0.slice(0, 7);
  const t = { today: 0, month: 0, all: 0, reports: 0, consultations: 0, success: 0, failed: 0, pending: 0 };
  for (const p of all) {
    const amt = Number(p.amount);
    if (p.status === 'paid') {
      t.success++; t.all += amt;
      const d = (p.paidAt || '').slice(0, 10);
      if (d === d0) t.today += amt;
      if (d.startsWith(month)) t.month += amt;
      if (p.purpose === 'report') t.reports += amt; else t.consultations += amt;
    } else if (p.status === 'failed') t.failed++; else t.pending++;
  }
  for (const k of ['today', 'month', 'all', 'reports', 'consultations']) t[k] = Math.floor(t[k] / 100);
  m.totals = t;
  // Live data straight from Razorpay (method: UPI/card/netbanking, VPA, fees, settlements)
  if (live) {
    try {
      m.razorpayPayments = ((await rzp.get('payments?count=25')).items || []).map((p) => ({ id: p.id, amount: p.amount / 100, status: p.status, method: p.method, vpa: p.vpa || '',
        email: p.email || '', contact: p.contact || '', fee: (p.fee || 0) / 100, created: p.created_at * 1000, description: p.description || '' }));
      m.settlements = ((await rzp.get('settlements?count=10')).items || []).map((s) => ({ id: s.id, amount: s.amount / 100, status: s.status, utr: s.utr || '', created: s.created_at * 1000 }));
    } catch (e) { m.razorpayError = e.message; }
  }
  return m;
}));

r.get('/notifications', h(() => many('SELECT * FROM notifications ORDER BY created_at DESC LIMIT 100')));

r.get('/settings', h(prices));

r.put('/settings', h(async (req) => {
  const rep = Math.round(Number(req.body?.report)), con = Math.round(Number(req.body?.consultation));
  if (!(rep >= 1) || !(con >= 1)) throw new ApiError(400, 'Prices must be at least Rs 1');
  await setSetting('reportPrice', String(rep));
  await setSetting('consultPrice', String(con));
  return prices();
}));

export default r;
