// Email (SMTP via nodemailer) + WhatsApp (Meta Cloud API or Twilio).
// Without credentials messages are only stored in the notification log.
import nodemailer from 'nodemailer';
import * as env from './env.js';
import { insert } from './db.js';
import { background } from './background.js';

const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const trimTo = (s, n = 900) => (s == null ? null : String(s).length > n ? String(s).slice(0, n) : String(s));
const log = (rec) => insert('notifications', 'not', rec).catch(() => {});

async function layout(title, body) {
  const site = esc(await env.siteName());
  return `<!doctype html><html><body style="margin:0;background:#fff7ed;font-family:Segoe UI,Arial,sans-serif;color:#1f1300">
  <div style="max-width:560px;margin:24px auto;background:#fff;border-radius:16px;overflow:hidden;border:1px solid #fed7aa">
  <div style="background:linear-gradient(135deg,#ea580c,#f59e0b);padding:24px 28px;color:#fff"><div style="font-size:13px;letter-spacing:2px;text-transform:uppercase">${site}</div>
  <div style="font-size:22px;font-weight:700;margin-top:6px">${title}</div></div><div style="padding:24px 28px;font-size:15px;line-height:1.6">${body}</div>
  <div style="padding:14px 28px;background:#fff7ed;font-size:12px;color:#9a3412">KP Horoscope - Krishnamurti Paddhati</div></div></body></html>`;
}

export function rootMessage(e) {
  let m = (e && (e.response || e.message)) || String(e);
  if (/535|auth/i.test(m)) m += '  ->  Wrong email/password. For Gmail you must use a 16-letter App Password (Google Account > Security > 2-Step Verification > App passwords), not your normal password.';
  return m;
}

async function transporter() {
  const host = await env.get('SMTP_HOST'), user = await env.get('SMTP_USER'), pass = (await env.get('SMTP_PASS')).replace(/ /g, '');
  if (!host || !user || !pass) return null;
  const port = Number(await env.get('SMTP_PORT', '587'));
  return nodemailer.createTransport({ host, port, secure: (await env.get('SMTP_SECURE')) === 'true' || port === 465, auth: { user, pass }, connectionTimeout: 15000, socketTimeout: 15000 });
}

/** Sends now; returns false if email is not configured; throws with the provider's error. */
export async function sendEmailNow(to, subject, title, bodyHtml) {
  const t = await transporter();
  if (!t) return false;
  const user = await env.get('SMTP_USER'), site = await env.siteName();
  let from = await env.get('MAIL_FROM', user);
  if (!from.includes('@')) from = `${site} <${user}>`;
  if (!from.includes('<')) from = `${site} <${from}>`;
  await t.sendMail({ from, to, subject, html: await layout(title, bodyHtml) });
  return true;
}

export function email(to, subject, title, bodyHtml) {
  return background(async () => {
    const rec = { channel: 'email', to, subject, status: 'logged' };
    try { if (await sendEmailNow(to, subject, title, bodyHtml)) rec.status = 'sent'; }
    catch (e) { rec.status = 'failed'; rec.error = trimTo(rootMessage(e)); console.warn('email failed:', rec.error); }
    await log(rec);
  });
}

async function phone(p) {
  let d = String(p || '').replace(/[^\d+]/g, '');
  if (!d) return '';
  if (d.startsWith('+')) return d.slice(1);
  if (d.length === 10) return (await env.get('DEFAULT_COUNTRY_CODE', '91')) + d;
  return d.replace(/^0+/, '');
}

const templateText = (t) => { t = String(t).replace(/\s*\n+\s*/g, ' | ').replace(/\s{2,}/g, ' ').trim(); return t.length > 1000 ? t.slice(0, 1000) : t; };

/** Sends now. Returns 'sent', 'skipped' (no number) or 'logged' (not configured); throws with the provider error. */
export async function sendWhatsAppNow(rawPhone, text) {
  const to = await phone(rawPhone);
  if (!to) return 'skipped';
  const provider = (await env.get('WHATSAPP_PROVIDER')).toLowerCase();
  if (provider === 'meta' && (await env.has('META_WA_TOKEN')) && (await env.has('META_WA_PHONE_ID'))) {
    const tpl = await env.get('META_WA_TEMPLATE');
    const body = tpl
      ? { messaging_product: 'whatsapp', to, type: 'template', template: { name: tpl, language: { code: await env.get('META_WA_TEMPLATE_LANG', 'en') }, components: [{ type: 'body', parameters: [{ type: 'text', text: templateText(text) }] }] } }
      : { messaging_product: 'whatsapp', to, type: 'text', text: { body: text } };
    const r = await fetch(`https://graph.facebook.com/v20.0/${await env.get('META_WA_PHONE_ID')}/messages`, {
      method: 'POST', headers: { Authorization: 'Bearer ' + (await env.get('META_WA_TOKEN')), 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(20000) });
    if (!r.ok) throw new Error(`Meta WhatsApp ${r.status}: ${await r.text()}` + (tpl ? '' : '  ->  Meta only delivers free-text messages to people who messaged your business in the last 24 hours. Create and approve a message template and enter its name.'));
    return 'sent';
  }
  if (provider === 'twilio' && (await env.has('TWILIO_SID')) && (await env.has('TWILIO_TOKEN')) && (await env.has('TWILIO_WA_FROM'))) {
    const sid = await env.get('TWILIO_SID');
    const form = new URLSearchParams({ From: 'whatsapp:' + (await env.get('TWILIO_WA_FROM')), To: 'whatsapp:+' + to, Body: text });
    const r = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${sid}/Messages.json`, {
      method: 'POST', headers: { Authorization: 'Basic ' + Buffer.from(sid + ':' + (await env.get('TWILIO_TOKEN'))).toString('base64'), 'Content-Type': 'application/x-www-form-urlencoded' }, body: form, signal: AbortSignal.timeout(20000) });
    if (!r.ok) throw new Error(`Twilio ${r.status}: ${await r.text()}  ->  With the Twilio sandbox, each receiver must first send the 'join <code>' message to the sandbox number.`);
    return 'sent';
  }
  return 'logged';
}

export function whatsapp(rawPhone, text) {
  return background(async () => {
    const rec = { channel: 'whatsapp', to: await phone(rawPhone), text: trimTo(text) };
    try { rec.status = await sendWhatsAppNow(rawPhone, text); }
    catch (e) { rec.status = 'failed'; rec.error = trimTo(rootMessage(e)); console.warn('whatsapp failed:', rec.error); }
    await log(rec);
  });
}

export async function testEmail(to) {
  try {
    const sent = await sendEmailNow(to, (await env.siteName()) + ' - test email', 'Test email', '<p>Email notifications are working.</p>');
    await log({ channel: 'email', to, subject: 'Test email', status: sent ? 'sent' : 'logged' });
    return sent ? 'ok' : 'Email is not configured: fill SMTP host, user and password first.';
  } catch (e) { await log({ channel: 'email', to, subject: 'Test email', status: 'failed', error: trimTo(rootMessage(e)) }); return 'Failed: ' + rootMessage(e); }
}

export async function testWhatsApp(to) {
  try {
    const st = await sendWhatsAppNow(to, `Test message from ${await env.siteName()}. WhatsApp notifications are working.`);
    await log({ channel: 'whatsapp', to: await phone(to), text: 'Test message', status: st });
    return st === 'sent' ? 'ok' : 'WhatsApp is not configured: choose a provider and fill its keys first.';
  } catch (e) { await log({ channel: 'whatsapp', to: await phone(to), text: 'Test message', status: 'failed', error: trimTo(rootMessage(e)) }); return 'Failed: ' + rootMessage(e); }
}

async function toAdmin(subject, title, html, waText) {
  const adminEmail = await env.get('ADMIN_NOTIFY_EMAIL', await env.get('ADMIN_EMAIL'));
  if (adminEmail) email(adminEmail, subject, title, html);
  if (await env.has('ADMIN_WHATSAPP')) whatsapp(await env.get('ADMIN_WHATSAPP'), waText);
}

function details(u, c) {
  const rows = [['Name', u.fullName], ['Email', u.email], ['WhatsApp', u.phone], ['Login ID', u.username], ['Gender', u.gender], ['Date of birth', u.dob],
    ['Time of birth', u.tob], ['Birth place', u.city], ['Lat / Lon', `${u.lat}, ${u.lon}`], ['UTC offset', `${u.tz} (${u.tzName})`]];
  if (c) rows.push(['Ascendant', c.ascendant.sign + ' ' + c.ascendant.dms], ['Moon / Nakshatra', c.moonSign + ' / ' + c.nakshatra], ['Running dasha', c.currentDasha.mahadasha + ' / ' + c.currentDasha.antardasha]);
  return '<table style="background:#fff7ed;border-radius:10px;padding:12px 16px">' + rows.map(([k, v]) => `<tr><td style="padding:2px 12px 2px 0;color:#9a3412">${k}</td><td><b>${esc(v)}</b></td></tr>`).join('') + '</table>';
}

// ------------------------------------------------------------------ events
export async function registered(u, password) {
  const login = (await env.siteUrl()) + '/login.html', site = await env.siteName();
  email(u.email, `Welcome to ${site} - your login details`, 'Your KP Horoscope account is ready',
    `<p>Namaste <b>${esc(u.fullName)}</b>,</p><p>Your account has been created. Chat with our KP astrologer to get your personalised report.</p>
     <table style="background:#fff7ed;border-radius:10px;padding:12px 16px;margin:12px 0"><tr><td>Login ID</td><td><b>${esc(u.username)}</b></td></tr>
     <tr><td>Email</td><td><b>${esc(u.email)}</b></td></tr><tr><td>Password</td><td><b>${esc(password)}</b></td></tr></table>
     <p><a href="${login}" style="background:#ea580c;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none">Login to your portal</a></p>`);
  whatsapp(u.phone, `Namaste ${u.fullName}!\nYour ${site} KP Horoscope account is ready.\n\nLogin ID: ${u.username}\nPassword: ${password}\n\nLogin: ${login}`);
  await toAdmin('New registration: ' + u.fullName, 'New customer registered', details(u), `New registration: ${u.fullName} (${u.phone}, ${u.email})`);
}

export async function chatStarted(account, p, c) {
  const self = p.relation === 'Self', url = await env.siteUrl();
  const forWhom = self ? '' : `<p>Consultation is for: <b>${esc(p.fullName)}</b> (${esc(p.relation)}) — born ${p.dob} ${p.tob}, ${esc(p.city)}</p>`;
  await toAdmin(`Consultation started: ${account.fullName}${self ? '' : ' for ' + p.fullName}`, 'A client started a consultation chat',
    `<p><b>${esc(account.fullName)}</b> just started a chat with the astrologer (consultation #${p.chatSession}).</p>${details(account, self ? c : null)}${forWhom}`
      + (self ? '' : `<p>Chart of ${esc(p.fullName)}: ${c.ascendant.sign} ascendant, Moon ${c.moonSign} (${c.nakshatra}), dasha ${c.currentDasha.mahadasha}/${c.currentDasha.antardasha}</p>`)
      + `<p><a href="${url}/admin.html">Open admin portal</a></p>`,
    `Consultation started\nClient: ${account.fullName}\nPhone: ${account.phone}\nEmail: ${account.email}${self ? '' : `\nFor: ${p.fullName} (${p.relation})`}\nDOB: ${p.dob} ${p.tob}\nPlace: ${p.city}\nAsc: ${c.ascendant.sign}, Moon: ${c.moonSign} (${c.nakshatra})\nDasha: ${c.currentDasha.mahadasha}/${c.currentDasha.antardasha}`);
  whatsapp(account.phone, `${account.fullName}, you are now connected with our KP astrologer${self ? '' : ' for ' + p.fullName}. Continue here: ${url}/client.html`);
}

export async function reportReady(u, r) {
  const url = await env.siteUrl();
  email(u.email, 'Your KP Horoscope report is ready', 'Your report is ready', `<p>Dear ${esc(u.fullName)}, your ${r.pages}-page KP horoscope report (${esc(r.title)}) is ready. Page 1 is free to read; unlock all pages in your portal.</p><p><a href="${url}/client.html#reports">Open my report</a></p>`);
  whatsapp(u.phone, `${u.fullName}, your ${r.pages}-page KP report is ready! Read page 1 free: ${url}/client.html#reports`);
  await toAdmin('Report generated: ' + u.fullName, 'Report generated', `<p>${esc(u.fullName)} generated a ${r.pages}-page report: ${esc(r.title)}</p>`, `Report generated for ${u.fullName}: ${r.title}`);
}

export async function paymentSuccess(u, p) {
  const what = p.purpose === 'report' ? 'Full KP Horoscope Report' : '30-min KP Consultation', amt = '₹' + p.amount / 100;
  email(u.email, 'Payment received - ' + what, 'Payment successful', `<p>Dear ${esc(u.fullName)}, we received ${amt} for <b>${what}</b>.</p><p>Payment ID: ${p.razorpayPaymentId}</p>`);
  whatsapp(u.phone, `Payment of ${amt} received for ${what}. Payment ID: ${p.razorpayPaymentId}. Thank you!`);
  await toAdmin(`Payment: ${amt} from ${u.fullName}`, 'Payment received', `<p>${esc(u.fullName)} paid ${amt} for ${what} (${p.razorpayPaymentId})</p>`, `${u.fullName} paid ${amt} for ${what}`);
}

export async function consultationBooked(u, c) {
  email(u.email, 'Consultation booked', 'Your 30-min KP consultation is booked', `<p>Dear ${esc(u.fullName)}, your consultation is booked for <b>${c.preferredDate} ${c.preferredTime}</b>. We will confirm and share the meeting link.</p>`);
  whatsapp(u.phone, `Your 30-min KP consultation is booked for ${c.preferredDate} ${c.preferredTime}. We'll share the meeting link soon.`);
  await toAdmin('New consultation: ' + u.fullName, 'New consultation booking', `<p>${esc(u.fullName)} booked ${c.preferredDate} ${c.preferredTime}. Topic: ${esc(c.topic)}</p>${details(u)}`, `New consultation: ${u.fullName} (${u.phone}) on ${c.preferredDate} ${c.preferredTime}`);
}

export function consultationUpdated(u, c) {
  const when = c.scheduledAt || `${c.preferredDate} ${c.preferredTime}`;
  email(u.email, 'Consultation ' + c.status, 'Consultation ' + c.status, `<p>Dear ${esc(u.fullName)}, your consultation on <b>${esc(when)}</b> is <b>${c.status}</b>.</p>` + (c.meetingLink ? `<p>Meeting link: <a href="${esc(c.meetingLink)}">${esc(c.meetingLink)}</a></p>` : ''));
  whatsapp(u.phone, `Your KP consultation (${when}) is ${c.status}.` + (c.meetingLink ? '\nMeeting link: ' + c.meetingLink : ''));
}

export async function passwordOtp(u, otp) {
  const site = await env.siteName();
  const body = `<p>Dear ${esc(u.fullName)},</p><p>Use this one-time password to reset your ${esc(site)} password:</p><p style="font-size:30px;letter-spacing:8px;font-weight:700;color:#c2410c;background:#fff7ed;padding:14px 20px;border-radius:12px;display:inline-block">${otp}</p><p>It is valid for 10 minutes. If you did not ask for this, you can ignore this email.</p>`;
  const rec = { channel: 'email', to: u.email, subject: 'Password reset code', status: 'logged' };
  let sent = false;
  try { sent = await sendEmailNow(u.email, `${site} - password reset code ${otp}`, 'Your password reset code', body); rec.status = sent ? 'sent' : 'logged'; }
  catch (e) { rec.status = 'failed'; rec.error = trimTo(rootMessage(e)); }
  await log(rec);
  if (u.phone) whatsapp(u.phone, `Your ${site} password reset code is ${otp}. It is valid for 10 minutes.`);
  return sent;
}
