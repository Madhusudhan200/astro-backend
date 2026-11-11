// KP (Krishnamurti Paddhati) horoscope engine.
// Pure JS: planetary positions from astronomy-engine, Placidus cusps,
// KP (Krishnamurti) ayanamsa, star / sub / sub-sub lords, significators,
// ruling planets and Vimshottari dasha.
import * as A from 'astronomy-engine';

const D2R = Math.PI / 180;
const R2D = 180 / Math.PI;
const norm = (x) => ((x % 360) + 360) % 360;

const SIGNS = ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'];
const SIGN_LORDS = ['Mars', 'Venus', 'Mercury', 'Moon', 'Sun', 'Mercury', 'Venus', 'Mars', 'Jupiter', 'Saturn', 'Saturn', 'Jupiter'];
const NAKSHATRAS = ['Ashwini', 'Bharani', 'Krittika', 'Rohini', 'Mrigashira', 'Ardra', 'Punarvasu', 'Pushya', 'Ashlesha', 'Magha', 'Purva Phalguni', 'Uttara Phalguni', 'Hasta', 'Chitra', 'Swati', 'Vishakha', 'Anuradha', 'Jyeshtha', 'Mula', 'Purva Ashadha', 'Uttara Ashadha', 'Shravana', 'Dhanishta', 'Shatabhisha', 'Purva Bhadrapada', 'Uttara Bhadrapada', 'Revati'];
const DASHA_ORDER = ['Ketu', 'Venus', 'Sun', 'Moon', 'Mars', 'Rahu', 'Jupiter', 'Saturn', 'Mercury'];
const DASHA_YEARS = { Ketu: 7, Venus: 20, Sun: 6, Moon: 10, Mars: 7, Rahu: 18, Jupiter: 16, Saturn: 19, Mercury: 17 };
const ABBR = { Sun: 'Su', Moon: 'Mo', Mars: 'Ma', Mercury: 'Me', Jupiter: 'Ju', Venus: 'Ve', Saturn: 'Sa', Rahu: 'Ra', Ketu: 'Ke' };
const WEEKDAY_LORDS = ['Sun', 'Moon', 'Mars', 'Mercury', 'Jupiter', 'Venus', 'Saturn'];
const NAK_SPAN = 360 / 27;
const YEAR_MS = 365.25 * 86400000;

function dms(deg) {
  const d = Math.floor(deg);
  const mf = (deg - d) * 60;
  const m = Math.floor(mf);
  const s = Math.floor((mf - m) * 60);
  return `${d}°${String(m).padStart(2, '0')}'${String(s).padStart(2, '0')}"`;
}

// Krishnamurti ayanamsa: 22°21'50" at J1900.0, advanced by general precession.
function kpAyanamsa(jdUT) {
  const prec = (T) => (5028.796195 * T + 1.1054348 * T * T) / 3600;
  const T = (jdUT - 2451545.0) / 36525;
  const T0 = (2415020.0 - 2451545.0) / 36525;
  return 22.363889 + prec(T) - prec(T0);
}

function tropicalLongitude(body, time) {
  const v = A.GeoVector(body, time, true);
  const ecl = A.SphereFromVector(A.RotateVector(A.Rotation_EQJ_ECT(time), v));
  return norm(ecl.lon);
}

function meanNode(time) {
  const T = time.tt / 36525;
  return norm(125.04452 - 1934.136261 * T + 0.0020708 * T * T + (T * T * T) / 450000);
}

function obliquity(time) {
  const T = time.tt / 36525;
  return 23.439291111 - 0.0130042 * T - 1.64e-7 * T * T + 5.04e-7 * T * T * T;
}

// Placidus house cusps (tropical). Returns array[12] of longitudes, index 0 = house 1.
function placidusCusps(ramc, lat, eps) {
  const e = eps * D2R;
  const phi = lat * D2R;
  const raToLon = (ra) => norm(Math.atan2(Math.sin(ra * D2R), Math.cos(ra * D2R) * Math.cos(e)) * R2D);
  const asc = norm(Math.atan2(Math.cos(ramc * D2R), -(Math.sin(ramc * D2R) * Math.cos(e) + Math.tan(phi) * Math.sin(e))) * R2D);
  const mc = raToLon(ramc);

  const solve = (fn) => {
    let lon = raToLon(fn(0));
    for (let i = 0; i < 60; i++) {
      const dec = Math.asin(Math.sin(e) * Math.sin(lon * D2R));
      let x = Math.tan(phi) * Math.tan(dec);
      x = Math.max(-1, Math.min(1, x));
      const ad = Math.asin(x) * R2D;
      const next = raToLon(fn(ad));
      if (Math.abs(next - lon) < 1e-7) { lon = next; break; }
      lon = next;
    }
    return lon;
  };
  const c11 = solve((ad) => ramc + (90 + ad) / 3);
  const c12 = solve((ad) => ramc + (2 * (90 + ad)) / 3);
  const c2 = solve((ad) => ramc + 180 - (2 * (90 - ad)) / 3);
  const c3 = solve((ad) => ramc + 180 - (90 - ad) / 3);

  const cusps = new Array(12);
  cusps[0] = asc; cusps[1] = c2; cusps[2] = c3; cusps[3] = norm(mc + 180);
  cusps[4] = norm(c11 + 180); cusps[5] = norm(c12 + 180); cusps[6] = norm(asc + 180);
  cusps[7] = norm(c2 + 180); cusps[8] = norm(c3 + 180); cusps[9] = mc;
  cusps[10] = c11; cusps[11] = c12;
  return cusps;
}

// KP lords for a sidereal longitude: sign lord, star lord, sub lord, sub-sub lord.
function kpLords(lon) {
  lon = norm(lon);
  const signIdx = Math.floor(lon / 30);
  const nakIdx = Math.floor(lon / NAK_SPAN);
  const starLord = DASHA_ORDER[nakIdx % 9];
  const pos = lon - nakIdx * NAK_SPAN;

  const divide = (startLord, span, offset) => {
    let i = DASHA_ORDER.indexOf(startLord);
    let acc = 0;
    for (let k = 0; k < 9; k++) {
      const lord = DASHA_ORDER[(i + k) % 9];
      const part = (span * DASHA_YEARS[lord]) / 120;
      if (offset < acc + part || k === 8) return { lord, start: acc, span: part };
      acc += part;
    }
  };
  const sub = divide(starLord, NAK_SPAN, pos);
  const subSub = divide(sub.lord, sub.span, pos - sub.start);
  return {
    sign: SIGNS[signIdx],
    signLord: SIGN_LORDS[signIdx],
    degInSign: lon - signIdx * 30,
    nakshatra: NAKSHATRAS[nakIdx],
    pada: Math.floor(pos / (NAK_SPAN / 4)) + 1,
    starLord,
    subLord: sub.lord,
    subSubLord: subSub.lord,
  };
}

function houseOf(lon, cusps) {
  for (let i = 0; i < 12; i++) {
    const a = cusps[i];
    const b = cusps[(i + 1) % 12];
    const span = norm(b - a);
    if (norm(lon - a) < span) return i + 1;
  }
  return 1;
}

function vimshottari(moonLon, birthMs) {
  const nakIdx = Math.floor(norm(moonLon) / NAK_SPAN);
  const lord = DASHA_ORDER[nakIdx % 9];
  const elapsedFrac = (norm(moonLon) - nakIdx * NAK_SPAN) / NAK_SPAN;
  const balanceYears = DASHA_YEARS[lord] * (1 - elapsedFrac);
  // Start of the running mahadasha (virtually before birth)
  let start = birthMs - DASHA_YEARS[lord] * elapsedFrac * YEAR_MS;
  const startIdx = DASHA_ORDER.indexOf(lord);
  const periods = [];
  for (let k = 0; k < 9; k++) {
    const md = DASHA_ORDER[(startIdx + k) % 9];
    const mdYears = DASHA_YEARS[md];
    const mdEnd = start + mdYears * YEAR_MS;
    const antardashas = [];
    let aStart = start;
    const mdIdx = DASHA_ORDER.indexOf(md);
    for (let j = 0; j < 9; j++) {
      const ad = DASHA_ORDER[(mdIdx + j) % 9];
      const adYears = (mdYears * DASHA_YEARS[ad]) / 120;
      const aEnd = aStart + adYears * YEAR_MS;
      antardashas.push({ lord: ad, start: new Date(aStart).toISOString().slice(0, 10), end: new Date(aEnd).toISOString().slice(0, 10) });
      aStart = aEnd;
    }
    periods.push({ lord: md, years: mdYears, start: new Date(start).toISOString().slice(0, 10), end: new Date(mdEnd).toISOString().slice(0, 10), antardashas });
    start = mdEnd;
  }
  return { balance: { lord, years: balanceYears }, periods };
}

/**
 * @param {object} p { dob:'YYYY-MM-DD', tob:'HH:MM:SS', lat, lon, tz (hours, e.g. 5.5) }
 */
function computeChart(p) {
  const [y, mo, d] = p.dob.split('-').map(Number);
  const [h, mi, s] = (p.tob || '00:00:00').split(':').map(Number);
  const utcMs = Date.UTC(y, mo - 1, d, h, mi, s || 0) - Number(p.tz) * 3600000;
  const date = new Date(utcMs);
  const time = A.MakeTime(date);
  const jdUT = time.ut + 2451545.0;
  const ayan = kpAyanamsa(jdUT);
  const eps = obliquity(time);

  const bodies = { Sun: A.Body.Sun, Moon: A.Body.Moon, Mars: A.Body.Mars, Mercury: A.Body.Mercury, Jupiter: A.Body.Jupiter, Venus: A.Body.Venus, Saturn: A.Body.Saturn };
  const nextTime = A.MakeTime(new Date(utcMs + 3600000));
  const tropical = {};
  const retro = {};
  for (const [name, b] of Object.entries(bodies)) {
    tropical[name] = tropicalLongitude(b, time);
    const later = tropicalLongitude(b, nextTime);
    let diff = later - tropical[name];
    if (diff > 180) diff -= 360; if (diff < -180) diff += 360;
    retro[name] = diff < 0;
  }
  tropical.Rahu = meanNode(time);
  tropical.Ketu = norm(tropical.Rahu + 180);
  retro.Rahu = true; retro.Ketu = true;

  const gast = A.SiderealTime(date); // hours
  const ramc = norm(gast * 15 + Number(p.lon));
  const tropCusps = placidusCusps(ramc, Number(p.lat), eps);
  const cuspLons = tropCusps.map((c) => norm(c - ayan));

  const cusps = cuspLons.map((lon, i) => ({ house: i + 1, longitude: lon, dms: dms(lon % 30), ...kpLords(lon) }));
  const planets = Object.keys(ABBR).map((name) => {
    const lon = norm(tropical[name] - ayan);
    return { name, abbr: ABBR[name], longitude: lon, dms: dms(lon % 30), retrograde: retro[name] && !['Rahu', 'Ketu'].includes(name), house: houseOf(lon, cuspLons), ...kpLords(lon) };
  });
  const byName = Object.fromEntries(planets.map((pl) => [pl.name, pl]));

  // Ownership: houses whose cusp sign is ruled by the planet
  const owned = {};
  for (const pl of planets) owned[pl.name] = cusps.filter((c) => c.signLord === pl.name).map((c) => c.house);
  // Nodes act as agents of their sign lord
  owned.Rahu = owned[byName.Rahu.signLord].slice();
  owned.Ketu = owned[byName.Ketu.signLord].slice();

  // 4-level KP significators per planet
  const significators = planets.map((pl) => {
    const star = byName[pl.starLord];
    const levels = {
      A: [star.house],
      B: [pl.house],
      C: owned[pl.starLord] || [],
      D: owned[pl.name] || [],
    };
    const all = [...new Set([...levels.A, ...levels.B, ...levels.C, ...levels.D])].sort((a, b) => a - b);
    return { planet: pl.name, ...levels, all };
  });

  // Houses -> planets signifying them
  const houseSignificators = Array.from({ length: 12 }, (_, i) => {
    const h = i + 1;
    const lv = { A: [], B: [], C: [], D: [] };
    for (const pl of planets) {
      if (pl.house === h) lv.B.push(pl.name);
      if ((owned[pl.name] || []).includes(h) && !['Rahu', 'Ketu'].includes(pl.name)) lv.D.push(pl.name);
    }
    for (const pl of planets) {
      if (lv.B.includes(pl.starLord)) lv.A.push(pl.name);
      if (lv.D.includes(pl.starLord)) lv.C.push(pl.name);
    }
    return { house: h, ...lv };
  });

  // Ruling planets (at birth moment)
  const localDay = new Date(utcMs + Number(p.tz) * 3600000).getUTCDay();
  const asc = cusps[0];
  const moon = byName.Moon;
  const rulingPlanets = {
    dayLord: WEEKDAY_LORDS[localDay],
    ascSignLord: asc.signLord, ascStarLord: asc.starLord, ascSubLord: asc.subLord,
    moonSignLord: moon.signLord, moonStarLord: moon.starLord, moonSubLord: moon.subLord,
  };

  const dasha = vimshottari(moon.longitude, utcMs);
  const now = new Date().toISOString().slice(0, 10);
  const currentMd = dasha.periods.find((pd) => pd.start <= now && now < pd.end) || null;
  const currentAd = currentMd ? currentMd.antardashas.find((a) => a.start <= now && now < a.end) : null;

  // Rasi chart: sign index -> planet abbreviations
  const rasi = Array.from({ length: 12 }, () => []);
  for (const pl of planets) rasi[Math.floor(pl.longitude / 30)].push(pl.abbr + (pl.retrograde ? '(R)' : ''));
  const ascSignIdx = Math.floor(asc.longitude / 30);

  return {
    input: { ...p, utc: date.toISOString() },
    ayanamsa: { name: 'Krishnamurti (KP)', value: ayan, dms: dms(ayan) },
    houseSystem: 'Placidus',
    ascendant: { sign: asc.sign, signIdx: ascSignIdx, dms: asc.dms, starLord: asc.starLord, subLord: asc.subLord },
    planets, cusps, significators, houseSignificators, rulingPlanets, dasha,
    currentDasha: { mahadasha: currentMd && currentMd.lord, antardasha: currentAd && currentAd.lord, mdEnd: currentMd && currentMd.end, adEnd: currentAd && currentAd.end },
    rasi, nakshatra: moon.nakshatra, moonSign: moon.sign, sunSign: byName.Sun.sign,
  };
}

// helpers used by the astrologer and the report builder
export function planetOf(chart, name) { return chart.planets.find((p) => p.name === name); }
export function housesSignified(chart, planet) { const s = chart.significators.find((x) => x.planet === planet); return s ? s.all : []; }
export const PLANETS = ['Sun', 'Moon', 'Mars', 'Mercury', 'Jupiter', 'Venus', 'Saturn', 'Rahu', 'Ketu'];
export { computeChart, SIGNS, SIGN_LORDS, NAKSHATRAS, DASHA_ORDER, DASHA_YEARS, dms, kpAyanamsa, tropicalLongitude, norm };
