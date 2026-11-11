// Developer check: builds a sample report and writes PDFs to ./data/test-*.pdf
import fs from 'fs';
import { computeChart } from '../src/kp/engine.js';
import * as astro from '../src/astrologer.js';
import { build } from '../src/report/builder.js';
import { renderFull, renderPreview, closeBrowser } from '../src/report/pdf.js';

const lang = process.argv[2] || 'en';
const u = { id: 'test', fullName: 'Ravi Kumar', gender: 'male', dob: '1990-01-01', tob: '12:00:00', city: 'New Delhi, Delhi, India', lat: 28.6139, lon: 77.209, tz: 5.5, tzName: 'Asia/Kolkata' };
const c = computeChart(u);
const content = astro.report(u, c, ['Studying engineering'], [{ role: 'user', text: 'career help' }], 'Career & Profession');
const blocks = build(u, c, content, 'Career & Profession');
const labels = lang === 'te' ? { Contents: 'విషయ సూచిక', 'KP Horoscope Report': 'కేపీ జాతక నివేదిక', 'Prepared for': 'వీరి కోసం' } : null;
let t = Date.now();
const full = await renderFull({ person: u, chart: c, hook: content.hook, blocks, site: 'Astrology Talk', language: lang, labels });
console.log('full', full.pages, 'pages', (full.pdf.length / 1024).toFixed(0) + ' KB', Date.now() - t, 'ms');
t = Date.now();
const prev = await renderPreview({ person: u, chart: c, hook: content.hook, site: 'Astrology Talk', language: lang, labels, pages: full.pages });
console.log('preview', (prev.length / 1024).toFixed(0) + ' KB', Date.now() - t, 'ms');
fs.mkdirSync('data', { recursive: true });
fs.writeFileSync(`data/test-full-${lang}.pdf`, full.pdf);
fs.writeFileSync(`data/test-preview-${lang}.pdf`, prev);
await closeBrowser();
