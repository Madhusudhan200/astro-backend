// Public API: config, languages, places, registration, login, password reset, Razorpay webhook.
import crypto from 'crypto';
import express from 'express';
import { one, many, insert, update, query } from '../db.js';
import { ApiError, hash, verify, login, logout, requireUser, publicUser } from '../auth.js';
import * as env from '../env.js';
import * as places from '../places.js';
import * as rzp from '../razorpay.js';
import * as notify from '../notify.js';
import * as consult from '../consult.js';
import { LANGS } from '../i18n.js';
import { h, clean, birth, prices, EMAIL_RE } from './util.js';
import { onWebhook } from './pay.js';

const r = express.Router();

r.get('/health', h(async () => ({ ok: true, places: await places.placesCount() })));

r.get('/config', h(async () => {
  const live = await rzp.live();
  return { siteName: await env.siteName(), razorpayKey: live ? await rzp.keyId() : null, demoPayments: !live, prices: await prices() };
}));

/** All languages: Indian languages first, then world languages. */
r.get('/languages', (req, res) => res.json(LANGS));

r.get('/places', h(async (req) => {
  const q = clean(req.query.q, 80);
  return q.length < 2 ? [] : places.search(q);
}));

r.get('/tz', h(async (req) => {
  const { tzName, dob, tob } = req.query;
  if (!places.validTz(tzName) || !/^\d{4}-\d{2}-\d{2}$/.test(String(dob || ''))) throw new ApiError(400, 'Invalid');
  return { tz: places.offsetForLocal(tzName, dob, /^\d{2}:\d{2}(:\d{2})?$/.test(String(tob || '')) ? tob : '12:00:00') };
}));

function genPassword() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';
  let p = '';
  for (let i = 0; i < 8; i++) p += chars[crypto.randomInt(chars.length)];
  return p + '@' + (10 + crypto.randomInt(90));
}

async function genUsername(fullName) {
  let base = String(fullName).split(/\s+/)[0].toLowerCase().replace(/[^a-z]/g, '') || 'user';
  base = base.slice(0, 10);
  for (;;) {
    const u = base + (1000 + crypto.randomInt(9000));
    if (!(await query('SELECT 1 FROM users WHERE lower(username)=$1', [u])).length) return u;
  }
}

r.post('/register', h(async (req, res) => {
  const b = req.body || {};
  const fullName = clean(b.fullName, 80), email = clean(b.email, 120).toLowerCase(), phone = clean(b.phone, 20);
  if (!fullName || !EMAIL_RE.test(email)) throw new ApiError(400, 'Please enter your full name and a valid email.');
  if (phone && !/^\+?[\d\s-]{10,16}$/.test(phone)) throw new ApiError(400, 'Please enter a valid WhatsApp number.');
  const bd = await birth(b);
  if (await one('SELECT id FROM users WHERE email=$1', [email])) throw new ApiError(409, 'This email is already registered. Please login.');
  const password = genPassword();
  const u = await insert('users', 'usr', { role: 'client', fullName, email, phone, gender: clean(b.gender, 10), ...bd, username: await genUsername(fullName),
    passwordHash: hash(password), chatSession: 1, language: 'en' });
  await consult.self(u);
  login(res, u);
  await notify.registered(u, password);
  return { user: publicUser(u), credentials: { username: u.username, email, password } };
}));

r.post('/login', h(async (req, res) => {
  const b = req.body || {};
  const id = clean(b.login, 120).toLowerCase(), pw = b.password == null ? '' : String(b.password);
  if (!id || !pw) throw new ApiError(400, 'Please enter your email / login ID and password');
  // Email first, then login ID
  const candidates = await many('SELECT * FROM users WHERE email=$1 OR lower(username)=$1 ORDER BY (email=$1) DESC, created_at', [id], true);
  const u = candidates.find((x) => verify(pw, x.passwordHash));
  if (!u) throw new ApiError(401, candidates.length ? 'Wrong password. Use “Forgot password?” to reset it.' : 'No account found with this email or login ID');
  login(res, u);
  return { user: publicUser(u) };
}));

r.post('/logout', (req, res) => { logout(res); res.json({ ok: true }); });

r.get('/me', h(async (req) => ({ user: publicUser(await requireUser(req)) })));

r.post('/change-password', h(async (req) => {
  const u = await requireUser(req);
  const b = req.body || {};
  if (!verify(String(b.current ?? ''), u.passwordHash)) throw new ApiError(400, 'Current password is wrong');
  const next = String(b.next ?? '');
  if (next.length < 8) throw new ApiError(400, 'New password must be at least 8 characters');
  await update('users', u.id, { passwordHash: hash(next) });
  return { ok: true };
}));

// ---------------------------------------------------------------- forgot password (email OTP)
const latestReset = (email) => one('SELECT * FROM password_resets WHERE email=$1 AND used=FALSE ORDER BY created_at DESC LIMIT 1', [email], true);

r.post('/forgot-password', h(async (req) => {
  const email = clean(req.body?.email, 120).toLowerCase();
  if (!EMAIL_RE.test(email)) throw new ApiError(400, 'Please enter your registered email address.');
  const u = await one('SELECT * FROM users WHERE email=$1', [email]);
  if (!u) throw new ApiError(404, 'No account is registered with this email.');
  const prev = await latestReset(email);
  if (prev && prev.createdAt && Date.parse(prev.createdAt) > Date.now() - 60000)
    throw new ApiError(429, 'A code was sent less than a minute ago. Please check your email (and spam folder) or wait a moment.');
  if (prev) await update('password_resets', prev.id, { used: true });
  const otp = String(crypto.randomInt(1000000)).padStart(6, '0');
  await insert('password_resets', 'pwr', { email, otpHash: hash(otp), expiresAt: Date.now() + 10 * 60000, attempts: 0, used: false });
  const sent = await notify.passwordOtp(u, otp);
  const masked = email.replace(/^(.).*(.@)/, '$1***$2');
  return { ok: true, sentTo: masked, emailDelivered: sent,
    message: sent ? `We sent a 6-digit code to ${masked}. It is valid for 10 minutes.` : 'Email delivery is not set up yet on this site, so the code could not be emailed. Please contact support.' };
}));

r.post('/reset-password', h(async (req, res) => {
  const b = req.body || {};
  const email = clean(b.email, 120).toLowerCase(), otp = clean(b.otp, 6), pw = String(b.password ?? '');
  const rs = await latestReset(email);
  if (!rs) throw new ApiError(400, 'Please request a new code.');
  if (Date.now() > Number(rs.expiresAt)) { await update('password_resets', rs.id, { used: true }); throw new ApiError(400, 'This code has expired. Please request a new one.'); }
  if (rs.attempts >= 5) { await update('password_resets', rs.id, { used: true }); throw new ApiError(400, 'Too many wrong attempts. Please request a new code.'); }
  if (!verify(otp, rs.otpHash)) { await update('password_resets', rs.id, { attempts: rs.attempts + 1 }); throw new ApiError(400, `Wrong code. ${5 - rs.attempts - 1} attempts left.`); }
  if (pw.length < 8) throw new ApiError(400, 'New password must be at least 8 characters.');
  const u = await one('SELECT * FROM users WHERE email=$1', [email]);
  if (!u) throw new ApiError(404, 'Account not found.');
  await update('users', u.id, { passwordHash: hash(pw) });
  await update('password_resets', rs.id, { used: true });
  login(res, u);
  return { ok: true, user: publicUser(u) };
}));

r.post('/razorpay/webhook', h(async (req) => {
  const raw = Buffer.isBuffer(req.body) ? req.body : Buffer.from(JSON.stringify(req.body || {}));
  if (!(await rzp.verifyWebhook(raw, req.get('X-Razorpay-Signature')))) throw new ApiError(400, 'Bad signature');
  await onWebhook(raw);
  return { ok: true };
}));

// ---------------------------------------------------------------- admin account (created / updated from ADMIN_EMAIL + ADMIN_PASSWORD)
export async function seedAdmin() {
  const email = (await env.get('ADMIN_EMAIL', 'admin@astrologytalk.local')).toLowerCase();
  const password = await env.get('ADMIN_PASSWORD', 'Admin@123');
  const fingerprint = crypto.createHash('sha256').update(email + '|' + password).digest('hex').slice(0, 16);
  let a = await one("SELECT * FROM users WHERE role='admin' AND email=$1", [email], true);
  if (!a) {
    // Another account may already use this email (e.g. a client): don't clash, just log it
    if (await one('SELECT id FROM users WHERE email=$1', [email])) { console.warn('ADMIN_EMAIL belongs to a client account; choose another admin email'); return; }
    a = await insert('users', 'usr', { role: 'admin', fullName: 'Administrator', email, passwordHash: hash(password), username: 'admin' });
    console.log('created admin account', email);
  } else {
    // If ADMIN_PASSWORD was changed (e.g. in Vercel > Environment Variables), apply the new value once.
    // A password changed later in the admin portal stays until ADMIN_PASSWORD changes again.
    const applied = (await query("SELECT value FROM settings WHERE key='adminPasswordFromEnv'"))[0]?.value || '';
    if (applied !== fingerprint && !verify(password, a.passwordHash)) { await update('users', a.id, { passwordHash: hash(password) }); console.log('admin password updated from ADMIN_PASSWORD'); }
  }
  // The main admin owns the login ID "admin"; older admin accounts get their own IDs
  for (const o of await many("SELECT id, email, username FROM users WHERE role='admin' AND email<>$1 AND lower(username)='admin'", [email])) {
    const [user, domain = ''] = o.email.split('@');
    await update('users', o.id, { username: `${user}-${domain.split('.')[0] || o.id.slice(-4)}` });
  }
  if (a.username !== 'admin') await update('users', a.id, { username: 'admin' });
  await query("INSERT INTO settings (key, value) VALUES ('adminPasswordFromEnv', $1) ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value", [fingerprint]);
}

export default r;
