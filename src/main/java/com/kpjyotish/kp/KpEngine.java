package com.kpjyotish.kp;

import io.github.cosinekitty.astronomy.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * KP (Krishnamurti Paddhati) engine: planet positions (Astronomy Engine), Placidus cusps,
 * KP ayanamsa, sign/star/sub/sub-sub lords, 4-level significators, ruling planets and
 * Vimshottari dasha.
 */
public final class KpEngine {
    private KpEngine() {}

    static final double D2R = Math.PI / 180, R2D = 180 / Math.PI;
    public static final String[] SIGNS = {"Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"};
    public static final String[] SIGN_LORDS = {"Mars", "Venus", "Mercury", "Moon", "Sun", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Saturn", "Jupiter"};
    public static final String[] NAKSHATRAS = {"Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra", "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni", "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha", "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha", "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"};
    public static final String[] DASHA_ORDER = {"Ketu", "Venus", "Sun", "Moon", "Mars", "Rahu", "Jupiter", "Saturn", "Mercury"};
    public static final Map<String, Integer> DASHA_YEARS = Map.of("Ketu", 7, "Venus", 20, "Sun", 6, "Moon", 10, "Mars", 7, "Rahu", 18, "Jupiter", 16, "Saturn", 19, "Mercury", 17);
    public static final String[] PLANETS = {"Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu"};
    static final Map<String, String> ABBR = Map.of("Sun", "Su", "Moon", "Mo", "Mars", "Ma", "Mercury", "Me", "Jupiter", "Ju", "Venus", "Ve", "Saturn", "Sa", "Rahu", "Ra", "Ketu", "Ke");
    static final String[] WEEKDAY_LORDS = {"Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn"};
    static final double NAK_SPAN = 360.0 / 27;
    static final double YEAR_MS = 365.25 * 86400000;

    static double norm(double x) { return ((x % 360) + 360) % 360; }

    public static String dms(double deg) {
        int d = (int) Math.floor(deg);
        double mf = (deg - d) * 60;
        int m = (int) Math.floor(mf);
        int s = (int) Math.floor((mf - m) * 60);
        return String.format("%d°%02d'%02d\"", d, m, s);
    }

    static int idx(String lord) { return Arrays.asList(DASHA_ORDER).indexOf(lord); }

    /** Krishnamurti ayanamsa: 22°21'50" at J1900.0 advanced by general precession. */
    static double kpAyanamsa(double jdUT) {
        double t = (jdUT - 2451545.0) / 36525, t0 = (2415020.0 - 2451545.0) / 36525;
        return 22.363889 + prec(t) - prec(t0);
    }

    static double prec(double t) { return (5028.796195 * t + 1.1054348 * t * t) / 3600; }

    static double tropical(Body body, Time time) {
        io.github.cosinekitty.astronomy.Vector v = Astronomy.geoVector(body, time, Aberration.Corrected);
        return norm(Astronomy.rotationEqjEct(time).rotate(v).toSpherical().getLon());
    }

    static double meanNode(Time time) {
        double t = time.getTt() / 36525;
        return norm(125.04452 - 1934.136261 * t + 0.0020708 * t * t + t * t * t / 450000);
    }

    static double obliquity(Time time) {
        double t = time.getTt() / 36525;
        return 23.439291111 - 0.0130042 * t - 1.64e-7 * t * t + 5.04e-7 * t * t * t;
    }

    interface RaFn { double ra(double ad); }

    /** Placidus cusps (tropical), index 0 = house 1. */
    static double[] placidus(double ramc, double lat, double eps) {
        double e = eps * D2R, phi = lat * D2R;
        java.util.function.DoubleUnaryOperator raToLon = ra -> norm(Math.atan2(Math.sin(ra * D2R), Math.cos(ra * D2R) * Math.cos(e)) * R2D);
        double asc = norm(Math.atan2(Math.cos(ramc * D2R), -(Math.sin(ramc * D2R) * Math.cos(e) + Math.tan(phi) * Math.sin(e))) * R2D);
        double mc = raToLon.applyAsDouble(ramc);
        RaFn[] fns = {
            ad -> ramc + (90 + ad) / 3,               // 11
            ad -> ramc + 2 * (90 + ad) / 3,           // 12
            ad -> ramc + 180 - 2 * (90 - ad) / 3,     // 2
            ad -> ramc + 180 - (90 - ad) / 3,         // 3
        };
        double[] r = new double[4];
        for (int k = 0; k < 4; k++) {
            double lon = raToLon.applyAsDouble(fns[k].ra(0));
            for (int i = 0; i < 60; i++) {
                double dec = Math.asin(Math.sin(e) * Math.sin(lon * D2R));
                double x = Math.max(-1, Math.min(1, Math.tan(phi) * Math.tan(dec)));
                double next = raToLon.applyAsDouble(fns[k].ra(Math.asin(x) * R2D));
                if (Math.abs(next - lon) < 1e-7) { lon = next; break; }
                lon = next;
            }
            r[k] = lon;
        }
        double c11 = r[0], c12 = r[1], c2 = r[2], c3 = r[3];
        return new double[]{asc, c2, c3, norm(mc + 180), norm(c11 + 180), norm(c12 + 180), norm(asc + 180), norm(c2 + 180), norm(c3 + 180), mc, c11, c12};
    }

    /** Fills sign / nakshatra / star / sub / sub-sub lords for a sidereal longitude. */
    static void lords(Chart.Point p, double lon) {
        lon = norm(lon);
        int signIdx = (int) Math.floor(lon / 30);
        int nakIdx = (int) Math.floor(lon / NAK_SPAN);
        String star = DASHA_ORDER[nakIdx % 9];
        double pos = lon - nakIdx * NAK_SPAN;
        double[] sub = divide(star, NAK_SPAN, pos);
        String subLord = DASHA_ORDER[(int) sub[0]];
        double[] subSub = divide(subLord, sub[2], pos - sub[1]);
        p.longitude = lon;
        p.dms = dms(lon % 30);
        p.sign = SIGNS[signIdx];
        p.signLord = SIGN_LORDS[signIdx];
        p.degInSign = lon - signIdx * 30;
        p.nakshatra = NAKSHATRAS[nakIdx];
        p.pada = (int) Math.floor(pos / (NAK_SPAN / 4)) + 1;
        p.starLord = star;
        p.subLord = subLord;
        p.subSubLord = DASHA_ORDER[(int) subSub[0]];
    }

    /** Returns {lordIndex, startOffset, span} of the Vimshottari division containing offset. */
    static double[] divide(String startLord, double span, double offset) {
        int i = idx(startLord);
        double acc = 0;
        for (int k = 0; k < 9; k++) {
            int li = (i + k) % 9;
            double part = span * DASHA_YEARS.get(DASHA_ORDER[li]) / 120;
            if (offset < acc + part || k == 8) return new double[]{li, acc, part};
            acc += part;
        }
        throw new IllegalStateException();
    }

    static int houseOf(double lon, double[] cusps) {
        for (int i = 0; i < 12; i++) {
            double span = norm(cusps[(i + 1) % 12] - cusps[i]);
            if (norm(lon - cusps[i]) < span) return i + 1;
        }
        return 1;
    }

    static String date(double ms) { return Instant.ofEpochMilli((long) ms).atZone(ZoneOffset.UTC).toLocalDate().toString(); }

    static Chart.Dasha vimshottari(double moonLon, double birthMs) {
        Chart.Dasha d = new Chart.Dasha();
        int nakIdx = (int) Math.floor(norm(moonLon) / NAK_SPAN);
        String lord = DASHA_ORDER[nakIdx % 9];
        double elapsed = (norm(moonLon) - nakIdx * NAK_SPAN) / NAK_SPAN;
        d.balance.lord = lord;
        d.balance.years = DASHA_YEARS.get(lord) * (1 - elapsed);
        double start = birthMs - DASHA_YEARS.get(lord) * elapsed * YEAR_MS;
        int startIdx = idx(lord);
        for (int k = 0; k < 9; k++) {
            String md = DASHA_ORDER[(startIdx + k) % 9];
            int mdYears = DASHA_YEARS.get(md);
            Chart.Period p = new Chart.Period();
            p.lord = md; p.years = mdYears; p.start = date(start); p.end = date(start + mdYears * YEAR_MS);
            p.antardashas = new ArrayList<>();
            double aStart = start;
            int mdIdx = idx(md);
            for (int j = 0; j < 9; j++) {
                String ad = DASHA_ORDER[(mdIdx + j) % 9];
                double aEnd = aStart + (mdYears * DASHA_YEARS.get(ad) / 120.0) * YEAR_MS;
                Chart.Period a = new Chart.Period();
                a.lord = ad; a.start = date(aStart); a.end = date(aEnd);
                p.antardashas.add(a);
                aStart = aEnd;
            }
            d.periods.add(p);
            start += mdYears * YEAR_MS;
        }
        return d;
    }

    /** @param tz UTC offset in hours at birth */
    public static Chart compute(String dob, String tob, double lat, double lon, double tz) {
        String[] dp = dob.split("-");
        String[] tp = (tob == null || tob.isBlank() ? "00:00:00" : tob).split(":");
        long utcMs = java.time.LocalDateTime.of(Integer.parseInt(dp[0]), Integer.parseInt(dp[1]), Integer.parseInt(dp[2]),
                Integer.parseInt(tp[0]), Integer.parseInt(tp[1]), tp.length > 2 ? Integer.parseInt(tp[2]) : 0)
                .toInstant(ZoneOffset.UTC).toEpochMilli() - Math.round(tz * 3600000);
        Time time = Time.fromMillisecondsSince1970(utcMs);
        Time later = Time.fromMillisecondsSince1970(utcMs + 3600000);
        double ayan = kpAyanamsa(time.getUt() + 2451545.0);
        double eps = obliquity(time);

        Map<String, Body> bodies = new LinkedHashMap<>();
        bodies.put("Sun", Body.Sun); bodies.put("Moon", Body.Moon); bodies.put("Mars", Body.Mars); bodies.put("Mercury", Body.Mercury);
        bodies.put("Jupiter", Body.Jupiter); bodies.put("Venus", Body.Venus); bodies.put("Saturn", Body.Saturn);
        Map<String, Double> trop = new HashMap<>();
        Map<String, Boolean> retro = new HashMap<>();
        for (var e : bodies.entrySet()) {
            double now = tropical(e.getValue(), time);
            double diff = tropical(e.getValue(), later) - now;
            if (diff > 180) diff -= 360;
            if (diff < -180) diff += 360;
            trop.put(e.getKey(), now);
            retro.put(e.getKey(), diff < 0);
        }
        trop.put("Rahu", meanNode(time));
        trop.put("Ketu", norm(meanNode(time) + 180));

        double ramc = norm(Astronomy.siderealTime(time) * 15 + lon);
        double[] tropCusps = placidus(ramc, lat, eps);
        double[] cuspLons = new double[12];
        for (int i = 0; i < 12; i++) cuspLons[i] = norm(tropCusps[i] - ayan);

        Chart c = new Chart();
        c.input.put("dob", dob); c.input.put("tob", tob); c.input.put("lat", lat); c.input.put("lon", lon); c.input.put("tz", tz);
        c.input.put("utc", Instant.ofEpochMilli(utcMs).toString());
        c.ayanamsa.value = ayan;
        c.ayanamsa.dms = dms(ayan);
        for (int i = 0; i < 12; i++) {
            Chart.Point p = new Chart.Point();
            p.house = i + 1;
            lords(p, cuspLons[i]);
            c.cusps.add(p);
        }
        for (String name : PLANETS) {
            Chart.Point p = new Chart.Point();
            p.name = name;
            p.abbr = ABBR.get(name);
            lords(p, trop.get(name) - ayan);
            p.retrograde = !name.equals("Rahu") && !name.equals("Ketu") && retro.getOrDefault(name, false);
            p.house = houseOf(p.longitude, cuspLons);
            c.planets.add(p);
        }

        // Ownership: houses whose cusp sign is ruled by the planet; nodes act as agents of their sign lord
        Map<String, List<Integer>> owned = new HashMap<>();
        for (String name : PLANETS) {
            List<Integer> l = new ArrayList<>();
            for (Chart.Point cu : c.cusps) if (cu.signLord.equals(name)) l.add(cu.house);
            owned.put(name, l);
        }
        owned.put("Rahu", new ArrayList<>(owned.get(c.planet("Rahu").signLord)));
        owned.put("Ketu", new ArrayList<>(owned.get(c.planet("Ketu").signLord)));

        for (Chart.Point p : c.planets) {
            Chart.Significator s = new Chart.Significator();
            s.planet = p.name;
            s.A = List.of(c.planet(p.starLord).house);
            s.B = List.of(p.house);
            s.C = owned.getOrDefault(p.starLord, List.of());
            s.D = owned.getOrDefault(p.name, List.of());
            TreeSet<Integer> all = new TreeSet<>();
            all.addAll(s.A); all.addAll(s.B); all.addAll(s.C); all.addAll(s.D);
            s.all = new ArrayList<>(all);
            c.significators.add(s);
        }
        for (int h = 1; h <= 12; h++) {
            Chart.HouseSignificator hs = new Chart.HouseSignificator();
            hs.house = h;
            for (Chart.Point p : c.planets) {
                if (p.house == h) hs.B.add(p.name);
                if (owned.get(p.name).contains(h) && !p.name.equals("Rahu") && !p.name.equals("Ketu")) hs.D.add(p.name);
            }
            for (Chart.Point p : c.planets) {
                if (hs.B.contains(p.starLord)) hs.A.add(p.name);
                if (hs.D.contains(p.starLord)) hs.C.add(p.name);
            }
            c.houseSignificators.add(hs);
        }

        Chart.Point asc = c.cusps.get(0), moon = c.planet("Moon");
        int localDay = Instant.ofEpochMilli(utcMs + Math.round(tz * 3600000)).atZone(ZoneOffset.UTC).getDayOfWeek().getValue() % 7;
        c.rulingPlanets.put("dayLord", WEEKDAY_LORDS[localDay]);
        c.rulingPlanets.put("ascSignLord", asc.signLord);
        c.rulingPlanets.put("ascStarLord", asc.starLord);
        c.rulingPlanets.put("ascSubLord", asc.subLord);
        c.rulingPlanets.put("moonSignLord", moon.signLord);
        c.rulingPlanets.put("moonStarLord", moon.starLord);
        c.rulingPlanets.put("moonSubLord", moon.subLord);

        c.dasha = vimshottari(moon.longitude, utcMs);
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        for (Chart.Period md : c.dasha.periods) {
            if (md.start.compareTo(today) <= 0 && today.compareTo(md.end) < 0) {
                c.currentDasha.mahadasha = md.lord;
                c.currentDasha.mdEnd = md.end;
                for (Chart.Period ad : md.antardashas)
                    if (ad.start.compareTo(today) <= 0 && today.compareTo(ad.end) < 0) { c.currentDasha.antardasha = ad.lord; c.currentDasha.adEnd = ad.end; }
            }
        }

        for (int i = 0; i < 12; i++) c.rasi.add(new ArrayList<>());
        for (Chart.Point p : c.planets) c.rasi.get((int) Math.floor(p.longitude / 30)).add(p.abbr + (Boolean.TRUE.equals(p.retrograde) ? "(R)" : ""));
        c.ascendant.sign = asc.sign;
        c.ascendant.signIdx = (int) Math.floor(asc.longitude / 30);
        c.ascendant.dms = asc.dms;
        c.ascendant.starLord = asc.starLord;
        c.ascendant.subLord = asc.subLord;
        c.nakshatra = moon.nakshatra;
        c.moonSign = moon.sign;
        c.sunSign = c.planet("Sun").sign;
        return c;
    }
}
