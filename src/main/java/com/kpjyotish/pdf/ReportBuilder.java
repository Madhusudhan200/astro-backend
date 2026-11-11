package com.kpjyotish.pdf;

import com.kpjyotish.ai.AiTypes.ReportContent;
import com.kpjyotish.ai.AiTypes.Section;
import com.kpjyotish.ai.FallbackAstrologer;
import com.kpjyotish.kp.Chart;
import com.kpjyotish.kp.KpEngine;
import com.kpjyotish.kp.Vedic;
import com.kpjyotish.pdf.Doc.*;
import com.kpjyotish.store.Entities.User;
import io.github.cosinekitty.astronomy.Body;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Assembles the complete (90+ page) KP horoscope report as document blocks. */
public final class ReportBuilder {
    private ReportBuilder() {}

    static String ord(int n) { return n + ((n % 100 >= 11 && n % 100 <= 13) ? "th" : switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; }); }
    static String houses(Collection<Integer> h) { return h.isEmpty() ? "—" : String.join(", ", h.stream().sorted().map(String::valueOf).toList()); }
    static String fmt(double d) { return String.format(Locale.ROOT, "%.4f", d); }
    static List<String> row(Object... v) { return Arrays.stream(v).map(x -> x == null ? "" : String.valueOf(x)).toList(); }

    public static List<Block> build(User u, Chart c, ReportContent content, String topic) {
        List<Block> b = new ArrayList<>();
        String first = u.fullName.split("\\s+")[0];
        Chart.Point moon = c.planet("Moon");

        // 1. Birth details & panchang
        b.add(new Chapter("Birth Details & Panchang", "The foundation of your horoscope"));
        b.add(new KeyValue(List.of(new String[]{"Name", u.fullName}, new String[]{"Gender", u.gender == null || u.gender.isBlank() ? "—" : u.gender},
                new String[]{"Date of birth", u.dob}, new String[]{"Time of birth", u.tob}, new String[]{"Place of birth", u.city},
                new String[]{"Latitude / Longitude", fmt(u.lat) + ", " + fmt(u.lon)}, new String[]{"Time zone", (u.tzName == null ? "" : u.tzName + "  ") + "UTC " + (u.tz >= 0 ? "+" : "") + u.tz + " h"},
                new String[]{"Birth moment (UTC)", String.valueOf(c.input.get("utc"))}, new String[]{"Ayanamsa", "Krishnamurti (KP) " + c.ayanamsa.dms}, new String[]{"House system", "Placidus (KP)"})));
        b.add(new Heading("Panchang at birth"));
        List<String[]> pk = new ArrayList<>();
        Vedic.panchang(c).forEach((k, v) -> pk.add(new String[]{k, v}));
        b.add(new KeyValue(pk));
        b.add(new Para("The panchang — weekday, tithi, nakshatra, yoga and karana — describes the quality of the moment you were born. You were born on a "
                + Vedic.panchang(c).get("Weekday (Vaar)") + ", ruled by " + c.rulingPlanets.get("dayLord") + ", which colours your nature with " + Vedic.DASHA_THEME.get(c.rulingPlanets.get("dayLord")) + ". "
                + "Your birth nakshatra " + c.nakshatra + " is ruled by " + moon.starLord + " — the lord whose Vimshottari dasha began your life's planetary clock."));

        // 2. Charts
        b.add(new Chapter("Your Birth Charts", "Rasi (D1), Chandra (Moon) chart and Navamsa (D9)"));
        b.add(new Charts(List.of(new ChartGrid("Rasi (D1)", "Lagna chart", c.rasi, c.ascendant.signIdx),
                new ChartGrid("Chandra Lagna", "Moon as ascendant", c.rasi, (int) Math.floor(moon.longitude / 30)))));
        b.add(new Charts(List.of(new ChartGrid("Navamsa (D9)", "Marriage & inner strength", Vedic.vargaGrid(c, 9), Vedic.vargaAsc(c, 9)),
                new ChartGrid("Dasamsa (D10)", "Career", Vedic.vargaGrid(c, 10), Vedic.vargaAsc(c, 10)))));
        b.add(new Note("Charts are in South-Indian style: signs are fixed, the shaded box with 'Asc' is the ascendant. (R) marks retrograde planets."));

        // 3. Planets
        b.add(new Chapter("Planetary Positions (KP)", "Sign, star, sub and sub-sub lords of the nine grahas"));
        List<List<String>> pr = new ArrayList<>();
        for (Chart.Point p : c.planets) pr.add(row(p.name + (Boolean.TRUE.equals(p.retrograde) ? " (R)" : ""), p.sign, p.dms, p.house, p.nakshatra + "-" + p.pada, p.signLord, p.starLord, p.subLord, p.subSubLord));
        b.add(new Table(List.of("Planet", "Sign", "Degree", "House", "Nakshatra", "Sign L", "Star L", "Sub L", "Sub-Sub L"), pr, new float[]{11, 11, 11, 7, 16, 11, 11, 11, 11}));
        b.add(new Heading("Dignity of planets"));
        List<List<String>> dg = new ArrayList<>();
        for (Chart.Point p : c.planets) dg.add(row(p.name, p.sign, Vedic.dignity(p.name, p.sign, p.signLord), Vedic.PLANET.get(p.name).exalt(), Vedic.PLANET.get(p.name).debil()));
        b.add(new Table(List.of("Planet", "In sign", "Dignity", "Exaltation", "Debilitation"), dg, new float[]{16, 18, 26, 20, 20}));
        b.add(new Heading("Ruling planets at birth"));
        var rp = c.rulingPlanets;
        b.add(new KeyValue(List.of(new String[]{"Day lord", rp.get("dayLord")}, new String[]{"Ascendant sign / star / sub lord", rp.get("ascSignLord") + " / " + rp.get("ascStarLord") + " / " + rp.get("ascSubLord")},
                new String[]{"Moon sign / star / sub lord", rp.get("moonSignLord") + " / " + rp.get("moonStarLord") + " / " + rp.get("moonSubLord")})));

        // 4. Cusps
        b.add(new Chapter("Placidus House Cusps", "The twelve bhavas with their KP lords"));
        List<List<String>> cr = new ArrayList<>();
        for (Chart.Point p : c.cusps) cr.add(row(p.house, p.sign, p.dms, p.nakshatra + "-" + p.pada, p.signLord, p.starLord, p.subLord, p.subSubLord));
        b.add(new Table(List.of("Cusp", "Sign", "Degree", "Nakshatra", "Sign L", "Star L", "Sub L", "Sub-Sub L"), cr, new float[]{8, 13, 13, 19, 12, 12, 12, 11}));
        b.add(new Heading("Planets in each house (Bhava placement)"));
        List<List<String>> bh = new ArrayList<>();
        for (int h = 1; h <= 12; h++) { int hh = h; bh.add(row(h, Vedic.HOUSE_NAME[h - 1], c.cusps.get(h - 1).sign, String.join(", ", c.planets.stream().filter(p -> p.house == hh).map(p -> p.name).toList()))); }
        b.add(new Table(List.of("House", "Name", "Cusp sign", "Planets"), bh, new float[]{9, 37, 20, 34}));
        b.add(new Para("In KP, the sub lord of each cusp is the deciding factor for that house: it shows whether the matters of the house will fructify. If the cusp sub lord signifies the supporting houses (through its star lord), the event is promised; the dasha periods of the significators then decide when it happens."));

        // 5. Significators
        b.add(new Chapter("KP Significators", "Which planets deliver the results of each house"));
        List<List<String>> sr = new ArrayList<>();
        for (Chart.Significator s : c.significators) sr.add(row(s.planet, houses(s.A), houses(s.B), houses(s.C), houses(s.D), houses(s.all)));
        b.add(new Table(List.of("Planet", "A: Star occ.", "B: Occupies", "C: Star owns", "D: Owns", "All houses"), sr, new float[]{14, 15, 15, 15, 15, 26}));
        b.add(new Heading("House significators"));
        List<List<String>> hr = new ArrayList<>();
        for (Chart.HouseSignificator h : c.houseSignificators) hr.add(row(h.house, String.join(", ", h.A), String.join(", ", h.B), String.join(", ", h.C), String.join(", ", h.D)));
        b.add(new Table(List.of("House", "A (strongest)", "B", "C", "D"), hr, new float[]{9, 26, 22, 22, 21}));
        b.add(new Para("Level A (planets in the star of occupants) is strongest, then B (occupants), C (planets in the star of the owner) and D (owner). A planet gives the results of every house it signifies during its dasha, bhukti and antara."));

        // 6. Divisional charts
        for (Vedic.Varga v : Vedic.VARGAS) {
            b.add(new Chapter("Divisional Chart " + v.code() + " — " + v.name(), "Governs " + v.governs()));
            int asc = Vedic.vargaAsc(c, v.n());
            b.add(new Charts(List.of(new ChartGrid(v.code() + " " + v.name(), "Asc: " + Vedic.SIGNS[asc], Vedic.vargaGrid(c, v.n()), asc))));
            List<List<String>> vr = new ArrayList<>();
            List<String> strong = new ArrayList<>();
            for (Chart.Point p : c.planets) {
                int s = Vedic.vargaSign(p.longitude, v.n());
                String d = Vedic.dignity(p.name, Vedic.SIGNS[s], KpEngine.SIGN_LORDS[s]);
                if (d.equals("Exalted") || d.equals("Own sign")) strong.add(p.name + " (" + d.toLowerCase(Locale.ROOT) + ")");
                vr.add(row(p.name, Vedic.SIGNS[s], KpEngine.SIGN_LORDS[s], ((s - asc + 12) % 12) + 1, d));
            }
            b.add(new Table(List.of("Planet", v.code() + " sign", "Sign lord", "House from " + v.code() + " Asc", "Dignity"), vr, new float[]{16, 20, 18, 20, 26}));
            b.add(new Para("The " + v.name() + " chart (" + v.code() + ") is used to study " + v.governs() + ". Its ascendant falls in " + Vedic.SIGNS[asc] + ". " + Vedic.SIGN_TRAITS[asc]
                    + "\n\n" + (strong.isEmpty() ? "No planet is exalted or in its own sign here, so results in this area come mainly through the KP promise of the main chart and the timing of dashas."
                    : "Strong planets in this varga: " + String.join(", ", strong) + ". These planets give especially good results for " + v.governs() + " during their periods.")));
        }

        // 7. Nakshatra & ascendant
        Vedic.Nak nk = Vedic.NAKS[Vedic.nakIdx(c.nakshatra)];
        b.add(new Chapter("Your Birth Star — " + c.nakshatra, "Janma Nakshatra profile"));
        b.add(new KeyValue(List.of(new String[]{"Nakshatra", c.nakshatra + " (pada " + moon.pada + ")"}, new String[]{"Ruling planet (star lord)", moon.starLord},
                new String[]{"Deity", nk.deity()}, new String[]{"Symbol", nk.symbol()}, new String[]{"Gana (temperament)", nk.gana()}, new String[]{"Moon sign", c.moonSign})));
        b.add(new Para("Your Moon is in " + c.nakshatra + ", presided over by " + nk.deity() + " and symbolised by the " + nk.symbol().toLowerCase(Locale.ROOT) + ". People born in this star are "
                + nk.traits() + ".\n\nThe Moon represents the mind, so your nakshatra describes how you feel, think and respond emotionally. Its lord " + moon.starLord
                + " is a lifelong influence: " + moon.starLord + " brings " + Vedic.PLANET.get(moon.starLord).karaka() + " into focus, and its dasha opened your life (balance "
                + String.format(Locale.ROOT, "%.2f", c.dasha.balance.years) + " years at birth).\n\nAs a " + nk.gana() + " gana native, "
                + switch (nk.gana()) { case "Deva" -> "you are naturally gentle, generous and drawn to goodness."; case "Manushya" -> "you balance worldly ambition with kindness and practicality."; default -> "you are strong-willed, independent and fearless in pursuing your goals."; }));
        Chart.Point asc = c.cusps.get(0);
        Vedic.Nak an = Vedic.NAKS[Vedic.nakIdx(asc.nakshatra)];
        b.add(new Heading("Ascendant nakshatra — " + asc.nakshatra));
        b.add(new Para("Your ascendant degree lies in " + asc.nakshatra + " (lord " + asc.starLord + "), giving a personality that is " + an.traits() + ". The ascendant sub lord "
                + asc.subLord + " is, in KP, the key to your health, nature and overall direction."));
        Chart.Point sun = c.planet("Sun");
        b.add(new Heading("Sun nakshatra — " + sun.nakshatra));
        b.add(new Para("Your Sun is in " + sun.nakshatra + " (lord " + sun.starLord + "): your soul's purpose expresses itself in ways that are " + Vedic.NAKS[Vedic.nakIdx(sun.nakshatra)].traits() + "."));

        b.add(new Chapter("Ascendant & Personality", c.ascendant.sign + " rising"));
        b.add(new Para(Vedic.SIGN_TRAITS[c.ascendant.signIdx] + "\n\nWith " + c.ascendant.sign + " rising at " + c.ascendant.dms + ", the ascendant sign lord "
                + asc.signLord + " shapes your body and temperament, the star lord " + asc.starLord + " shows the results you seek through life, and the sub lord " + asc.subLord
                + " decides health and success of the self.\n\n" + FallbackAstrologer.cuspText(c, 1, first)));
        b.add(new Heading("Element and quality"));
        b.add(new Para("Your ascendant is a " + Vedic.QUALITY[c.ascendant.signIdx % 3].toLowerCase(Locale.ROOT) + " " + Vedic.ELEMENT[c.ascendant.signIdx % 4].toLowerCase(Locale.ROOT)
                + " sign. Your Moon sign " + c.moonSign + " is a " + Vedic.QUALITY[(int) (moon.longitude / 30) % 3].toLowerCase(Locale.ROOT) + " " + Vedic.ELEMENT[(int) (moon.longitude / 30) % 4].toLowerCase(Locale.ROOT)
                + " sign. " + Vedic.SIGN_TRAITS[(int) (moon.longitude / 30)]));

        // 8. Planet chapters
        for (Chart.Point p : c.planets) {
            Vedic.PlanetInfo pi = Vedic.PLANET.get(p.name);
            String dig = Vedic.dignity(p.name, p.sign, p.signLord);
            b.add(new Chapter(p.name + " in Your Chart", p.sign + " · " + ord(p.house) + " house · " + p.nakshatra));
            b.add(new KeyValue(List.of(new String[]{"Position", p.sign + " " + p.dms + (Boolean.TRUE.equals(p.retrograde) ? " (retrograde)" : "")},
                    new String[]{"House (Placidus)", ord(p.house) + " — " + Vedic.HOUSE_NAME[p.house - 1]}, new String[]{"Nakshatra / pada", p.nakshatra + " / " + p.pada},
                    new String[]{"Sign / Star / Sub / Sub-sub lord", p.signLord + " / " + p.starLord + " / " + p.subLord + " / " + p.subSubLord},
                    new String[]{"Dignity", dig}, new String[]{"Houses signified", houses(c.housesSignified(p.name))}, new String[]{"Significations (karaka)", pi.karaka()})));
            b.add(new Heading("Placement"));
            b.add(new Para(p.name + " represents " + pi.karaka() + ". In your chart it sits in " + p.sign + ". " + Vedic.SIGN_TRAITS[(int) (p.longitude / 30)] + " " + Vedic.dignityText(dig)
                    + "\n\nPlaced in your " + ord(p.house) + " house, " + p.name + " brings its energy into " + Vedic.HOUSE_DETAIL[p.house - 1] + "."
                    + (Boolean.TRUE.equals(p.retrograde) ? " Being retrograde, its results are more internal and may come after review, delay or repetition — often deeper and more lasting." : "")));
            Chart.Point star = c.planet(p.starLord);
            b.add(new Heading("KP judgement — star lord and sub lord"));
            b.add(new Para("In KP a planet gives the results of its star lord. " + p.name + " is in the star of " + p.starLord + " (in " + star.sign + ", " + ord(star.house)
                    + " house), so it delivers results of houses " + houses(c.housesSignified(p.name)) + ". The sub lord " + p.subLord + " decides whether those results are favourable: "
                    + p.subLord + " signifies houses " + houses(c.housesSignified(p.subLord)) + ".\n\nThe nakshatra " + p.nakshatra + " adds qualities that are " + Vedic.NAKS[Vedic.nakIdx(p.nakshatra)].traits() + "."));
            Chart.Period md = c.dasha.periods.stream().filter(x -> x.lord.equals(p.name)).findFirst().orElse(null);
            if (md != null) {
                b.add(new Heading(p.name + " Mahadasha"));
                b.add(new Para("Your " + p.name + " Mahadasha runs from " + md.start + " to " + md.end + " (" + md.years + " years). It emphasises " + Vedic.DASHA_THEME.get(p.name)
                        + ", and activates houses " + houses(c.housesSignified(p.name)) + " in your life."));
            }
            b.add(new Heading("Strengthening " + p.name));
            b.add(new KeyValue(List.of(new String[]{"Gemstone", pi.gem() + " (wear only after personal consultation)"}, new String[]{"Mantra", pi.mantra()}, new String[]{"Day", pi.day()},
                    new String[]{"Colour", pi.color()}, new String[]{"Deity", pi.deity()}, new String[]{"Body areas", pi.body()})));
        }

        // 9. House chapters
        for (int h = 1; h <= 12; h++) {
            Chart.Point cu = c.cusps.get(h - 1);
            Chart.HouseSignificator hs = c.houseSignificators.get(h - 1);
            b.add(new Chapter(ord(h) + " House — " + Vedic.HOUSE_NAME[h - 1], "Cusp in " + cu.sign + " " + cu.dms));
            b.add(new Para("The " + ord(h) + " house governs " + Vedic.HOUSE_DETAIL[h - 1] + "."));
            b.add(new KeyValue(List.of(new String[]{"Cusp", cu.sign + " " + cu.dms + " (" + cu.nakshatra + "-" + cu.pada + ")"},
                    new String[]{"Sign / Star / Sub / Sub-sub lord", cu.signLord + " / " + cu.starLord + " / " + cu.subLord + " / " + cu.subSubLord},
                    new String[]{"Sub lord signifies", houses(c.housesSignified(cu.subLord))},
                    new String[]{"Significators A / B / C / D", (hs.A.isEmpty() ? "—" : String.join(", ", hs.A)) + "  /  " + (hs.B.isEmpty() ? "—" : String.join(", ", hs.B)) + "  /  "
                            + (hs.C.isEmpty() ? "—" : String.join(", ", hs.C)) + "  /  " + (hs.D.isEmpty() ? "—" : String.join(", ", hs.D))})));
            b.add(new Heading("KP reading"));
            b.add(new Para(FallbackAstrologer.cuspText(c, h, first)));
            Set<String> sig = new LinkedHashSet<>(); sig.addAll(hs.A); sig.addAll(hs.B); sig.addAll(hs.C); sig.addAll(hs.D);
            b.add(new Heading("When this house is activated"));
            List<List<String>> act = new ArrayList<>();
            String today = LocalDate.now().toString();
            for (Chart.Period md : c.dasha.periods) {
                if (md.end.compareTo(today) < 0) continue;
                for (Chart.Period ad : md.antardashas) {
                    if (ad.end.compareTo(today) < 0) continue;
                    if (sig.contains(ad.lord) || sig.contains(md.lord)) act.add(row(md.lord + " – " + ad.lord, ad.start, ad.end));
                    if (act.size() >= 8) break;
                }
                if (act.size() >= 8) break;
            }
            if (act.isEmpty()) b.add(new Para("No significator periods are due in the near future; this house works quietly in the background."));
            else b.add(new Table(List.of("Dasha – Bhukti", "From", "To"), act, new float[]{40, 30, 30}));
        }

        // 10. Consultation chapters (AI / rule-based)
        b.add(new Chapter("Your Consultation", topic == null || topic.isBlank() ? "General life reading" : topic));
        b.add(new Para(content.summary()));
        for (Section s : content.consultation()) { b.add(new Heading(s.title())); b.add(new Para(s.content())); }

        // 11. Life areas
        for (Section s : content.sections()) { b.add(new Chapter(s.title(), "Life area analysis")); b.add(new Para(s.content())); }

        // 12. Dasha
        b.add(new Chapter("Vimshottari Dasha", "Your 120-year planetary timeline"));
        List<List<String>> dr = new ArrayList<>();
        for (Chart.Period p : c.dasha.periods) dr.add(row(p.lord, p.years, p.start, p.end, houses(c.housesSignified(p.lord))));
        b.add(new Table(List.of("Mahadasha", "Years", "From", "To", "Signifies houses"), dr, new float[]{18, 10, 22, 22, 28}));
        b.add(new Para("Balance of dasha at birth: " + c.dasha.balance.lord + " " + String.format(Locale.ROOT, "%.2f", c.dasha.balance.years) + " years. Currently running: "
                + c.currentDasha.mahadasha + " Mahadasha / " + c.currentDasha.antardasha + " Antardasha (until " + c.currentDasha.adEnd + ")."));
        for (Chart.Period md : c.dasha.periods) {
            b.add(new Chapter(md.lord + " Mahadasha", md.start + " to " + md.end));
            List<Integer> sig = c.housesSignified(md.lord);
            b.add(new Para("This " + md.years + "-year period emphasises " + Vedic.DASHA_THEME.get(md.lord) + ". In your chart " + md.lord + " signifies houses " + houses(sig)
                    + " — so matters of " + String.join("; ", sig.stream().limit(4).map(h -> Vedic.HOUSE_DETAIL[h - 1].split(",")[0] + " (" + h + ")").toList()) + " come to the fore."
                    + (sig.contains(11) || sig.contains(10) || sig.contains(2) ? " The link with gain houses makes this a productive, rewarding period." : "")
                    + (sig.contains(8) || sig.contains(12) ? " The link with houses 8/12 calls for careful planning of expenses and health." : "")));
            List<List<String>> ar = new ArrayList<>();
            for (Chart.Period ad : md.antardashas) ar.add(row(md.lord + " – " + ad.lord, ad.start, ad.end, houses(c.housesSignified(ad.lord)), antarNote(c, md.lord, ad.lord)));
            b.add(new Table(List.of("Antardasha", "From", "To", "Houses", "Theme"), ar, new float[]{18, 14, 14, 16, 38}));
        }

        // 13. Pratyantar dashas for the current and next mahadasha
        String today = LocalDate.now().toString();
        int curIdx = 0;
        for (int i = 0; i < c.dasha.periods.size(); i++) if (c.dasha.periods.get(i).start.compareTo(today) <= 0 && today.compareTo(c.dasha.periods.get(i).end) < 0) curIdx = i;
        for (Chart.Period md : c.dasha.periods.subList(curIdx, Math.min(curIdx + 2, c.dasha.periods.size()))) {
            b.add(new Chapter("Pratyantar Dasha — " + md.lord + " Mahadasha", "Sub-sub periods for precise timing"));
            for (Chart.Period ad : md.antardashas) {
                b.add(new Heading(md.lord + " – " + ad.lord + "  (" + ad.start + " to " + ad.end + ")"));
                List<List<String>> pd = new ArrayList<>();
                for (String[] p : pratyantar(ad)) pd.add(row(md.lord + "-" + ad.lord + "-" + p[0], p[1], p[2]));
                b.add(new Table(List.of("Pratyantar", "From", "To"), pd, new float[]{40, 30, 30}));
            }
        }

        // 14. Yearly forecast
        int moonSign = (int) Math.floor(moon.longitude / 30);
        int year0 = LocalDate.now().getYear();
        for (int y = year0; y < year0 + 12; y++) {
            LocalDate mid = LocalDate.of(y, 7, 1);
            int jup = Vedic.transitSign(Body.Jupiter, mid), sat = Vedic.transitSign(Body.Saturn, mid);
            int jFrom = ((jup - moonSign + 12) % 12) + 1, sFrom = ((sat - moonSign + 12) % 12) + 1;
            b.add(new Chapter("Year " + y + " Forecast", "Dasha periods and major transits"));
            List<List<String>> yr = new ArrayList<>();
            String ys = y + "-01-01", ye = y + "-12-31";
            for (Chart.Period md : c.dasha.periods) for (Chart.Period ad : md.antardashas)
                if (ad.start.compareTo(ye) <= 0 && ad.end.compareTo(ys) >= 0) yr.add(row(md.lord + " – " + ad.lord, ad.start, ad.end, antarNote(c, md.lord, ad.lord)));
            b.add(new Table(List.of("Running period", "From", "To", "Theme"), yr, new float[]{22, 16, 16, 46}));
            b.add(new KeyValue(List.of(new String[]{"Jupiter transit", Vedic.SIGNS[jup] + " — " + ord(jFrom) + " from your Moon"}, new String[]{"Saturn transit", Vedic.SIGNS[sat] + " — " + ord(sFrom) + " from your Moon"})));
            b.add(new Para("Jupiter: " + Vedic.jupiterTransit(jFrom) + ".\n\nSaturn: " + Vedic.saturnTransit(sFrom) + ".\n\n" + yearSummary(c, yr.isEmpty() ? null : yr.get(0).get(0), jFrom, sFrom, y)));
            List<List<String>> pt = new ArrayList<>();
            for (Chart.Period md : c.dasha.periods) for (Chart.Period ad : md.antardashas) {
                if (ad.start.compareTo(ye) > 0 || ad.end.compareTo(ys) < 0) continue;
                for (String[] p : pratyantar(ad)) if (p[1].compareTo(ye) <= 0 && p[2].compareTo(ys) >= 0)
                    pt.add(row(md.lord + "-" + ad.lord + "-" + p[0], p[1], p[2], pdNote(c, p[0])));
            }
            if (!pt.isEmpty()) {
                b.add(new Heading("Period-by-period timing in " + y));
                b.add(new Table(List.of("Pratyantar", "From", "To", "Indication"), pt, new float[]{28, 16, 16, 40}));
            }
        }

        // 15. Remedies & lucky factors
        b.add(new Chapter("Remedies & Lucky Factors", "Simple, supportive practices"));
        List<List<String>> rr = new ArrayList<>();
        for (var e : Vedic.PLANET.entrySet()) rr.add(row(e.getKey(), e.getValue().gem(), e.getValue().day(), e.getValue().color(), e.getValue().deity()));
        b.add(new Table(List.of("Planet", "Gemstone", "Day", "Colour", "Deity"), rr, new float[]{14, 26, 16, 22, 22}));
        List<String> weak = c.planets.stream().filter(p -> Vedic.dignity(p.name, p.sign, p.signLord).equals("Debilitated")).map(p -> p.name).toList();
        String key1 = c.ascendant.subLord, key2 = c.rulingPlanets.get("moonStarLord");
        b.add(new Heading("Personal recommendations"));
        b.add(new Bullets(List.of(
                "Strengthen " + key1 + " (your ascendant sub lord): chant \"" + Vedic.PLANET.get(key1).mantra() + "\" 108 times on " + Vedic.PLANET.get(key1).day() + "s.",
                "Support " + key2 + " (your Moon's star lord) by wearing " + Vedic.PLANET.get(key2).color().toLowerCase(Locale.ROOT) + " on " + Vedic.PLANET.get(key2).day() + "s and doing charity on that day.",
                weak.isEmpty() ? "No planet is debilitated in your chart — maintain your strengths with gratitude and discipline." : "Debilitated planet(s) " + String.join(", ", weak) + ": serve the people they signify and worship their deity; avoid wearing their gemstone without consultation.",
                "Respect teachers and elders for Jupiter's grace; keep a regular routine for Saturn; spend a few minutes in meditation daily to steady the Moon (the mind).",
                "Begin important work during the favourable dasha windows shown in this report, ideally on the day of a ruling planet.")));
        b.add(new Heading("Lucky factors"));
        b.add(new KeyValue(List.of(new String[]{"Lucky numbers", Vedic.PLANET.get(key1).number() + ", " + Vedic.PLANET.get(key2).number() + ", " + Vedic.PLANET.get(c.ascendant.starLord).number()},
                new String[]{"Lucky days", Vedic.PLANET.get(key1).day() + ", " + Vedic.PLANET.get(key2).day()}, new String[]{"Lucky colours", Vedic.PLANET.get(key1).color() + ", " + Vedic.PLANET.get(key2).color()},
                new String[]{"Favourable deity", Vedic.PLANET.get(c.ascendant.starLord).deity()})));
        b.add(new Note("Gemstones are powerful; wear one only after a personal consultation with an astrologer."));

        // 16. KP method + glossary + closing
        b.add(new Chapter("How KP Astrology Works", "The method behind this report"));
        b.add(new Para("Krishnamurti Paddhati (KP), developed by Prof. K. S. Krishnamurti, refines traditional Vedic astrology to answer two questions precisely: will an event happen, and when?\n\n"
                + "1. Every planet gives the results of the star (nakshatra) it occupies, and the sub lord decides whether those results are favourable.\n\n"
                + "2. Each house is judged through its cusp sub lord: if the sub lord signifies the supporting houses, the event is promised.\n\n"
                + "3. Events happen during the dasha, bhukti and antara of planets that signify the relevant houses, confirmed by the ruling planets of the moment.\n\n"
                + "This report was calculated from your exact birth time (to the second) and place using Placidus cusps and the Krishnamurti ayanamsa."));
        b.add(new Chapter("Glossary of Terms", "Quick reference"));
        List<List<String>> gl = new ArrayList<>();
        for (String[] g : Vedic.GLOSSARY) gl.add(row(g[0], g[1]));
        b.add(new Table(List.of("Term", "Meaning"), gl, new float[]{28, 72}));
        b.add(new Chapter("Closing Words", "With blessings"));
        b.add(new Para("Dear " + first + ", the stars show tendencies and timing — your choices, effort and character shape how these unfold. Use the favourable periods to act boldly, "
                + "and the challenging ones to prepare, learn and rest. If you have specific questions, our astrologer is available for a personal 30-minute consultation.\n\n"
                + "May this report bring you clarity, confidence and peace."));
        b.add(new Note("Disclaimer: astrology offers guidance based on traditional principles. It is not a substitute for medical, legal or financial advice."));
        return b;
    }

    static String antarNote(Chart c, String md, String ad) {
        List<Integer> s = c.housesSignified(ad);
        boolean gain = s.contains(11) || s.contains(10) || s.contains(2);
        boolean hard = s.contains(8) || s.contains(12);
        return Vedic.DASHA_THEME.get(ad).split(",")[0] + (gain && !hard ? " — productive" : hard && !gain ? " — plan carefully" : gain ? " — gains with expenses" : "");
    }

    static String pdNote(Chart c, String lord) {
        List<Integer> s = c.housesSignified(lord);
        List<String> l = new ArrayList<>();
        if (s.contains(10) || s.contains(6)) l.add("work");
        if (s.contains(2) || s.contains(11)) l.add("gains");
        if (s.contains(4) || s.contains(9)) l.add("studies / home");
        if (s.contains(7)) l.add("relationships");
        if (s.contains(5)) l.add("creativity");
        if (s.contains(12) || s.contains(8)) l.add("expenses - be careful");
        return l.isEmpty() ? "steady, routine period" : String.join(", ", l);
    }

    static String yearSummary(Chart c, String period, int jFrom, int sFrom, int year) {
        boolean jGood = Set.of(2, 5, 7, 9, 11).contains(jFrom), sGood = Set.of(3, 6, 11).contains(sFrom), sSade = Set.of(12, 1, 2).contains(sFrom);
        String tone = jGood && sGood ? "This is one of your stronger years: both Jupiter and Saturn support you, so it is a good time for important decisions and new beginnings."
                : jGood ? "Jupiter's support makes " + year + " a year of growth and good opportunities; use it for learning, family events and financial planning."
                : sGood ? "Saturn rewards steady effort in " + year + "; consistent work brings stable gains even if progress feels slow."
                : sSade ? year + " falls in Sade Sati: a year of maturity and restructuring. Stay disciplined, avoid hasty risks and look after your health."
                : year + " is a balanced year: results come in proportion to effort. Focus on routine, health and steady progress.";
        return tone + (period == null ? "" : " The year opens in the " + period + " period, whose themes are listed above.");
    }

    /** Pratyantar (sub-sub) periods inside an antardasha: {lord, start, end}. */
    static List<String[]> pratyantar(Chart.Period ad) {
        LocalDate s = LocalDate.parse(ad.start), e = LocalDate.parse(ad.end);
        long days = ChronoUnit.DAYS.between(s, e);
        int i0 = Arrays.asList(KpEngine.DASHA_ORDER).indexOf(ad.lord);
        List<String[]> out = new ArrayList<>();
        double acc = 0;
        for (int k = 0; k < 9; k++) {
            String lord = KpEngine.DASHA_ORDER[(i0 + k) % 9];
            double len = days * KpEngine.DASHA_YEARS.get(lord) / 120.0;
            LocalDate a = s.plusDays(Math.round(acc)), z = k == 8 ? e : s.plusDays(Math.round(acc + len));
            out.add(new String[]{lord, a.toString(), z.toString()});
            acc += len;
        }
        return out;
    }
}
