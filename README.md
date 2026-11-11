---
title: Astrology Talk Backend
colorFrom: yellow
colorTo: red
sdk: docker
app_port: 7860
pinned: false
---

# Astro-backend — Astrology Talk server (Java 17 + Spring Boot)

The backend for **Astrology Talk**, a KP (Krishnamurti Paddhati) horoscope website. It serves the **Astro-frontend** pages and the `/api` used by them.

## Features
- **KP engine:** planets (Astronomy Engine), Placidus cusps, KP ayanamsa, star/sub/sub-sub lords, significators, ruling planets, Vimshottari dasha, 16 divisional charts, panchang and transits.
- **Birth places:** every Indian city and village plus places worldwide, built from the GeoNames data. The UTC offset is exact for the birth date.
- **Accounts:** registration with login details sent by email and WhatsApp, forgot password with an email code, and family profiles.
- **Astrologer chat:** a 4-question interview (about you, how you think and feel, and one astrology question), then KP analysis, with memory per person. An optional smart-answers key gives free-form answers.
- **Reports:**
  - 90+ page PDF report (OpenPDF), with a free page 1 and blurred locked pages.
  - Reports in 62 languages, using Noto fonts for proper Indian scripts.
- **Payments and notifications:**
  - Razorpay payments (UPI / PhonePe / GPay / cards), with webhook and a gateway dashboard.
  - Email (SMTP / Gmail) and WhatsApp (Meta Cloud API or Twilio) notifications.
- **Admin settings:** the admin can enter integration keys from the portal (Settings → Integrations), with no restart needed.

## Run

You need JDK 17+ and Maven 3.9+.

```
copy .env.example .env        # then edit it
mvn package -DskipTests
java -jar target/kp-jyotish.jar
```

Open http://localhost:8090 (port set by `PORT` in `.env`). The frontend is served from `../frontend`, which is the Astro-frontend repo checked out next to this folder; override this with `FRONTEND_DIR`.

The admin account is created on first start from `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

## Not in git
`.env` holds secrets. `data/` holds the H2 database (customers, chats, saved keys), generated PDFs, fonts and the place database. `target/` holds build output. Back up `data/` yourself.

## Layout
```
src/main/java/com/kpjyotish/
  kp/        KpEngine, Vedic (vargas, panchang, transits)
  places/    PlaceIndex, PlaceBuilder (GeoNames)
  ai/        AiService, FallbackAstrologer (interview + KP analysis)
  pdf/       ReportBuilder, PdfRenderer, PdfFonts, BlockTranslator
  i18n/      Lang (62 languages), Translator
  service/   ConsultService, Notifier, Razorpay, Auth
  web/       REST controllers
```

## Deploy on Render

1. On render.com: **New → Blueprint**, then connect GitHub and choose **astro-backend**. Render reads `render.yaml`:
   - a Docker web service `astro-backend` in the Singapore region
   - plan **Standard (2 GB)**: the place database and PDF rendering need it, and the 512 MB plans run out of memory
   - a **2 GB persistent disk** at `/app/data` for the database, reports and fonts, so nothing is lost on redeploy
   - health check `/api/config`
2. Fill in the values Render asks for:
   - `SITE_URL` = your Vercel URL, e.g. `https://astro-frontend.vercel.app`. If you don't have it yet, put a placeholder and update it after deploying the frontend.
   - `ADMIN_EMAIL` / `ADMIN_PASSWORD` = your admin login
   - Optional: `SMTP_USER` / `SMTP_PASS` (Gmail address + App Password), `ADMIN_NOTIFY_EMAIL`, and Razorpay keys. All of these can also be added later in Admin → Settings → Integrations.
3. Click **Apply**. The first deploy takes about 5–8 minutes: Maven builds the jar, then the first start downloads the birth-place database (about 1 minute).
4. Your backend URL is shown at the top of the service, e.g. `https://astro-backend.onrender.com`. Opening `/api/config` there should show JSON.
5. **Razorpay webhook:** in the Razorpay Dashboard → Settings → Webhooks, add `https://astro-backend.onrender.com/api/razorpay/webhook`, select the events `payment.captured` and `order.paid`, and set the same secret as `RAZORPAY_WEBHOOK_SECRET`.

Notes:
- The local `.env` file is not used on Render; set values in Render → Environment instead.
- Back up the disk regularly (Render → Disks → Snapshots).

## Free hosting (no card needed): Hugging Face Spaces + Neon

- **Neon** (neon.tech, sign up with GitHub) is a free Postgres database. Create a project and copy the connection string (`postgresql://...neon.tech/neondb?sslmode=require`).
- **Hugging Face Spaces** (huggingface.co, free account):
  1. Click **New Space** and choose SDK **Docker** with the **Blank** template. Name it `astro-backend` and make it **Public**.
  2. Push this repo to the Space (`git push hf main`). The YAML header at the top of this README tells Hugging Face to run the Dockerfile on port 7860.
  3. In Space → **Settings → Variables and secrets**, add the secrets:
     - `DB_URL` = the Neon connection string
     - `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `JWT_SECRET` (any long random text)
     - `SITE_URL` = your Vercel address
     - `COOKIE_SECURE` = `true`
  4. The backend runs at `https://<hf-username>-astro-backend.hf.space`. Put that address in the frontend's `vercel.json`.

Notes for the free tier:
- Data (customers, chats, reports, saved keys) lives in Neon, so it is kept when the Space restarts.
- Report PDFs are rebuilt automatically after a restart.
- The place database is downloaded again on each start (about 1 minute).
- A free Space sleeps after 48 hours without visitors; the next visit wakes it up in about 1–2 minutes.