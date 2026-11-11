// The Express app: used by Vercel (api/index.js) and locally (server.js).
import express from 'express';
import cookieParser from 'cookie-parser';
import { connect } from './db.js';
import publicRoutes, { seedAdmin } from './routes/public.js';
import clientRoutes from './routes/client.js';
import payRoutes from './routes/pay.js';
import adminRoutes from './routes/admin.js';

// Database schema + admin account, once per server start (cold start on Vercel)
let ready = null;
const init = () => {
  if (!ready) ready = connect().then(seedAdmin).catch((e) => { ready = null; throw e; });
  return ready;
};

export function createApp() {
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', true);
  app.use((req, res, next) => { res.set('X-Content-Type-Options', 'nosniff'); next(); });
  // The Razorpay webhook signature is computed over the raw body
  app.use('/api/razorpay/webhook', express.raw({ type: '*/*', limit: '1mb' }));
  app.use(express.json({ limit: '6mb' })); // browser translations of a 100-page report are large
  app.use(cookieParser());
  app.use('/api', (req, res, next) => init().then(() => next(), (e) => {
    console.error('startup failed:', e);
    res.status(503).json({ error: /DATABASE_URL/.test(e.message) ? 'The database is not connected yet. In Vercel open Storage, connect a Neon database to this project, then redeploy.'
      : 'Could not connect to the database: ' + e.message });
  }));
  app.use('/api', (req, res, next) => { res.set('Cache-Control', 'no-store'); next(); });

  app.use('/api', publicRoutes);
  app.use('/api/my', clientRoutes);
  app.use('/api/pay', payRoutes);
  app.use('/api/admin', adminRoutes);
  app.use('/api', (req, res) => res.status(404).json({ error: 'Not found' }));
  if (process.env.VERCEL) app.get('/', (req, res) => res.json({ ok: true, service: 'Astrology Talk API', health: '/api/health' }));

  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    if (err && err.type === 'entity.too.large') return res.status(413).json({ error: 'Request too large' });
    if (err && err.type === 'entity.parse.failed') return res.status(400).json({ error: 'Invalid request' });
    const status = err && Number.isInteger(err.status) ? err.status : 500;
    if (status >= 500) console.error(err);
    res.status(status).json({ error: status >= 500 ? 'Something went wrong on the server. Please try again.' : err.message });
  });
  return app;
}

export { init };
