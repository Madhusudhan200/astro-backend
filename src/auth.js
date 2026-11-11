// JWT cookie sessions + password hashing.
import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { one } from './db.js';

export class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

let secret = null;
function key() {
  if (!secret) {
    const s = process.env.JWT_SECRET;
    if (!s || s.startsWith('change-me')) {
      console.warn('[auth] JWT_SECRET not set - using a random one (sessions reset on restart)');
      secret = crypto.randomBytes(32).toString('hex');
    } else secret = crypto.createHash('sha256').update(s).digest('hex');
  }
  return secret;
}

export const hash = (pw) => bcrypt.hashSync(pw, 10);
export const verify = (pw, h) => !!h && bcrypt.compareSync(String(pw), h);

export function login(res, user) {
  const token = jwt.sign({ sub: user.id, role: user.role }, key(), { expiresIn: '7d' });
  res.cookie('token', token, { httpOnly: true, sameSite: 'lax', secure: process.env.COOKIE_SECURE === 'true' || !!process.env.VERCEL, maxAge: 7 * 86400000, path: '/' });
}
export function logout(res) { res.clearCookie('token', { path: '/' }); }

/** Current user or 401; with role, 403 if the role differs. */
export async function requireUser(req, role) {
  const token = req.cookies?.token;
  if (!token) throw new ApiError(401, 'Please login');
  let payload;
  try { payload = jwt.verify(token, key()); } catch { throw new ApiError(401, 'Please login'); }
  const u = await one('SELECT * FROM users WHERE id=$1', [payload.sub], true);
  if (!u) throw new ApiError(401, 'Please login');
  if (role && u.role !== role) throw new ApiError(403, 'Forbidden');
  return u;
}

/** User object safe to send to the browser. */
export function publicUser(u) {
  if (!u) return null;
  const { passwordHash, ...rest } = u;
  return rest;
}
