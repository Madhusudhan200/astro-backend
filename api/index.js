// Vercel serverless entry: every request is routed here (see vercel.json).
let app = null;

export default async function handler(req, res) {
  try {
    if (!app) app = (await import('../src/app.js')).createApp();
  } catch (e) {
    console.error('API failed to start:', e);
    res.statusCode = 500;
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ error: 'The server failed to start: ' + (e && e.message) }));
    return;
  }
  return app(req, res);
}
