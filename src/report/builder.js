// Assembles the complete (90+ page) KP horoscope report as document blocks:
// { t:'chapter', title, subtitle } { t:'heading', text } { t:'para', text } { t:'bullets', items }
// { t:'table', headers, rows, widths } { t:'kv', pairs } { t:'charts', charts:[{title, subtitle, grid, asc}] } { t:'note', text }
import { housesSignified, planetOf, SIGNS, SIGN_LORDS, DASHA_ORDER, DASHA_YEARS } from '../kp/engine.js';
import * as V from '../kp/vedic.js';
import { cuspText, ord } from '../astrologer.js';

const houses = (h) => (h && h.length ? [...h].sort((a, b) => a - b).join(', ') : '—');
const fmt = (d) => Number(d).toFixed(4);
const S = (v) => (v == null ? '' : String(v));
const row = (...v) => v.map(S);

const chapter = (title, subtitle) => ({ t: 'chapter', title, subtitle });
const heading = (text) => ({ t: 'heading', text });
const para = (text) => ({ t: 'para', text });
const table = (headers, rows, widths) => ({ t: 'table', headers, rows, widths });
const kv = (pairs) => ({ t: 'kv', pairs });
const charts = (...c) => ({ t: 'charts', charts: c });
const note = (text) => ({ t: 'note', text });
const grid = (title, subtitle, g, asc) => ({ title, subtitle, grid: g, asc });

function antarNote(c, md, ad) {
  const s = housesSignified(c, ad);
  const gain = s.includes(11) || s.includes(10) || s.includes(2), hard = s.includes(8) || s.includes(12);
  return V.DASHA_THEME[ad].split(',')[0] + (gain && !hard ? ' - productive' : hard && !gain ? ' - plan carefully' : gain ? ' - gains with expenses' : '');
}
function pdNote(c, lord) {
  const s = housesSignified(c, lord), l = [];
  if (s.includes(10) || s.includes(6)) l.push('work');
  if (s.includes(2) || s.includes(11)) l.push('gains');
  if (s.includes(4) || s.includes(9)) l.push('studies / home');
  if (s.includes(7)) l.push('relationships');
  if (s.includes(5)) l.push('creativity');
  if (s.includes(12) || s.includes(8)) l.push('expenses - be careful');
  return l.length ? l.join(', ') : 'steady, routine period';
}
function yearSummary(period, jFrom, sFrom, year) {
  const jGood = [2, 5, 7, 9, 11].includes(jFrom), sGood = [3, 6, 11].includes(sFrom), sSade = [12, 1, 2].includes(sFrom);
  const tone = jGood && sGood ? 'This is one of your stronger years: both Jupiter and Saturn support you, so it is a good time for important decisions and new beginnings.'
    : jGood ? `Jupiter's support makes ${year} a year of growth and good opportunities; use it for learning, family events and financial planning.`
      : sGood ? `Saturn rewards steady effort in ${year}; consistent work brings stable gains even if progress feels slow.`
        : sSade ? `${year} falls in Sade Sati: a year of maturity and restructuring. Stay disciplined, avoid hasty risks and look after your health.`
          : `${year} is a balanced year: results come in proportion to effort. Focus on routine, health and steady progress.`;
  return tone + (period ? ` The year opens in the ${period} period, whose themes are listed above.` : '');
}
/** Pratyantar (sub-sub) periods inside an antardasha: [lord, start, end] */
function pratyantar(ad) {
  const s = Date.parse(ad.start), e = Date.parse(ad.end), days = (e - s) / 86400000;
  const i0 = DASHA_ORDER.indexOf(ad.lord), out = [];
  let acc = 0;
  for (let k = 0; k < 9; k++) {
    const lord = DASHA_ORDER[(i0 + k) % 9], len = (days * DASHA_YEARS[lord]) / 120;
    const a = new Date(s + Math.round(acc) * 86400000).toISOString().slice(0, 10);
    const z = k === 8 ? ad.end : new Date(s + Math.round(acc + len) * 86400000).toISOString().slice(0, 10);
    out.push([lord, a, z]); acc += len;
  }
  return out;
}

export function build(u, c, content, topic) {
  const b = [];
  const first = String(u.fullName).split(/\s+/)[0];
  const moon = planetOf(c, 'Moon');
  const today = new Date().toISOString().slice(0, 10);

  // 1. Birth details & panchang
  b.push(chapter('Birth Details & Panchang', 'The foundation of your horoscope'));
  b.push(kv([['Name', u.fullName], ['Gender', u.gender || '—'], ['Date of birth', u.dob], ['Time of birth', u.tob], ['Place of birth', u.city],
    ['Latitude / Longitude', fmt(u.lat) + ', ' + fmt(u.lon)], ['Time zone', (u.tzName ? u.tzName + '  ' : '') + 'UTC ' + (u.tz >= 0 ? '+' : '') + u.tz + ' h'],
    ['Birth moment (UTC)', c.input.utc], ['Ayanamsa', 'Krishnamurti (KP) ' + c.ayanamsa.dms], ['House system', 'Placidus (KP)']]));
  b.push(heading('Panchang at birth'));
  const pk = V.panchang(c);
  b.push(kv(pk));
  b.push(para(`The panchang - weekday, tithi, nakshatra, yoga and karana - describes the quality of the moment you were born. You were born on a ${pk[0][1]}, ruled by ${c.rulingPlanets.dayLord}, which colours your nature with ${V.DASHA_THEME[c.rulingPlanets.dayLord]}. Your birth nakshatra ${c.nakshatra} is ruled by ${moon.starLord} - the lord whose Vimshottari dasha began your life's planetary clock.`));

  // 2. Charts
  b.push(chapter('Your Birth Charts', 'Rasi (D1), Chandra (Moon) chart and Navamsa (D9)'));
  b.push(charts(grid('Rasi (D1)', 'Lagna chart', c.rasi, c.ascendant.signIdx), grid('Chandra Lagna', 'Moon as ascendant', c.rasi, Math.floor(moon.longitude / 30))));
  b.push(charts(grid('Navamsa (D9)', 'Marriage & inner strength', V.vargaGrid(c, 9), V.vargaAsc(c, 9)), grid('Dasamsa (D10)', 'Career', V.vargaGrid(c, 10), V.vargaAsc(c, 10))));
  b.push(note("Charts are in South-Indian style: signs are fixed, the shaded box with 'Asc' is the ascendant. (R) marks retrograde planets."));

  // 3. Planets
  b.push(chapter('Planetary Positions (KP)', 'Sign, star, sub and sub-sub lords of the nine grahas'));
  b.push(table(['Planet', 'Sign', 'Degree', 'House', 'Nakshatra', 'Sign L', 'Star L', 'Sub L', 'Sub-Sub L'],
    c.planets.map((p) => row(p.name + (p.retrograde ? ' (R)' : ''), p.sign, p.dms, p.house, p.nakshatra + '-' + p.pada, p.signLord, p.starLord, p.subLord, p.subSubLord)), [11, 11, 11, 7, 16, 11, 11, 11, 11]));
  b.push(heading('Dignity of planets'));
  b.push(table(['Planet', 'In sign', 'Dignity', 'Exaltation', 'Debilitation'], c.planets.map((p) => row(p.name, p.sign, V.dignity(p.name, p.sign, p.signLord), V.PLANET[p.name].exalt, V.PLANET[p.name].debil)), [16, 18, 26, 20, 20]));
  b.push(heading('Ruling planets at birth'));
  const rp = c.rulingPlanets;
  b.push(kv([['Day lord', rp.dayLord], ['Ascendant sign / star / sub lord', `${rp.ascSignLord} / ${rp.ascStarLord} / ${rp.ascSubLord}`], ['Moon sign / star / sub lord', `${rp.moonSignLord} / ${rp.moonStarLord} / ${rp.moonSubLord}`]]));

  // 4. Cusps
  b.push(chapter('Placidus House Cusps', 'The twelve bhavas with their KP lords'));
  b.push(table(['Cusp', 'Sign', 'Degree', 'Nakshatra', 'Sign L', 'Star L', 'Sub L', 'Sub-Sub L'], c.cusps.map((p) => row(p.house, p.sign, p.dms, p.nakshatra + '-' + p.pada, p.signLord, p.starLord, p.subLord, p.subSubLord)), [8, 13, 13, 19, 12, 12, 12, 11]));
  b.push(heading('Planets in each house (Bhava placement)'));
  b.push(table(['House', 'Name', 'Cusp sign', 'Planets'], Array.from({ length: 12 }, (_, i) => row(i + 1, V.HOUSE_NAME[i], c.cusps[i].sign, c.planets.filter((p) => p.house === i + 1).map((p) => p.name).join(', '))), [9, 37, 20, 34]));
  b.push(para('In KP, the sub lord of each cusp is the deciding factor for that house: it shows whether the matters of the house will fructify. If the cusp sub lord signifies the supporting houses (through its star lord), the event is promised; the dasha periods of the significators then decide when it happens.'));

  // 5. Significators
  b.push(chapter('KP Significators', 'Which planets deliver the results of each house'));
  b.push(table(['Planet', 'A: Star occ.', 'B: Occupies', 'C: Star owns', 'D: Owns', 'All houses'], c.significators.map((s) => row(s.planet, houses(s.A), houses(s.B), houses(s.C), houses(s.D), houses(s.all))), [14, 15, 15, 15, 15, 26]));
  b.push(heading('House significators'));
  b.push(table(['House', 'A (strongest)', 'B', 'C', 'D'], c.houseSignificators.map((h) => row(h.house, h.A.join(', '), h.B.join(', '), h.C.join(', '), h.D.join(', '))), [9, 26, 22, 22, 21]));
  b.push(para('Level A (planets in the star of occupants) is strongest, then B (occupants), C (planets in the star of the owner) and D (owner). A planet gives the results of every house it signifies during its dasha, bhukti and antara.'));

  // 6. Divisional charts
  for (const v of V.VARGAS) {
    b.push(chapter(`Divisional Chart ${v.code} - ${v.name}`, 'Governs ' + v.governs));
    const asc = V.vargaAsc(c, v.n);
    b.push(charts(grid(`${v.code} ${v.name}`, 'Asc: ' + SIGNS[asc], V.vargaGrid(c, v.n), asc)));
    const strong = [];
    const rows = c.planets.map((p) => {
      const s = V.vargaSign(p.longitude, v.n), d = V.dignity(p.name, SIGNS[s], SIGN_LORDS[s]);
      if (d === 'Exalted' || d === 'Own sign') strong.push(`${p.name} (${d.toLowerCase()})`);
      return row(p.name, SIGNS[s], SIGN_LORDS[s], ((s - asc + 12) % 12) + 1, d);
    });
    b.push(table(['Planet', v.code + ' sign', 'Sign lord', `House from ${v.code} Asc`, 'Dignity'], rows, [16, 20, 18, 20, 26]));
    b.push(para(`The ${v.name} chart (${v.code}) is used to study ${v.governs}. Its ascendant falls in ${SIGNS[asc]}. ${V.SIGN_TRAITS[asc]}\n\n`
      + (strong.length ? `Strong planets in this varga: ${strong.join(', ')}. These planets give especially good results for ${v.governs} during their periods.`
        : 'No planet is exalted or in its own sign here, so results in this area come mainly through the KP promise of the main chart and the timing of dashas.')));
  }

  // 7. Nakshatra & ascendant
  const nk = V.NAKS[V.nakIdx(c.nakshatra)];
  b.push(chapter('Your Birth Star - ' + c.nakshatra, 'Janma Nakshatra profile'));
  b.push(kv([['Nakshatra', `${c.nakshatra} (pada ${moon.pada})`], ['Ruling planet (star lord)', moon.starLord], ['Deity', nk.deity], ['Symbol', nk.symbol], ['Gana (temperament)', nk.gana], ['Moon sign', c.moonSign]]));
  b.push(para(`Your Moon is in ${c.nakshatra}, presided over by ${nk.deity} and symbolised by the ${nk.symbol.toLowerCase()}. People born in this star are ${nk.traits}.\n\n`
    + `The Moon represents the mind, so your nakshatra describes how you feel, think and respond emotionally. Its lord ${moon.starLord} is a lifelong influence: ${moon.starLord} brings ${V.PLANET[moon.starLord].karaka} into focus, and its dasha opened your life (balance ${c.dasha.balance.years.toFixed(2)} years at birth).\n\n`
    + `As a ${nk.gana} gana native, ` + ({ Deva: 'you are naturally gentle, generous and drawn to goodness.', Manushya: 'you balance worldly ambition with kindness and practicality.' }[nk.gana] || 'you are strong-willed, independent and fearless in pursuing your goals.')));
  const asc = c.cusps[0], an = V.NAKS[V.nakIdx(asc.nakshatra)];
  b.push(heading('Ascendant nakshatra - ' + asc.nakshatra));
  b.push(para(`Your ascendant degree lies in ${asc.nakshatra} (lord ${asc.starLord}), giving a personality that is ${an.traits}. The ascendant sub lord ${asc.subLord} is, in KP, the key to your health, nature and overall direction.`));
  const sun = planetOf(c, 'Sun');
  b.push(heading('Sun nakshatra - ' + sun.nakshatra));
  b.push(para(`Your Sun is in ${sun.nakshatra} (lord ${sun.starLord}): your soul's purpose expresses itself in ways that are ${V.NAKS[V.nakIdx(sun.nakshatra)].traits}.`));
  b.push(chapter('Ascendant & Personality', c.ascendant.sign + ' rising'));
  b.push(para(`${V.SIGN_TRAITS[c.ascendant.signIdx]}\n\nWith ${c.ascendant.sign} rising at ${c.ascendant.dms}, the ascendant sign lord ${asc.signLord} shapes your body and temperament, the star lord ${asc.starLord} shows the results you seek through life, and the sub lord ${asc.subLord} decides health and success of the self.\n\n` + cuspText(c, 1)));
  b.push(heading('Element and quality'));
  const ms = Math.floor(moon.longitude / 30);
  b.push(para(`Your ascendant is a ${V.QUALITY[c.ascendant.signIdx % 3].toLowerCase()} ${V.ELEMENT[c.ascendant.signIdx % 4].toLowerCase()} sign. Your Moon sign ${c.moonSign} is a ${V.QUALITY[ms % 3].toLowerCase()} ${V.ELEMENT[ms % 4].toLowerCase()} sign. ${V.SIGN_TRAITS[ms]}`));

  // 8. Planet chapters
  for (const p of c.planets) {
    const pi = V.PLANET[p.name], dig = V.dignity(p.name, p.sign, p.signLord);
    b.push(chapter(p.name + ' in Your Chart', `${p.sign} · ${ord(p.house)} house · ${p.nakshatra}`));
    b.push(kv([['Position', p.sign + ' ' + p.dms + (p.retrograde ? ' (retrograde)' : '')], ['House (Placidus)', `${ord(p.house)} - ${V.HOUSE_NAME[p.house - 1]}`], ['Nakshatra / pada', `${p.nakshatra} / ${p.pada}`],
      ['Sign / Star / Sub / Sub-sub lord', `${p.signLord} / ${p.starLord} / ${p.subLord} / ${p.subSubLord}`], ['Dignity', dig], ['Houses signified', houses(housesSignified(c, p.name))], ['Significations (karaka)', pi.karaka]]));
    b.push(heading('Placement'));
    b.push(para(`${p.name} represents ${pi.karaka}. In your chart it sits in ${p.sign}. ${V.SIGN_TRAITS[Math.floor(p.longitude / 30)]} ${V.dignityText(dig)}\n\nPlaced in your ${ord(p.house)} house, ${p.name} brings its energy into ${V.HOUSE_DETAIL[p.house - 1]}.`
      + (p.retrograde ? ' Being retrograde, its results are more internal and may come after review, delay or repetition - often deeper and more lasting.' : '')));
    const star = planetOf(c, p.starLord);
    b.push(heading('KP judgement - star lord and sub lord'));
    b.push(para(`In KP a planet gives the results of its star lord. ${p.name} is in the star of ${p.starLord} (in ${star.sign}, ${ord(star.house)} house), so it delivers results of houses ${houses(housesSignified(c, p.name))}. The sub lord ${p.subLord} decides whether those results are favourable: ${p.subLord} signifies houses ${houses(housesSignified(c, p.subLord))}.\n\nThe nakshatra ${p.nakshatra} adds qualities that are ${V.NAKS[V.nakIdx(p.nakshatra)].traits}.`));
    const md = c.dasha.periods.find((x) => x.lord === p.name);
    if (md) {
      b.push(heading(p.name + ' Mahadasha'));
      b.push(para(`Your ${p.name} Mahadasha runs from ${md.start} to ${md.end} (${md.years} years). It emphasises ${V.DASHA_THEME[p.name]}, and activates houses ${houses(housesSignified(c, p.name))} in your life.`));
    }
    b.push(heading('Strengthening ' + p.name));
    b.push(kv([['Gemstone', pi.gem + ' (wear only after personal consultation)'], ['Mantra', pi.mantra], ['Day', pi.day], ['Colour', pi.color], ['Deity', pi.deity], ['Body areas', pi.body]]));
  }

  // 9. House chapters
  for (let h = 1; h <= 12; h++) {
    const cu = c.cusps[h - 1], hs = c.houseSignificators[h - 1];
    b.push(chapter(`${ord(h)} House - ${V.HOUSE_NAME[h - 1]}`, `Cusp in ${cu.sign} ${cu.dms}`));
    b.push(para(`The ${ord(h)} house governs ${V.HOUSE_DETAIL[h - 1]}.`));
    b.push(kv([['Cusp', `${cu.sign} ${cu.dms} (${cu.nakshatra}-${cu.pada})`], ['Sign / Star / Sub / Sub-sub lord', `${cu.signLord} / ${cu.starLord} / ${cu.subLord} / ${cu.subSubLord}`],
      ['Sub lord signifies', houses(housesSignified(c, cu.subLord))], ['Significators A / B / C / D', [hs.A, hs.B, hs.C, hs.D].map((l) => (l.length ? l.join(', ') : '—')).join('  /  ')]]));
    b.push(heading('KP reading'));
    b.push(para(cuspText(c, h)));
    const sig = new Set([...hs.A, ...hs.B, ...hs.C, ...hs.D]);
    b.push(heading('When this house is activated'));
    const act = [];
    outer: for (const md of c.dasha.periods) {
      if (md.end < today) continue;
      for (const ad of md.antardashas) {
        if (ad.end < today) continue;
        if (sig.has(ad.lord) || sig.has(md.lord)) act.push(row(`${md.lord} - ${ad.lord}`, ad.start, ad.end));
        if (act.length >= 8) break outer;
      }
    }
    b.push(act.length ? table(['Dasha - Bhukti', 'From', 'To'], act, [40, 30, 30]) : para('No significator periods are due in the near future; this house works quietly in the background.'));
  }

  // 10. Consultation + 11. life areas
  b.push(chapter('Your Consultation', topic || 'General life reading'));
  b.push(para(content.summary));
  for (const s of content.consultation) { b.push(heading(s.title)); b.push(para(s.content)); }
  for (const s of content.sections) { b.push(chapter(s.title, 'Life area analysis')); b.push(para(s.content)); }

  // 12. Dasha
  b.push(chapter('Vimshottari Dasha', 'Your 120-year planetary timeline'));
  b.push(table(['Mahadasha', 'Years', 'From', 'To', 'Signifies houses'], c.dasha.periods.map((p) => row(p.lord, p.years, p.start, p.end, houses(housesSignified(c, p.lord)))), [18, 10, 22, 22, 28]));
  b.push(para(`Balance of dasha at birth: ${c.dasha.balance.lord} ${c.dasha.balance.years.toFixed(2)} years. Currently running: ${c.currentDasha.mahadasha} Mahadasha / ${c.currentDasha.antardasha} Antardasha (until ${c.currentDasha.adEnd}).`));
  for (const md of c.dasha.periods) {
    b.push(chapter(md.lord + ' Mahadasha', `${md.start} to ${md.end}`));
    const sig = housesSignified(c, md.lord);
    b.push(para(`This ${md.years}-year period emphasises ${V.DASHA_THEME[md.lord]}. In your chart ${md.lord} signifies houses ${houses(sig)} - so matters of ${sig.slice(0, 4).map((h) => V.HOUSE_DETAIL[h - 1].split(',')[0] + ` (${h})`).join('; ')} come to the fore.`
      + (sig.includes(11) || sig.includes(10) || sig.includes(2) ? ' The link with gain houses makes this a productive, rewarding period.' : '')
      + (sig.includes(8) || sig.includes(12) ? ' The link with houses 8/12 calls for careful planning of expenses and health.' : '')));
    b.push(table(['Antardasha', 'From', 'To', 'Houses', 'Theme'], md.antardashas.map((ad) => row(`${md.lord} - ${ad.lord}`, ad.start, ad.end, houses(housesSignified(c, ad.lord)), antarNote(c, md.lord, ad.lord))), [18, 14, 14, 16, 38]));
  }

  // 13. Pratyantar for current + next mahadasha
  let cur = 0;
  c.dasha.periods.forEach((p, i) => { if (p.start <= today && today < p.end) cur = i; });
  for (const md of c.dasha.periods.slice(cur, cur + 2)) {
    b.push(chapter(`Pratyantar Dasha - ${md.lord} Mahadasha`, 'Sub-sub periods for precise timing'));
    for (const ad of md.antardashas) {
      b.push(heading(`${md.lord} - ${ad.lord}  (${ad.start} to ${ad.end})`));
      b.push(table(['Pratyantar', 'From', 'To'], pratyantar(ad).map(([l, a, z]) => row(`${md.lord}-${ad.lord}-${l}`, a, z)), [40, 30, 30]));
    }
  }

  // 14. Yearly forecast (12 years)
  const year0 = new Date().getFullYear();
  for (let y = year0; y < year0 + 12; y++) {
    const mid = new Date(Date.UTC(y, 6, 1));
    const jup = V.transitSign('Jupiter', mid), sat = V.transitSign('Saturn', mid);
    const jFrom = ((jup - ms + 12) % 12) + 1, sFrom = ((sat - ms + 12) % 12) + 1;
    b.push(chapter(`Year ${y} Forecast`, 'Dasha periods and major transits'));
    const ys = `${y}-01-01`, ye = `${y}-12-31`, yr = [], pt = [];
    for (const md of c.dasha.periods) for (const ad of md.antardashas) {
      if (ad.start > ye || ad.end < ys) continue;
      yr.push(row(`${md.lord} - ${ad.lord}`, ad.start, ad.end, antarNote(c, md.lord, ad.lord)));
      for (const [l, a, z] of pratyantar(ad)) if (a <= ye && z >= ys) pt.push(row(`${md.lord}-${ad.lord}-${l}`, a, z, pdNote(c, l)));
    }
    b.push(table(['Running period', 'From', 'To', 'Theme'], yr, [22, 16, 16, 46]));
    b.push(kv([['Jupiter transit', `${SIGNS[jup]} - ${ord(jFrom)} from your Moon`], ['Saturn transit', `${SIGNS[sat]} - ${ord(sFrom)} from your Moon`]]));
    b.push(para(`Jupiter: ${V.jupiterTransit(jFrom)}.\n\nSaturn: ${V.saturnTransit(sFrom)}.\n\n` + yearSummary(yr.length ? yr[0][0] : null, jFrom, sFrom, y)));
    if (pt.length) { b.push(heading('Period-by-period timing in ' + y)); b.push(table(['Pratyantar', 'From', 'To', 'Indication'], pt, [28, 16, 16, 40])); }
  }

  // 15. Remedies & lucky factors
  b.push(chapter('Remedies & Lucky Factors', 'Simple, supportive practices'));
  b.push(table(['Planet', 'Gemstone', 'Day', 'Colour', 'Deity'], Object.entries(V.PLANET).map(([k, v]) => row(k, v.gem, v.day, v.color, v.deity)), [14, 26, 16, 22, 22]));
  const weak = c.planets.filter((p) => V.dignity(p.name, p.sign, p.signLord) === 'Debilitated').map((p) => p.name);
  const k1 = c.ascendant.subLord, k2 = c.rulingPlanets.moonStarLord;
  b.push(heading('Personal recommendations'));
  b.push({ t: 'bullets', items: [
    `Strengthen ${k1} (your ascendant sub lord): chant "${V.PLANET[k1].mantra}" 108 times on ${V.PLANET[k1].day}s.`,
    `Support ${k2} (your Moon's star lord) by wearing ${V.PLANET[k2].color.toLowerCase()} on ${V.PLANET[k2].day}s and doing charity on that day.`,
    weak.length ? `Debilitated planet(s) ${weak.join(', ')}: serve the people they signify and worship their deity; avoid wearing their gemstone without consultation.` : 'No planet is debilitated in your chart - maintain your strengths with gratitude and discipline.',
    "Respect teachers and elders for Jupiter's grace; keep a regular routine for Saturn; spend a few minutes in meditation daily to steady the Moon (the mind).",
    'Begin important work during the favourable dasha windows shown in this report, ideally on the day of a ruling planet.'] });
  b.push(heading('Lucky factors'));
  b.push(kv([['Lucky numbers', `${V.PLANET[k1].number}, ${V.PLANET[k2].number}, ${V.PLANET[c.ascendant.starLord].number}`], ['Lucky days', `${V.PLANET[k1].day}, ${V.PLANET[k2].day}`],
    ['Lucky colours', `${V.PLANET[k1].color}, ${V.PLANET[k2].color}`], ['Favourable deity', V.PLANET[c.ascendant.starLord].deity]]));
  b.push(note('Gemstones are powerful; wear one only after a personal consultation with an astrologer.'));

  // 16. Method, glossary, closing
  b.push(chapter('How KP Astrology Works', 'The method behind this report'));
  b.push(para('Krishnamurti Paddhati (KP), developed by Prof. K. S. Krishnamurti, refines traditional Vedic astrology to answer two questions precisely: will an event happen, and when?\n\n1. Every planet gives the results of the star (nakshatra) it occupies, and the sub lord decides whether those results are favourable.\n\n2. Each house is judged through its cusp sub lord: if the sub lord signifies the supporting houses, the event is promised.\n\n3. Events happen during the dasha, bhukti and antara of planets that signify the relevant houses, confirmed by the ruling planets of the moment.\n\nThis report was calculated from your exact birth time (to the second) and place using Placidus cusps and the Krishnamurti ayanamsa.'));
  b.push(chapter('Glossary of Terms', 'Quick reference'));
  b.push(table(['Term', 'Meaning'], V.GLOSSARY.map(([a, z]) => row(a, z)), [28, 72]));
  b.push(chapter('Closing Words', 'With blessings'));
  b.push(para(`Dear ${first}, the stars show tendencies and timing - your choices, effort and character shape how these unfold. Use the favourable periods to act boldly, and the challenging ones to prepare, learn and rest. If you have specific questions, our astrologer is available for a personal 30-minute consultation.\n\nMay this report bring you clarity, confidence and peace.`));
  b.push(note('Disclaimer: astrology offers guidance based on traditional principles. It is not a substitute for medical, legal or financial advice.'));
  return b;
}

// ---------------------------------------------------------------- translation of blocks
const paras = (text, fn) => String(text).split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean).map(fn).join('\n\n');

function mapBlocks(blocks, hook, fn) {
  const out = blocks.map((b) => {
    switch (b.t) {
      case 'chapter': return { ...b, title: fn(b.title), subtitle: b.subtitle ? fn(b.subtitle) : b.subtitle };
      case 'heading': return { ...b, text: fn(b.text) };
      case 'para': return { ...b, text: paras(b.text, fn) };
      case 'note': return { ...b, text: fn(b.text) };
      case 'bullets': return { ...b, items: b.items.map(fn) };
      case 'table': return { ...b, headers: b.headers.map(fn), rows: b.rows.map((r) => r.map(fn)) };
      case 'kv': return { ...b, pairs: b.pairs.map(([k, v]) => [fn(k), fn(v)]) };
      case 'charts': return { ...b, charts: b.charts.map((g) => ({ ...g, title: fn(g.title), subtitle: fn(g.subtitle) })) };
      default: return b;
    }
  });
  const h = hook ? { headline: fn(hook.headline), teaser: paras(hook.teaser, fn), secrets: (hook.secrets || []).map(fn) } : null;
  return { blocks: out, hook: h };
}

/** All texts of the report (paragraphs split on blank lines). */
export function texts(blocks, hook) {
  const s = new Set();
  mapBlocks(blocks, hook, (t) => { s.add(t); return t; });
  return [...s];
}
export const applyTranslations = (blocks, hook, tr) => mapBlocks(blocks, hook, (t) => tr[t] ?? t);
