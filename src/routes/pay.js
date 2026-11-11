// Razorpay payments for the full report and the 30-min consultation (UPI / PhonePe / GPay / cards).
import crypto from 'crypto';
import express from 'express';
import { one, insert, update, query } from '../db.js';
import { ApiError, requireUser } from '../auth.js';
import * as env from '../env.js';
import * as rzp from '../razorpay.js';
import * as notify from '../notify.js';
import { h, clean, prices } from './util.js';

const r = express.Router();

r.post('/order', h(async (req) => {
  const u = await requireUser(req, 'client');
  const b = req.body || {};
  const purpose = String(b.purpose);
  const p = {};
  if (purpose === 'report') {
    const rep = await one('SELECT * FROM reports WHERE id=$1 AND user_id=$2', [String(b.reportId), u.id]);
    if (!rep) throw new ApiError(404, 'Report not found');
    if (rep.unlocked) throw new ApiError(400, 'This report is already unlocked');
    if (rep.status !== 'ready') throw new ApiError(400, 'Report is still being prepared');
    p.reportId = rep.id;
  } else if (purpose === 'consultation') {
    p.preferredDate = clean(b.preferredDate, 10);
    p.preferredTime = clean(b.preferredTime, 5);
    p.topic = clean(b.topic, 300);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(p.preferredDate) || !/^\d{2}:\d{2}$/.test(p.preferredTime)) throw new ApiError(400, 'Choose a preferred date and time');
    if (p.preferredDate < new Date().toISOString().slice(0, 10)) throw new ApiError(400, 'Choose a future date');
  } else throw new ApiError(400, 'Invalid purpose');
  const amount = (await prices())[purpose] * 100;
  let o;
  try { o = await rzp.createOrder(amount, `${purpose}_${Date.now()}`, { userId: u.id, purpose }); }
  catch (e) { console.error('razorpay order failed', e); throw new ApiError(502, 'Could not start payment. Please try again.'); }
  await insert('payments', 'pay', { userId: u.id, purpose, amount, orderId: o.id, status: 'created', demo: !!o.demo, ...p });
  const live = await rzp.live();
  return { orderId: o.id, amount, currency: 'INR', key: live ? await rzp.keyId() : null, demo: !!o.demo, name: await env.siteName(),
    description: purpose === 'report' ? 'Full KP Horoscope Report' : '30-min KP Consultation', prefill: { name: u.fullName, email: u.email, contact: u.phone || '' } };
}));

r.post('/verify', h(async (req) => {
  const u = await requireUser(req, 'client');
  const b = req.body || {};
  const p = await one('SELECT * FROM payments WHERE order_id=$1 AND user_id=$2', [String(b.orderId), u.id]);
  if (!p) throw new ApiError(404, 'Order not found');
  if (p.status === 'paid') return { ok: true };
  if (!p.demo && !(await rzp.verify(p.orderId, b.paymentId, b.signature))) {
    await update('payments', p.id, { status: 'failed' });
    throw new ApiError(400, 'Payment verification failed');
  }
  await fulfil(p, p.demo ? 'pay_demo_' + crypto.randomBytes(4).toString('hex') : b.paymentId, b.signature);
  return { ok: true };
}));

async function fulfil(p, paymentId, signature) {
  // atomic: only the first caller (browser or webhook) flips the payment to paid
  const won = await query("UPDATE payments SET status='paid', razorpay_payment_id=$1, signature=$2, paid_at=$3, updated_at=$3 WHERE id=$4 AND status<>'paid' RETURNING id",
    [paymentId, signature || null, new Date().toISOString(), p.id]);
  if (!won.length) return;
  const paid = { ...p, status: 'paid', razorpayPaymentId: paymentId };
  const u = await one('SELECT * FROM users WHERE id=$1', [p.userId]);
  if (p.purpose === 'report' && p.reportId) {
    const rep = await one('SELECT * FROM reports WHERE id=$1', [p.reportId]);
    if (rep) {
      // unlock the report and all its language versions
      const rootId = rep.parentId || rep.id;
      await query('UPDATE reports SET unlocked=TRUE WHERE id=$1 OR parent_id=$1 OR id=$2', [rootId, rep.id]);
    }
  }
  if (p.purpose === 'consultation') {
    const c = await insert('consultations', 'con', { userId: u.id, paymentId: p.id, status: 'requested', durationMin: 30, preferredDate: p.preferredDate, preferredTime: p.preferredTime, topic: p.topic });
    await notify.consultationBooked(u, c);
  }
  await notify.paymentSuccess(u, paid);
}

export async function onWebhook(raw) {
  try {
    const evt = JSON.parse(raw.toString('utf8'));
    if (evt.event === 'payment.captured' || evt.event === 'order.paid') {
      const ent = evt.payload?.payment?.entity || {};
      const p = await one('SELECT * FROM payments WHERE order_id=$1', [ent.order_id]);
      if (p) await fulfil(p, ent.id, null);
    }
  } catch (e) { console.warn('webhook error:', e.message); }
}

export default r;
