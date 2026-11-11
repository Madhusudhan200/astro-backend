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
