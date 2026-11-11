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
    let s = process.env.JWT_SECRET;
    if (!s || s.startsWith('change-me')) {
      // No JWT_SECRET: derive a stable secret from the (private) database address, so every serverless
      // instance agrees and logins survive restarts. Random only for a local database without a URL.
      const db = process.env.DATABASE_URL || process.env.POSTGRES_URL || process.env.DB_URL;
      s = db ? 'astrology-talk|' + db : crypto.randomBytes(32).toString('hex');
    }
    secret = crypto.createHash('sha256').update(s).digest('hex');
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
