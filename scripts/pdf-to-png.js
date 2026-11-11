// Developer check: node scripts/pdf-to-png.js file.pdf 1,2,3  -> file-p1.png ... (renders with pdf.js in Edge/Chrome)
import fs from 'fs';
import path from 'path';
import puppeteer from 'puppeteer-core';

const [file, list = '1'] = process.argv.slice(2);
const pages = list.split(',').map(Number);
const exe = ['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', 'C:/Program Files/Google/Chrome/Application/chrome.exe'].find((p) => fs.existsSync(p));
const b = await puppeteer.launch({ executablePath: exe, headless: true });
const pg = await b.newPage();
await pg.setViewport({ width: 900, height: 1270 });
await pg.setContent('<html><body style="margin:0"><canvas id="c"></canvas></body></html>');
const lib = fs.readFileSync('node_modules/pdfjs-dist/build/pdf.min.mjs', 'utf8');
const worker = fs.readFileSync('node_modules/pdfjs-dist/build/pdf.worker.min.mjs', 'utf8');
await pg.evaluate(async (lib, worker, data) => {
  const libUrl = URL.createObjectURL(new Blob([lib], { type: 'text/javascript' }));
  const pdfjs = await import(libUrl);
  pdfjs.GlobalWorkerOptions.workerSrc = URL.createObjectURL(new Blob([worker], { type: 'text/javascript' }));
  window.doc = await pdfjs.getDocument({ data: Uint8Array.from(atob(data), (c) => c.charCodeAt(0)) }).promise;
}, lib, worker, fs.readFileSync(file).toString('base64'));
for (const n of pages) {
  await pg.evaluate(async (n) => {
    const p = await window.doc.getPage(n);
    const v = p.getViewport({ scale: 1.5 });
    const c = document.getElementById('c');
    c.width = v.width; c.height = v.height;
    await p.render({ canvasContext: c.getContext('2d'), viewport: v }).promise;
  }, n);
  const out = path.join(path.dirname(file), path.basename(file, '.pdf') + '-p' + n + '.png');
  await (await pg.$('#c')).screenshot({ path: out });
  console.log(out);
}
await b.close();
