// Run work after the response has been sent. On Vercel this uses waitUntil() so the function
// is kept alive until the work finishes; locally the promise simply runs.
import { waitUntil } from '@vercel/functions';

export function background(task) {
  const p = Promise.resolve().then(task).catch((e) => console.error('[background]', e));
  try { waitUntil(p); } catch { /* not on Vercel */ }
  return p;
}
