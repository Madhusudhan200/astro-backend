// Razorpay Orders API + signature verification. Checkout supports UPI (PhonePe, Google Pay, Paytm),
// cards, net banking and wallets. Without keys the site runs in DEMO payment mode.
import crypto from 'crypto';
import * as env from './env.js';

export async function live() { return (await env.has('RAZORPAY_KEY_ID')) && (await env.has('RAZORPAY_KEY_SECRET')); }
export const keyId = () => env.get('RAZORPAY_KEY_ID');
async function auth() { return 'Basic ' + Buffer.from((await env.get('RAZORPAY_KEY_ID')) + ':' + (await env.get('RAZORPAY_KEY_SECRET'))).toString('base64'); }

export async function createOrder(amountPaise, receipt, notes) {
  if (!(await live())) return { id: 'order_demo_' + crypto.randomBytes(6).toString('hex'), amount: amountPaise, demo: true };
  const r = await fetch('https://api.razorpay.com/v1/orders', {
    method: 'POST', headers: { Authorization: await auth(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount: amountPaise, currency: 'INR', receipt, notes }), signal: AbortSignal.timeout(20000),
  });
  if (!r.ok) throw new Error(`Razorpay order failed: ${r.status} ${await r.text()}`);
  const j = await r.json();
  return { id: j.id, amount: j.amount, demo: false };
}

export async function get(path) {
  const r = await fetch('https://api.razorpay.com/v1/' + path, { headers: { Authorization: await auth() }, signal: AbortSignal.timeout(20000) });
  if (!r.ok) throw new Error(`Razorpay ${r.status}: ${await r.text()}`);
  return r.json();
}

const hmac = (secret, data) => crypto.createHmac('sha256', secret).update(data).digest('hex');
const safeEq = (a, b) => { const x = Buffer.from(String(a)), y = Buffer.from(String(b || '')); return x.length === y.length && crypto.timingSafeEqual(x, y); };

export async function verify(orderId, paymentId, signature) {
  if (!(await live())) return true;
  return safeEq(hmac(await env.get('RAZORPAY_KEY_SECRET'), `${orderId}|${paymentId}`), signature);
}

export async function verifyWebhook(raw, signature) {
  const s = await env.get('RAZORPAY_WEBHOOK_SECRET');
  return !!s && !!signature && safeEq(hmac(s, raw), signature);
}
