// Developer check: end-to-end API flow against a running server (default http://localhost:8090)
const BASE = process.argv[2] || 'http://localhost:8090';
const jar = {};
async function call(who, method, path, body, raw) {
  const r = await fetch(BASE + path, { method, headers: { 'Content-Type': 'application/json', Cookie: jar[who] || '' }, body: body ? JSON.stringify(body) : undefined });
  const sc = r.headers.getSetCookie?.() || [];
  for (const c of sc) { const m = c.match(/^token=([^;]*)/); if (m) jar[who] = 'token=' + m[1]; }
  if (raw) return { status: r.status, type: r.headers.get('content-type'), size: (await r.arrayBuffer()).byteLength };
  const j = await r.json().catch(() => null);
  if (!r.ok) throw new Error(`${method} ${path} -> ${r.status} ${JSON.stringify(j)}`);
  return j;
}
const ok = (label, v) => console.log('OK  ', label, v === undefined ? '' : v);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

ok('health', JSON.stringify(await call('x', 'GET', '/api/health')));
ok('config', JSON.stringify(await call('x', 'GET', '/api/config')));
ok('languages', (await call('x', 'GET', '/api/languages')).length);
const pl = await call('x', 'GET', '/api/places?q=' + encodeURIComponent('kondapur'));
ok('places kondapur', pl.slice(0, 3).map((p) => p.label).join(' | '));
const hyd = (await call('x', 'GET', '/api/places?q=hyderabad'))[0];
ok('places hyderabad', hyd.label + ' ' + hyd.tzName);
ok('tz 1943 kolkata', (await call('x', 'GET', '/api/tz?tzName=Asia/Kolkata&dob=1943-06-01&tob=10:00:00')).tz);

const email = `test${Date.now()}@example.com`;
const reg = await call('c', 'POST', '/api/register', { fullName: 'Sai Teja', email, phone: '9876543210', gender: 'male', dob: '2009-08-15', tob: '06:30:15', city: hyd.label, lat: hyd.lat, lon: hyd.lon, tzName: hyd.tzName });
ok('register', reg.credentials.username + ' / ' + reg.credentials.password);
await call('c', 'POST', '/api/logout');
jar.c = '';
ok('login by id', (await call('c', 'POST', '/api/login', { login: reg.credentials.username, password: reg.credentials.password })).user.fullName);

let dash = await call('c', 'GET', '/api/my/dashboard');
ok('dashboard profiles', dash.profiles.map((p) => p.relation + ':' + p.fullName).join(', '));
let chat = await call('c', 'GET', '/api/my/chat');
ok('greeting', chat.messages[0].text.slice(0, 80) + '...');
for (const m of ['carrear help, mpc or bipc after 10th?', 'I am in 10th class, scored 92% in maths', 'I like solving problems, I feel nervous before exams', 'I want to become engineer', 'my birth time is from certificate']) {
  chat = await call('c', 'POST', '/api/my/chat', { message: m });
  console.log('     >', m, '\n     <', chat.messages.at(-1).text.slice(0, 110).replace(/\n/g, ' '), '...');
}
ok('topic / ready', chat.topic + ' / ' + chat.readyForReport + ' / memory ' + chat.memory.length);

const pr = await call('c', 'POST', '/api/my/profiles', { relation: 'Mother', fullName: 'Lakshmi', gender: 'female', dob: '1975-03-10', tob: '14:20:00', city: hyd.label, lat: hyd.lat, lon: hyd.lon, tzName: hyd.tzName });
ok('add mother profile', pr.id);
const ch2 = await call('c', 'GET', '/api/my/chat?profileId=' + pr.id);
ok('mother chat greeting', ch2.messages[0].text.includes('your mother'));

let rep = await call('c', 'POST', '/api/my/reports', { profileId: 'self', language: 'en' });
ok('report created', rep.id + ' ' + rep.status + ' ' + rep.title);
const t0 = Date.now();
while (rep.status === 'generating') { await sleep(1500); rep = await call('c', 'GET', '/api/my/reports/' + rep.id); }
ok('report ' + rep.status, `${rep.pages} pages in ${((Date.now() - t0) / 1000).toFixed(1)} s ${rep.error || ''}`);
ok('preview pdf', JSON.stringify(await call('c', 'GET', `/api/my/reports/${rep.id}/preview.pdf?view=1`, null, true)));
ok('full pdf locked', JSON.stringify(await call('c', 'GET', `/api/my/reports/${rep.id}/full.pdf`, null, true)));
const order = await call('c', 'POST', '/api/pay/order', { purpose: 'report', reportId: rep.id });
ok('order', order.orderId + ' demo=' + order.demo + ' amount=' + order.amount);
ok('verify', JSON.stringify(await call('c', 'POST', '/api/pay/verify', { orderId: order.orderId })));
ok('full pdf unlocked', JSON.stringify(await call('c', 'GET', `/api/my/reports/${rep.id}/full.pdf?download=1`, null, true)));

// Telugu version -> waits for the browser translation (no server key)
let te = await call('c', 'POST', `/api/my/reports/${rep.id}/translate`, { language: 'te' });
while (te.status === 'generating') { await sleep(1000); te = await call('c', 'GET', '/api/my/reports/' + te.id); }
ok('telugu status', te.status + ' unlocked=' + te.unlocked);
if (te.status === 'translating') {
  const tx = await call('c', 'GET', `/api/my/reports/${te.id}/texts`);
  ok('texts to translate', tx.texts.length);
  const fake = Object.fromEntries(tx.texts.map((t) => [t, 'తెలుగు ' + t]));
  await call('c', 'POST', `/api/my/reports/${te.id}/translations`, { translations: fake });
  while (te.status !== 'ready' && te.status !== 'failed') { await sleep(1500); te = await call('c', 'GET', '/api/my/reports/' + te.id); }
  ok('telugu report', te.status + ' ' + te.pages + ' pages');
}

const co = await call('c', 'POST', '/api/pay/order', { purpose: 'consultation', preferredDate: new Date(Date.now() + 86400000 * 3).toISOString().slice(0, 10), preferredTime: '18:30', topic: 'career' });
await call('c', 'POST', '/api/pay/verify', { orderId: co.orderId });
dash = await call('c', 'GET', '/api/my/dashboard');
ok('consultations / payments', dash.consultations.length + ' / ' + dash.payments.length);

const fp = await call('c', 'POST', '/api/forgot-password', { email });
ok('forgot password', fp.message);

// admin
ok('admin login', (await call('a', 'POST', '/api/login', { login: 'admin', password: process.env.ADMIN_PASSWORD || 'Admin@123' })).user.role);
ok('stats', JSON.stringify(await call('a', 'GET', '/api/admin/stats')).slice(0, 160));
const users = await call('a', 'GET', '/api/admin/users');
ok('users', users.length);
const ud = await call('a', 'GET', '/api/admin/users/' + reg.user.id);
ok('user detail', `${ud.profiles.length} profiles, ${ud.reports.length} reports, ${ud.profiles[0].messages.length} messages`);
ok('payments', (await call('a', 'GET', '/api/admin/payments')).length);
const cons = await call('a', 'GET', '/api/admin/consultations');
ok('consultations', cons.length);
ok('patch consultation', (await call('a', 'PATCH', '/api/admin/consultations/' + cons[0].id, { status: 'confirmed', meetingLink: 'https://meet.example.com/x' })).status);
ok('integrations', (await call('a', 'GET', '/api/admin/integrations')).fields.length);
ok('gateway', JSON.stringify((await call('a', 'GET', '/api/admin/gateway')).totals));
ok('notifications', (await call('a', 'GET', '/api/admin/notifications')).length);
ok('settings', JSON.stringify(await call('a', 'GET', '/api/admin/settings')));
ok('admin pdf', JSON.stringify(await call('a', 'GET', `/api/admin/reports/${rep.id}/report.pdf?view=1`, null, true)));
try { await call('c', 'GET', '/api/admin/stats'); console.log('FAIL client reached admin'); } catch (e) { ok('client blocked from admin', e.message.slice(0, 40)); }
console.log('\nALL DONE');
