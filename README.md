# Astrology Talk - backend (Node.js, Vercel)

KP horoscope API: registration with auto-generated login, client and admin portals, astrologer chat with memory per
profile, 90+ page KP report PDF (page 1 free, the rest blurred until paid), Razorpay (UPI first), email and WhatsApp
notifications, 62 languages.

- Runs as one Vercel serverless function (`api/index.js`), free Hobby plan, no card needed.
- Data in Postgres: Neon (free) on Vercel, or a built-in database locally.
- PDFs are made with headless Chromium (perfect Telugu, Hindi, Tamil, Urdu ... text with Google Noto fonts) and stored
  in the database. Older PDFs are removed after 14 days and rebuilt automatically when someone opens them.

## Deploy on Vercel (free)

1. **Import the repo**: vercel.com > Add New > Project > import `astro-backend`. Framework preset: **Other**. Deploy.
2. **Database**: in the project open **Storage** > **Create Database** > **Neon** (free) > connect it to this project.
   This adds `DATABASE_URL` automatically.
3. **Environment variables** (Settings > Environment Variables):

   | Name | Value |
   |---|---|
   | `ADMIN_EMAIL` | your admin email |
   | `ADMIN_PASSWORD` | a strong admin password |
   | `JWT_SECRET` | any long random text |
   | `SITE_URL` | your frontend address, e.g. `https://astro-frontend.vercel.app` |

   Email, WhatsApp and Razorpay keys can be entered later in **Admin > Settings > Integrations** (or as variables,
   see `.env.example`).
4. **Redeploy** (Deployments > ... > Redeploy). Open `https://<backend>.vercel.app/api/health` - it shows `"ok": true`.
5. **Birth places** (every Indian village + world towns, about 190 MB): run once from your computer
   ```
   set DATABASE_URL=<the Neon connection string from Vercel > Storage > Neon > .env.local>
   npm install
   npm run places:import
   ```
   Until then, place search still works through OpenStreetMap.
6. **Frontend**: in the `astro-frontend` repo, `vercel.json` must point `/api/...` to this backend's address.

Admin portal: `<frontend>/login.html?admin=1`, login ID `admin` (or `ADMIN_EMAIL`) and `ADMIN_PASSWORD`.

## Run locally

```
npm install
npm start            # http://localhost:8090 (serves ../frontend too)
npm run places:import   # first time only
```

Local PDFs use the installed Microsoft Edge or Google Chrome (`CHROME_PATH` to choose another).

## Layout

| Path | What |
|---|---|
| `api/index.js` | Vercel entry |
| `server.js` | local server |
| `src/app.js` | Express app, error handling |
| `src/routes/` | public, client, pay, admin APIs |
| `src/consult.js` | profiles, chat, memory, reports |
| `src/kp/` | KP engine (Placidus, KP ayanamsa, star/sub lords, significators, dasha, vargas) |
| `src/astrologer.js` | built-in astrologer (interview + answers + report text) |
| `src/report/` | report chapters and PDF rendering |
| `scripts/import-places.js` | GeoNames import |
