// Shared helpers for the routes: async handler wrapper, input cleaning, birth-details validation, prices.
import { ApiError } from '../auth.js';
import * as places from '../places.js';
import * as env from '../env.js';
import { getSetting } from '../db.js';

/** Express 4 does not catch rejected promises - wrap every async handler. */
export const h = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).then((out) => { if (out !== undefined && !res.headersSent) res.json(out); }).catch(next);

export function clean(o, max) {
  const s = o == null ? '' : String(o).replace(/[<>]/g, '').trim();
  return s.length > max ? s.slice(0, max) : s;
}
const has = (o) => o != null && String(o).trim() !== '';

/** Validates birth details (date, time with seconds, place) and resolves coordinates + exact UTC offset. */
export async function birth(b) {
  const dob = clean(b.dob, 10);
  let tob = clean(b.tob, 8);
  if (/^\d{2}:\d{2}$/.test(tob)) tob += ':00';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dob) || !/^\d{2}:\d{2}:\d{2}$/.test(tob)) throw new ApiError(400, 'Please enter date of birth and time of birth (HH:MM:SS).');
  if (dob < '1900-01-01' || dob > new Date().toISOString().slice(0, 10)) throw new ApiError(400, 'Please enter a valid date of birth.');
  let lat = has(b.lat) ? Number(b.lat) : null, lon = has(b.lon) ? Number(b.lon) : null;
  const city = clean(b.city, 200);
  let known = null;
  if (lat == null || lon == null || Number.isNaN(lat) || Number.isNaN(lon)) {
    const l = await places.searchLocal(city, 1);
    if (l.length) { known = l[0]; lat = known.lat; lon = known.lon; }
  }
  if (lat == null || lon == null || Number.isNaN(lat) || Number.isNaN(lon) || Math.abs(lat) > 66 || Math.abs(lon) > 180)
    throw new ApiError(400, 'Could not find the birth place. Pick it from the suggestions or enter latitude and longitude.');
  const tzName = has(b.tzName) && places.validTz(String(b.tzName)) ? String(b.tzName) : known ? known.tzName : await places.nearestTz(lat, lon);
  let tz = null;
  if (b.tzManual === true && has(b.tz)) tz = Number(b.tz);
  if ((tz == null || Number.isNaN(tz)) && places.validTz(tzName)) tz = places.offsetForLocal(tzName, dob, tob);
  if ((tz == null || Number.isNaN(tz)) && has(b.tz)) tz = Number(b.tz);
  if (tz == null || Number.isNaN(tz) || Math.abs(tz) > 14) throw new ApiError(400, 'Please enter the UTC offset of the birth place.');
  return { dob, tob, city: !city && known ? known.label : city, lat, lon, tz, tzName };
}

export async function prices() {
  return {
    report: parseInt(await getSetting('reportPrice', await env.get('REPORT_PRICE', '499')), 10),
    consultation: parseInt(await getSetting('consultPrice', await env.get('CONSULT_PRICE', '999')), 10),
  };
}

export const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
