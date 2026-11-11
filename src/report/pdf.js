// Report PDF rendering with headless Chromium (perfect shaping for Telugu, Hindi, Tamil, Urdu, Chinese ...
// through Google Noto fonts) + pdf-lib for the page frame, contents page numbers and the locked preview.
//
// renderFull()    -> the complete paid report (90+ pages)
// renderPreview() -> page 1 (hook) is real; every other page is a blurred placeholder that shows only its
//                    page number. Locked content is never included in the preview file.
import fs from 'fs';
import puppeteer from 'puppeteer-core';
import { PDFDocument, PDFName, PDFArray, PDFDict, PDFRef, PDFNumber, StandardFonts, rgb } from 'pdf-lib';
import { lang as findLang } from '../i18n.js';

// ---------------------------------------------------------------- colours / page geometry (points)
const HEX = { ORANGE: '#ea580c', AMBER: '#f59e0b', DEEP: '#7c2d12', INK: '#2b1606', SOFT: '#fff7ed', LINE: '#fed7aa', MUTED: '#9a3412', ASC: '#ffedd5' };
const C = Object.fromEntries(Object.entries(HEX).map(([k, h]) => [k, rgb(parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255)]));
const W = 595.28, H = 841.89, M = 50;

/** Fixed labels drawn by the renderer itself (translated together with the report text). */
export const LABELS = ['Contents', 'Everything inside your KP horoscope', 'KP Horoscope Report', 'Prepared for', 'Name', 'Date of Birth', 'Time of Birth', 'Place',
  'Ascendant', 'Moon / Nakshatra', 'Ayanamsa', 'Running Dasha', 'Rasi Chart', 'What your stars are saying...', 'LOCKED INSIDE YOUR FULL REPORT', 'INSIDE THIS REPORT',
  'Unlock all pages of your report in your portal'];

// ---------------------------------------------------------------- browser
let browserP = null;
let queue = Promise.resolve();

function localChrome() {
  const c = [process.env.CHROME_PATH,
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', 'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser', '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'];
  return c.find((p) => p && fs.existsSync(p));
}

async function launch() {
  if (process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME) {
    const chromium = (await import('@sparticuz/chromium')).default;
    return puppeteer.launch({ args: chromium.args, executablePath: await chromium.executablePath(), headless: true });
  }
  const exe = localChrome();
  if (!exe) throw new Error('No Chrome / Edge found for PDF rendering. Set CHROME_PATH to a Chrome or Edge executable.');
  return puppeteer.launch({ executablePath: exe, headless: true, args: ['--no-sandbox', '--disable-gpu', '--font-render-hinting=none'] });
}

async function browser() {
  if (browserP) {
    const b = await browserP.catch(() => null);
    if (b && b.connected) return b;
  }
  browserP = launch();
  return browserP;
}

/** HTML -> PDF bytes. Renders run one at a time (Chromium is memory-hungry on serverless). */
function htmlToPdf(html) {
  const job = queue.then(async () => {
    const b = await browser();
    const page = await b.newPage();
    try {
      await page.setContent(html, { waitUntil: 'networkidle0', timeout: 45000 });
      await page.evaluate(() => document.fonts.ready.then(() => true));
      return await page.pdf({ preferCSSPageSize: true, printBackground: true, timeout: 120000 });
    } finally { await page.close().catch(() => {}); }
  });
  queue = job.catch(() => {});
  return job;
}

export async function closeBrowser() { if (browserP) { const b = await browserP.catch(() => null); browserP = null; if (b) await b.close().catch(() => {}); } }

// ---------------------------------------------------------------- html helpers
const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const nl = (s) => esc(s).replace(/\n/g, '<br>');
const SHORT = ['Ari', 'Tau', 'Gem', 'Can', 'Leo', 'Vir', 'Lib', 'Sco', 'Sag', 'Cap', 'Aqu', 'Pis'];
const SI_ORDER = [11, 0, 1, 2, 10, -1, 3, 9, 4, 8, 7, 6, 5]; // -1 = centre block

function fontLinks(l) {
  const fams = ['Noto Sans'];
  if (l && l.fontFamily && !fams.includes(l.fontFamily)) fams.push(l.fontFamily);
  // one stylesheet per family, so a family without a bold weight cannot break the others
  return fams.map((f) => `<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=${encodeURIComponent(f).replace(/%20/g, '+')}:wght@400;700&display=block">`
    + `<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=${encodeURIComponent(f).replace(/%20/g, '+')}&display=block">`).join('');
}

function css(l) {
  const family = l && l.fontFamily !== 'Noto Sans' ? `'${l.fontFamily}', 'Noto Sans'` : "'Noto Sans'";
  const align = !l || l.code === 'en' ? 'justify' : l.rtl ? 'right' : 'left';
  return `
  @page { size: A4; margin: 24mm 17mm 17mm 17mm; }
  @page :first { margin: 0; }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; }
  body { font-family: ${family}, sans-serif; color: ${HEX.INK}; font-size: 10.5pt; line-height: 1.5; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  p { margin: 0 0 7pt; text-align: ${align}; orphans: 3; widows: 3; }
  .band { break-before: page; background: ${HEX.ORANGE}; color: #fff; padding: 11pt 13pt 13pt; border-bottom: 3pt solid ${HEX.AMBER}; margin-bottom: 14pt; break-after: avoid; }
  .band .t { font-size: 17pt; font-weight: 700; line-height: 1.25; }
  .band .s { font-size: 9.5pt; color: ${HEX.ASC}; margin-top: 3pt; }
  h2 { font-size: 13pt; color: ${HEX.DEEP}; background: ${HEX.SOFT}; border-left: 4pt solid ${HEX.ORANGE}; padding: 5pt 9pt; margin: 10pt 0 8pt; break-after: avoid; font-weight: 700; line-height: 1.3; }
  table { width: 100%; border-collapse: collapse; margin: 0 0 10pt; font-size: 8.5pt; line-height: 1.35; }
  th, td { border: 0.5pt solid ${HEX.LINE}; padding: 4pt 5pt; text-align: start; vertical-align: top; }
  th { background: ${HEX.ORANGE}; color: #fff; font-weight: 700; }
  thead { display: table-header-group; }
  tr { break-inside: avoid; }
  tbody tr:nth-child(odd) td { background: ${HEX.SOFT}; }
  table.kv td { font-size: 9pt; }
  table.kv td.k { color: ${HEX.MUTED}; width: 34%; }
  table.kv td.v { font-weight: 700; }
  ul { margin: 0 0 8pt; padding-inline-start: 16pt; }
  li { margin-bottom: 4pt; text-align: ${align}; }
  li::marker { color: ${HEX.ORANGE}; }
  .note { font-style: italic; color: ${HEX.MUTED}; font-size: 8.5pt; margin: 6pt 0; text-align: start; }
  .charts { display: flex; gap: 12pt; margin: 0 0 12pt; break-inside: avoid; }
  .charts.one { width: 62%; margin-left: auto; margin-right: auto; }
  .charts > .si { flex: 1; }
  .si { display: grid; grid-template-columns: repeat(4, 1fr); border: 0.8pt solid ${HEX.ORANGE}; }
  .si .cell { border: 0.8pt solid ${HEX.ORANGE}; padding: 3pt; overflow: hidden; }
  .si .cell .sg { font-size: 6.5pt; color: ${HEX.MUTED}; }
  .si .cell.asc { background: ${HEX.ASC}; }
  .si .cell.asc .sg { color: ${HEX.ORANGE}; font-weight: 700; }
  .si .cell .pl { font-size: 8.5pt; font-weight: 700; line-height: 1.2; word-spacing: 2pt; }
  .si .mid { grid-column: span 2; grid-row: span 2; background: #fffbf5; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; border: 0.8pt solid ${HEX.ORANGE}; padding: 4pt; }
  .si .mid .a { font-size: 11pt; font-weight: 700; color: ${HEX.ORANGE}; line-height: 1.2; }
  .si .mid .b { font-size: 8pt; color: ${HEX.MUTED}; }
  .toc { display: block; }
  .toc a { display: flex; justify-content: space-between; color: ${HEX.INK}; text-decoration: none; font-size: 8.5pt; padding: 3.5pt 6pt; border: 0.5pt solid ${HEX.LINE}; border-top: 0; line-height: 1.3; }
  .toc a:first-child { border-top: 0.5pt solid ${HEX.LINE}; }
  .toc a:nth-child(even) { background: ${HEX.SOFT}; }
  .toc a .pg { width: 34pt; flex: none; }
  /* ---- page 1 (hook) ---- */
  .hook { width: 210mm; height: 297mm; position: relative; overflow: hidden; break-after: page; }
  .hero { position: absolute; left: 0; top: 0; width: 100%; height: 190pt; background: linear-gradient(90deg, ${HEX.ORANGE}, ${HEX.AMBER}); color: #fff; padding: 0 ${M}pt; overflow: hidden; }
  .hero .o1, .hero .o2 { position: absolute; border-radius: 50%; background: rgba(255,255,255,.13); }
  .hero .o1 { width: 180pt; height: 180pt; right: -20pt; top: -50pt; }
  .hero .o2 { width: 100pt; height: 100pt; right: -20pt; top: 110pt; }
  .hero .site { font-size: 10pt; margin-top: 36pt; letter-spacing: .5pt; }
  .hero .title { font-size: 26pt; font-weight: 700; line-height: 1.2; margin-top: 4pt; }
  .hero .for { font-size: 13pt; margin-top: 2pt; }
  .hero .birth { font-size: 10pt; margin-top: 2pt; opacity: .95; }
  .hero .head { font-size: 14.5pt; font-weight: 700; line-height: 1.25; margin-top: 8pt; max-height: 38pt; overflow: hidden; }
  .bbox { position: absolute; left: ${M}pt; top: 220pt; width: 255pt; height: 200pt; background: ${HEX.SOFT}; border-radius: 8pt; padding: 12pt 10pt; }
  .bbox .r { display: flex; height: 21pt; align-items: baseline; }
  .bbox .k { width: 90pt; flex: none; font-size: 8.5pt; color: ${HEX.MUTED}; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  .bbox .v { font-size: 9pt; font-weight: 700; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  .hchart { position: absolute; right: ${M}pt; top: 220pt; width: 215pt; }
  .hchart .cell { height: 50pt; }
  .teaser-t { position: absolute; left: ${M}pt; right: ${M}pt; top: 440pt; font-size: 13pt; font-weight: 700; color: ${HEX.DEEP}; }
  .teaser { position: absolute; left: ${M}pt; right: ${M}pt; top: 464pt; height: 136pt; overflow: hidden; }
  .secrets { position: absolute; left: ${M}pt; right: ${M}pt; bottom: 140pt; height: 155pt; border: 1.5pt solid ${HEX.ORANGE}; border-radius: 10pt; padding: 12pt 16pt; overflow: hidden; }
  .secrets .h { font-size: 11.5pt; font-weight: 700; color: ${HEX.ORANGE}; margin-bottom: 8pt; }
  .secrets .i { display: flex; gap: 9pt; font-size: 10pt; margin-bottom: 6pt; line-height: 1.3; }
  .secrets .i:before { content: ''; flex: none; width: 6pt; height: 6pt; border-radius: 50%; background: ${HEX.AMBER}; margin-top: 4pt; }
  .unlock { position: absolute; left: 50%; transform: translateX(-50%); bottom: 92pt; width: 300pt; height: 34pt; border-radius: 17pt; background: ${HEX.ORANGE}; color: #fff; font-weight: 700; font-size: 11pt; display: flex; align-items: center; justify-content: center; text-align: center; padding: 0 10pt; line-height: 1.15; }
  `;
}

function siChart(g, cls = '') {
  const cells = SI_ORDER.map((sign) => {
    if (sign === -1) return `<div class="mid"><div class="a">${esc(g.title)}</div><div class="b">${esc(g.subtitle)}</div></div>`;
    const asc = sign === g.asc;
    return `<div class="cell${asc ? ' asc' : ''}"><div class="sg">${SHORT[sign]}${asc ? '&nbsp;&nbsp;Asc' : ''}</div><div class="pl">${esc((g.grid[sign] || []).join(' '))}</div></div>`;
  }).join('');
  return `<div class="si ${cls}">${cells}</div>`;
}

function hookHtml(u, c, hook, site, preview, L) {
  const rows = [[L('Name'), u.fullName], [L('Date of Birth'), u.dob], [L('Time of Birth'), u.tob], [L('Place'), u.city], [L('Ascendant'), `${c.ascendant.sign} ${c.ascendant.dms}`],
    [L('Moon / Nakshatra'), `${c.moonSign} / ${c.nakshatra}`], [L('Ayanamsa'), 'KP ' + c.ayanamsa.dms], [L('Running Dasha'), `${c.currentDasha.mahadasha} / ${c.currentDasha.antardasha}`]];
  const secrets = (hook.secrets || []).slice(0, 4);
  return `<section class="hook">
    <div class="hero"><div class="o1"></div><div class="o2"></div>
      <div class="site">${esc(String(site).toUpperCase())}&nbsp;&nbsp;|&nbsp;&nbsp;KRISHNAMURTI PADDHATI</div>
      <div class="title">${esc(L('KP Horoscope Report'))}</div>
      <div class="for">${esc(L('Prepared for'))} ${esc(u.fullName)}</div>
      <div class="birth">${esc(u.dob)}&nbsp;&nbsp;|&nbsp;&nbsp;${esc(u.tob)}&nbsp;&nbsp;|&nbsp;&nbsp;${esc(u.city)}</div>
      <div class="head">${esc(hook.headline)}</div>
    </div>
    <div class="bbox">${rows.map(([k, v]) => `<div class="r"><div class="k">${esc(k)}</div><div class="v">${esc(v)}</div></div>`).join('')}</div>
    <div class="hchart">${siChart({ title: L('Rasi Chart'), subtitle: 'KP / Sidereal', grid: c.rasi, asc: c.ascendant.signIdx })}</div>
    <div class="teaser-t">${esc(L('What your stars are saying...'))}</div>
    <div class="teaser">${String(hook.teaser || '').split(/\n\s*\n/).map((p) => `<p>${nl(p.trim())}</p>`).join('')}</div>
    <div class="secrets"><div class="h">${esc(preview ? L('LOCKED INSIDE YOUR FULL REPORT') : L('INSIDE THIS REPORT'))}</div>
      ${secrets.map((s) => `<div class="i"><div>${esc(s)}</div></div>`).join('')}</div>
    ${preview ? `<div class="unlock">${esc(L('Unlock all pages of your report in your portal'))}</div>` : ''}
  </section>`;
}

function blockHtml(b, chapterNo) {
  switch (b.t) {
    case 'chapter': return `<div class="band" id="c${chapterNo}"><div class="t">${esc(b.title)}</div>${b.subtitle ? `<div class="s">${esc(b.subtitle)}</div>` : ''}</div>`;
    case 'heading': return `<h2>${esc(b.text)}</h2>`;
    case 'para': return String(b.text).split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean).map((p) => `<p>${nl(p)}</p>`).join('');
    case 'bullets': return `<ul>${b.items.map((i) => `<li>${esc(i)}</li>`).join('')}</ul>`;
    case 'note': return `<div class="note">${nl(b.text)}</div>`;
    case 'kv': return `<table class="kv"><tbody>${b.pairs.map(([k, v]) => `<tr><td class="k">${esc(k)}</td><td class="v">${esc(v)}</td></tr>`).join('')}</tbody></table>`;
    case 'table': {
      const sum = (b.widths || []).reduce((a, x) => a + x, 0) || 1;
      const cols = b.widths ? `<colgroup>${b.widths.map((w) => `<col style="width:${((w / sum) * 100).toFixed(2)}%">`).join('')}</colgroup>` : '';
      return `<table>${cols}<thead><tr>${b.headers.map((h) => `<th>${esc(h)}</th>`).join('')}</tr></thead><tbody>${b.rows.map((r) => `<tr>${r.map((v) => `<td>${esc(v)}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
    }
    case 'charts': return `<div class="charts${b.charts.length === 1 ? ' one' : ''}">${b.charts.map((g) => siChart(g)).join('')}</div>`;
    default: return '';
  }
}

function page(l, body) {
  return `<!doctype html><html lang="${esc(l ? l.code : 'en')}" dir="${l && l.rtl ? 'rtl' : 'ltr'}"><head><meta charset="utf-8">${fontLinks(l)}<style>${css(l)}</style></head><body>${body}</body></html>`;
}

const labeler = (tr) => (en) => (tr && tr[en]) || en;

// ---------------------------------------------------------------- pdf-lib helpers
const ascii = (s) => String(s ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^\x20-\x7e]/g, '');

/** Page index of every chapter, read from the contents page links ("#c12" -> page of chapter 12). */
function chapterPages(doc) {
  const pages = doc.getPages();
  const idx = new Map(pages.map((p, i) => [p.ref.toString(), i]));
  const catalog = doc.catalog;
  const ctx = doc.context;
  const named = new Map();
  // Named destinations: /Dests dictionary or /Names /Dests name tree
  const destsDict = catalog.lookupMaybe(PDFName.of('Dests'), PDFDict);
  if (destsDict) for (const [k, v] of destsDict.entries()) named.set(decodeName(k), v);
  const names = catalog.lookupMaybe(PDFName.of('Names'), PDFDict);
  const tree = names && names.lookupMaybe(PDFName.of('Dests'), PDFDict);
  const walk = (node) => {
    if (!node) return;
    const arr = node.lookupMaybe(PDFName.of('Names'), PDFArray);
    if (arr) for (let i = 0; i + 1 < arr.size(); i += 2) named.set(strOf(arr.lookup(i)), arr.get(i + 1));
    const kids = node.lookupMaybe(PDFName.of('Kids'), PDFArray);
    if (kids) for (let i = 0; i < kids.size(); i++) walk(kids.lookup(i, PDFDict));
  };
  walk(tree);
  const pageOfDest = (d) => {
    let v = d instanceof PDFRef ? ctx.lookup(d) : d;
    if (v && !(v instanceof PDFArray) && !(v instanceof PDFDict)) v = named.get(strOf(v));
    if (v instanceof PDFRef) v = ctx.lookup(v);
    if (v instanceof PDFDict) v = v.lookup(PDFName.of('D'));
    if (v instanceof PDFArray) { const r = v.get(0); return r instanceof PDFRef ? idx.get(r.toString()) : null; }
    return null;
  };
  const links = [];
  pages.forEach((p, pi) => {
    const annots = p.node.lookupMaybe(PDFName.of('Annots'), PDFArray);
    if (!annots) return;
    for (let i = 0; i < annots.size(); i++) {
      const a = annots.lookup(i, PDFDict);
      if (String(a.get(PDFName.of('Subtype'))) !== '/Link') continue;
      let dest = a.get(PDFName.of('Dest'));
      let name = dest && !(dest instanceof PDFArray) && !(dest instanceof PDFRef) ? strOf(dest) : null;
      if (!dest) {
        const act = a.lookupMaybe(PDFName.of('A'), PDFDict);
        if (act) { dest = act.get(PDFName.of('D')); if (dest && !(dest instanceof PDFArray) && !(dest instanceof PDFRef)) name = strOf(dest); }
      }
      if (!dest) continue;
      const target = pageOfDest(dest);
      const rect = a.lookup(PDFName.of('Rect'), PDFArray).asArray().map((n) => (n instanceof PDFNumber ? n.asNumber() : Number(ctx.lookup(n))));
      links.push({ page: pi, name, target, rect });
    }
  });
  return links;
}
function decodeName(n) { return String(n).replace(/^\//, '').replace(/#([0-9a-fA-F]{2})/g, (_, h) => String.fromCharCode(parseInt(h, 16))); }
function strOf(v) {
  if (!v) return '';
  if (typeof v.decodeText === 'function') { try { return v.decodeText(); } catch { /* fallthrough */ } }
  return decodeName(v.toString()).replace(/^\(|\)$/g, '');
}

async function frame(doc, site, label, total) {
  const font = await doc.embedFont(StandardFonts.Helvetica);
  const siteA = ascii(site) || 'Astrology Talk';
  doc.getPages().forEach((p, i) => {
    const n = i + 1;
    if (n > 1) {
      p.drawRectangle({ x: 0, y: H - 40, width: W, height: 40, color: C.ORANGE });
      p.drawRectangle({ x: 0, y: H - 43, width: W, height: 3, color: C.AMBER });
      p.drawText(siteA.toUpperCase(), { x: M, y: H - 25, size: 11, font, color: rgb(1, 1, 1) });
      const r = 'KP Horoscope - Krishnamurti Paddhati';
      p.drawText(r, { x: W - M - font.widthOfTextAtSize(r, 8.5), y: H - 24, size: 8.5, font, color: rgb(1, 1, 1) });
    }
    const t = `Page ${n} of ${total}`;
    p.drawText(t, { x: W / 2 - font.widthOfTextAtSize(t, 8.5) / 2, y: 26, size: 8.5, font, color: C.MUTED });
    p.drawText(ascii(label), { x: M, y: 26, size: 8.5, font, color: C.MUTED });
  });
}

// ---------------------------------------------------------------- public API
/** Full report: { pdf: Uint8Array, pages } */
export async function renderFull({ person, chart, hook, blocks, site, language = 'en', labels = null }) {
  const l = findLang(language) || findLang('en');
  const L = labeler(labels);
  const chapters = blocks.filter((b) => b.t === 'chapter');
  const toc = `<div class="band" id="toc"><div class="t">${esc(L('Contents'))}</div><div class="s">${esc(L('Everything inside your KP horoscope'))}</div></div>
    <div class="toc">${chapters.map((ch, i) => `<a href="#c${i + 1}"><span>${esc(ch.title)}</span><span class="pg"></span></a>`).join('')}</div>`;
  let n = 0;
  const body = blocks.map((b) => blockHtml(b, b.t === 'chapter' ? ++n : n)).join('\n');
  const raw = await htmlToPdf(page(l, hookHtml(person, chart, hook, site, false, L) + toc + body));

  const doc = await PDFDocument.load(raw);
  doc.setTitle('KP Horoscope - ' + ascii(person.fullName));
  doc.setAuthor(ascii(site));
  doc.setCreator(ascii(site));
  doc.setProducer(ascii(site));
  const total = doc.getPageCount();

  // Contents page numbers, drawn into the empty right-hand cell of every contents row
  const bold = await doc.embedFont(StandardFonts.HelveticaBold);
  const links = chapterPages(doc);
  const tocLinks = links.filter((k) => (k.name ? /^c\d+$/.test(k.name) : true) && k.target != null && k.page < 6);
  const rtl = !!(l && l.rtl);
  tocLinks.forEach((k, order) => {
    const no = k.name ? Number(k.name.slice(1)) : order + 1;
    if (!no || no > chapters.length) return;
    const pg = String(k.target + 1);
    const [x1, y1, x2, y2] = k.rect;
    const size = 8.5, tw = bold.widthOfTextAtSize(pg, size);
    const x = rtl ? Math.min(x1, x2) + 8 : Math.max(x1, x2) - 8 - tw;
    const y = (Math.min(y1, y2) + Math.max(y1, y2)) / 2 - size * 0.35;
    doc.getPage(k.page).drawText(pg, { x, y, size, font: bold, color: C.INK });
  });

  await frame(doc, site, site, total);
  return { pdf: await doc.save(), pages: total };
}

// Seeded random so the same report always gets the same blurred pages
function rng(seed) {
  let s = 0;
  for (const ch of String(seed)) s = (s * 31 + ch.charCodeAt(0)) | 0;
  return () => { s = (s + 0x6d2b79f5) | 0; let t = Math.imul(s ^ (s >>> 15), 1 | s); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const rounded = (x, y, w, h) => { const r = Math.min(h / 2, w / 2); return `M ${x + r} ${y} L ${x + w - r} ${y} A ${r} ${r} 0 0 1 ${x + w - r} ${y + h} L ${x + r} ${y + h} A ${r} ${r} 0 0 1 ${x + r} ${y} Z`; };

/** One blurred page body (vector, soft-edged bars that look like out-of-focus text, tables and headings). */
function blurBody(p, rnd) {
  const bar = (x, yTop, bw, bh, color) => {
    for (let k = 0; k < 6; k++) { const s = k * 1.6; p.drawSvgPath(rounded(x - s, yTop - s, bw + 2 * s, bh + 2 * s), { x: 0, y: H, color, opacity: 0.065 }); }
  };
  let y = 90; // distance from the top
  while (y < H - 110) {
    const k = rnd();
    if (k < 0.12) { bar(M, y, W - 2 * M, 22, C.ORANGE); y += 40; }
    else if (k < 0.24) { for (let r = 0; r < 7; r++) { bar(M, y, W - 2 * M, 12, r === 0 ? C.ORANGE : C.AMBER); y += 18; } y += 12; }
    else {
      const lines = 4 + Math.floor(rnd() * 5);
      for (let i = 0; i < lines; i++) { const lw = (W - 2 * M) * (i === lines - 1 ? 0.35 + rnd() * 0.4 : 0.88 + rnd() * 0.12); bar(M, y, lw, 7, C.DEEP); y += 15; }
      y += 14;
    }
  }
  p.drawRectangle({ x: 0, y: 0, width: W, height: H - 44, color: C.SOFT, opacity: 0.35 });
}

function lockBadge(p, pageNo, total, bold, font) {
  const cx = W / 2, cy = H / 2 + 20;
  p.drawCircle({ x: cx, y: cy, size: 46, color: C.ORANGE });
  p.drawSvgPath('M -12 -2 L -12 -14 C -12 -30 12 -30 12 -14 L 12 -2', { x: cx, y: cy, borderColor: rgb(1, 1, 1), borderWidth: 5 });
  p.drawRectangle({ x: cx - 18, y: cy - 22, width: 36, height: 26, color: rgb(1, 1, 1) });
  p.drawCircle({ x: cx, y: cy - 9, size: 4, color: C.ORANGE });
  const t1 = `Page ${pageNo}`, t2 = `of ${total}  -  locked`;
  p.drawText(t1, { x: cx - bold.widthOfTextAtSize(t1, 30) / 2, y: cy - 92, size: 30, font: bold, color: C.DEEP });
  p.drawText(t2, { x: cx - font.widthOfTextAtSize(t2, 11) / 2, y: cy - 118, size: 11, font, color: C.MUTED });
}

/** Preview: real page 1 (hook) + blurred locked pages 2..total that show only their page number. */
export async function renderPreview({ person, chart, hook, site, language = 'en', labels = null, pages, seed }) {
  const l = findLang(language) || findLang('en');
  const raw = await htmlToPdf(page(l, hookHtml(person, chart, hook, site, true, labeler(labels))));
  const hookDoc = await PDFDocument.load(raw);

  // Three blurred backgrounds drawn once and reused on every locked page -> small file, fast to load
  const bgDoc = await PDFDocument.create();
  const rnd = rng(seed || person.id || person.fullName);
  for (let k = 0; k < 3; k++) blurBody(bgDoc.addPage([W, H]), rnd);

  const doc = await PDFDocument.create();
  doc.setTitle('KP Horoscope preview - ' + ascii(person.fullName));
  doc.setAuthor(ascii(site));
  doc.setCreator(ascii(site));
  doc.setProducer(ascii(site));
  const [first] = await doc.copyPages(hookDoc, [0]);
  doc.addPage(first);
  const bgs = await doc.embedPages(bgDoc.getPages());
  const font = await doc.embedFont(StandardFonts.Helvetica), bold = await doc.embedFont(StandardFonts.HelveticaBold);
  for (let n = 2; n <= pages; n++) {
    const p = doc.addPage([W, H]);
    p.drawPage(bgs[n % 3], { x: 0, y: 0 });
    lockBadge(p, n, pages, bold, font);
  }
  await frame(doc, site, 'Preview', pages);
  return doc.save({ useObjectStreams: true });
}
