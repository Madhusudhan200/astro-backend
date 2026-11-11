package com.kpjyotish.kp;

import io.github.cosinekitty.astronomy.*;

import java.util.*;

/** Vedic knowledge base + extra calculations (divisional charts, panchang, transits) for the full report. */
public final class Vedic {
    private Vedic() {}

    public static final String[] SIGNS = KpEngine.SIGNS;

    // ------------------------------------------------------------------ signs
    public static final String[] SIGN_TRAITS = {
        "Aries is a fiery, movable sign ruled by Mars: pioneering, courageous, direct and quick to act, with a natural urge to lead and start new things.",
        "Taurus is an earthy, fixed sign ruled by Venus: steady, patient, practical and loyal, with a love of comfort, beauty, good food and financial security.",
        "Gemini is an airy, dual sign ruled by Mercury: curious, witty, communicative and versatile, learning fast and enjoying variety and conversation.",
        "Cancer is a watery, movable sign ruled by the Moon: caring, sensitive, protective and intuitive, deeply attached to home, mother and family.",
        "Leo is a fiery, fixed sign ruled by the Sun: generous, dignified, creative and confident, with natural authority and a wish to be respected.",
        "Virgo is an earthy, dual sign ruled by Mercury: analytical, precise, helpful and health-conscious, with a talent for detail, service and improvement.",
        "Libra is an airy, movable sign ruled by Venus: balanced, diplomatic, artistic and fair-minded, valuing partnership, harmony and justice.",
        "Scorpio is a watery, fixed sign ruled by Mars: intense, secretive, determined and research-minded, with great inner strength and transformative power.",
        "Sagittarius is a fiery, dual sign ruled by Jupiter: optimistic, philosophical, honest and freedom-loving, drawn to higher learning, travel and dharma.",
        "Capricorn is an earthy, movable sign ruled by Saturn: disciplined, ambitious, responsible and patient, building success steadily over time.",
        "Aquarius is an airy, fixed sign ruled by Saturn: humanitarian, inventive, independent and future-oriented, thinking for the larger group.",
        "Pisces is a watery, dual sign ruled by Jupiter: compassionate, imaginative, spiritual and selfless, with strong intuition and artistic sensitivity."};
    public static final String[] ELEMENT = {"Fire", "Earth", "Air", "Water"};
    public static final String[] QUALITY = {"Movable", "Fixed", "Dual"};

    // ------------------------------------------------------------------ nakshatras
    public record Nak(String deity, String symbol, String gana, String traits) {}
    public static final Nak[] NAKS = {
        new Nak("Ashwini Kumaras", "Horse's head", "Deva", "quick, energetic, helpful and fond of healing and new beginnings"),
        new Nak("Yama", "Yoni", "Manushya", "determined, responsible and able to carry heavy burdens with strong will"),
        new Nak("Agni", "Razor / flame", "Rakshasa", "sharp, courageous, purifying and outspoken, with leadership drive"),
        new Nak("Brahma", "Chariot", "Manushya", "attractive, creative, stable and fond of comfort, beauty and growth"),
        new Nak("Soma", "Deer's head", "Deva", "gentle, curious, searching and fond of travel and learning"),
        new Nak("Rudra", "Teardrop", "Manushya", "intense, intellectual and transformative, growing through challenges"),
        new Nak("Aditi", "Bow and quiver", "Deva", "kind, optimistic, generous and able to bounce back and renew"),
        new Nak("Brihaspati", "Cow's udder", "Deva", "nurturing, wise, dutiful and spiritually inclined — one of the most auspicious stars"),
        new Nak("Nagas", "Coiled serpent", "Rakshasa", "perceptive, strategic, persuasive and deeply intuitive"),
        new Nak("Pitris", "Royal throne", "Rakshasa", "dignified, traditional, proud of lineage and drawn to leadership"),
        new Nak("Bhaga", "Front legs of a bed", "Manushya", "pleasant, creative, social and fond of relaxation and romance"),
        new Nak("Aryaman", "Back legs of a bed", "Manushya", "reliable, generous, helpful and good at partnerships and contracts"),
        new Nak("Savitar", "Open hand", "Deva", "skilful, clever, hard-working and good with the hands and crafts"),
        new Nak("Vishwakarma", "Bright jewel", "Rakshasa", "artistic, stylish, inventive and fond of design and building"),
        new Nak("Vayu", "Young sprout in the wind", "Deva", "independent, flexible, diplomatic and business-minded"),
        new Nak("Indra-Agni", "Triumphal arch", "Rakshasa", "goal-oriented, ambitious and persistent until success is achieved"),
        new Nak("Mitra", "Lotus", "Deva", "friendly, devoted, cooperative and successful in groups and abroad"),
        new Nak("Indra", "Earring / umbrella", "Rakshasa", "protective, responsible, resourceful and capable of authority"),
        new Nak("Nirriti", "Bunch of roots", "Rakshasa", "investigative, philosophical, getting to the root of things"),
        new Nak("Apas", "Winnowing basket", "Manushya", "confident, persuasive, invincible in spirit and fond of purification"),
        new Nak("Vishwadevas", "Elephant tusk", "Manushya", "righteous, principled, persistent and achieving lasting victory"),
        new Nak("Vishnu", "Ear / three footprints", "Deva", "attentive listener, learned, well-connected and respected"),
        new Nak("Eight Vasus", "Drum", "Rakshasa", "musical, wealthy, energetic and good in group activities"),
        new Nak("Varuna", "Empty circle", "Rakshasa", "independent, healing, secretive and scientific in thinking"),
        new Nak("Aja Ekapada", "Front of a funeral cot", "Manushya", "intense, idealistic, passionate and transformative"),
        new Nak("Ahir Budhnya", "Back of a funeral cot", "Manushya", "wise, calm, disciplined and spiritually deep"),
        new Nak("Pushan", "Fish / drum", "Deva", "gentle, caring, protective, creative and spiritually evolved")};

    // ------------------------------------------------------------------ planets
    public record PlanetInfo(String karaka, String body, String gem, String mantra, String day, String color, int number, String metal, String deity, String exalt, String debil) {}
    public static final Map<String, PlanetInfo> PLANET = new LinkedHashMap<>();
    static {
        PLANET.put("Sun", new PlanetInfo("soul, father, authority, government, vitality, confidence and reputation", "heart, bones, eyes, spine", "Ruby", "Om Hraam Hreem Hraum Sah Suryaya Namah", "Sunday", "Orange / copper red", 1, "Copper / gold", "Surya", "Aries", "Libra"));
        PLANET.put("Moon", new PlanetInfo("mind, emotions, mother, public, comforts, travel and fluids", "mind, chest, blood, body fluids", "Pearl", "Om Shraam Shreem Shraum Sah Chandraya Namah", "Monday", "White / silver", 2, "Silver", "Chandra / Parvati", "Taurus", "Scorpio"));
        PLANET.put("Mars", new PlanetInfo("energy, courage, siblings, property, land, engineering, police and surgery", "muscles, blood, marrow", "Red Coral", "Om Kraam Kreem Kraum Sah Bhaumaya Namah", "Tuesday", "Red", 9, "Copper", "Hanuman / Subrahmanya", "Capricorn", "Cancer"));
        PLANET.put("Mercury", new PlanetInfo("intellect, speech, education, trade, accounts, writing, communication and friends", "nervous system, skin, lungs", "Emerald", "Om Braam Breem Braum Sah Budhaya Namah", "Wednesday", "Green", 5, "Bronze", "Vishnu", "Virgo", "Pisces"));
        PLANET.put("Jupiter", new PlanetInfo("wisdom, teachers, children, wealth, dharma, law, banking and blessings", "liver, fat, thighs", "Yellow Sapphire", "Om Graam Greem Graum Sah Gurave Namah", "Thursday", "Yellow", 3, "Gold", "Dakshinamurthy / Brihaspati", "Cancer", "Capricorn"));
        PLANET.put("Venus", new PlanetInfo("marriage, spouse, love, arts, vehicles, luxury, beauty and comforts", "reproductive system, kidneys, face", "Diamond / White Sapphire", "Om Draam Dreem Draum Sah Shukraya Namah", "Friday", "White / pastel", 6, "Silver", "Lakshmi", "Pisces", "Virgo"));
        PLANET.put("Saturn", new PlanetInfo("discipline, hard work, longevity, servants, masses, delays, justice and endurance", "legs, joints, teeth, nerves", "Blue Sapphire", "Om Praam Preem Praum Sah Shanaye Namah", "Saturday", "Dark blue / black", 8, "Iron", "Shani / Hanuman", "Libra", "Aries"));
        PLANET.put("Rahu", new PlanetInfo("ambition, foreign lands, technology, sudden gains, illusion and unconventional paths", "skin, nervous system", "Hessonite (Gomedh)", "Om Bhraam Bhreem Bhraum Sah Rahave Namah", "Saturday", "Smoky / dark", 4, "Lead", "Durga", "Taurus", "Scorpio"));
        PLANET.put("Ketu", new PlanetInfo("spirituality, detachment, research, healing, intuition and liberation", "nerves, skin, hidden ailments", "Cat's Eye", "Om Sraam Sreem Sraum Sah Ketave Namah", "Tuesday", "Grey / multicolour", 7, "Iron", "Ganesha", "Scorpio", "Taurus"));
    }
    static final Map<String, Set<String>> FRIENDS = Map.of(
            "Sun", Set.of("Moon", "Mars", "Jupiter"), "Moon", Set.of("Sun", "Mercury"), "Mars", Set.of("Sun", "Moon", "Jupiter"),
            "Mercury", Set.of("Sun", "Venus"), "Jupiter", Set.of("Sun", "Moon", "Mars"), "Venus", Set.of("Mercury", "Saturn"),
            "Saturn", Set.of("Mercury", "Venus"), "Rahu", Set.of("Mercury", "Venus", "Saturn"), "Ketu", Set.of("Mars", "Jupiter"));

    public static String dignity(String planet, String sign, String signLord) {
        PlanetInfo pi = PLANET.get(planet);
        if (pi.exalt().equals(sign)) return "Exalted";
        if (pi.debil().equals(sign)) return "Debilitated";
        if (signLord.equals(planet)) return "Own sign";
        if (FRIENDS.get(planet).contains(signLord)) return "Friendly sign";
        return "Neutral / enemy sign";
    }

    public static String dignityText(String d) {
        return switch (d) {
            case "Exalted" -> "It is exalted here — at its strongest, giving excellent and generous results of its significations.";
            case "Debilitated" -> "It is debilitated here — its results come with effort and learning; remedies and patience turn this into hard-won strength.";
            case "Own sign" -> "It is in its own sign — comfortable and strong, giving reliable results.";
            case "Friendly sign" -> "It sits in a friendly sign — supported and able to give good results.";
            default -> "It sits in a neutral or challenging sign — results depend more on its star lord and sub lord, as KP teaches.";
        };
    }

    // ------------------------------------------------------------------ houses
    public static final String[] HOUSE_NAME = {"Tanu Bhava (Self)", "Dhana Bhava (Wealth)", "Sahaja Bhava (Courage)", "Sukha Bhava (Happiness)", "Putra Bhava (Children)", "Ripu Bhava (Service & Health)",
            "Kalatra Bhava (Marriage)", "Ayur Bhava (Longevity)", "Dharma Bhava (Fortune)", "Karma Bhava (Career)", "Labha Bhava (Gains)", "Vyaya Bhava (Expenses & Moksha)"};
    public static final String[] HOUSE_DETAIL = {
        "body, health, appearance, personality, temperament, fame, overall strength and the direction of life",
        "family, accumulated wealth, speech, food habits, face, right eye, early education and values",
        "courage, younger siblings, communication, writing, media, short journeys, hands, neighbours and self-effort",
        "mother, home, happiness, vehicles, land and property, basic education, heart and inner peace",
        "children, intelligence, creativity, romance, speculation, mantras, past-life merit and higher studies' foundation",
        "service, job, competition, enemies, debts, loans, diseases and their cure, pets and maternal relatives",
        "marriage, spouse, partnerships, business dealings, public relations, contracts and travel",
        "longevity, sudden events, inheritance, insurance, research, occult knowledge and transformation",
        "fortune, father, guru, dharma, higher learning, long-distance travel, pilgrimage and luck",
        "career, profession, status, authority, karma, government recognition and public life",
        "gains, income, friends, elder siblings, fulfilment of desires, networks and success",
        "expenses, losses, foreign lands, hospitals, bed pleasures, charity, sleep and liberation (moksha)"};

    // ------------------------------------------------------------------ dasha results
    public static final Map<String, String> DASHA_THEME = Map.of(
            "Sun", "self-confidence, recognition, dealings with authority and government, and focus on health and father",
            "Moon", "emotional growth, public dealings, travel, home and mother, and changing circumstances",
            "Mars", "energy, ambition, property matters, competition, technical work and decisive action",
            "Mercury", "learning, communication, business, writing, analysis and networking",
            "Jupiter", "wisdom, expansion, children, finances, teachers, spirituality and good fortune",
            "Venus", "relationships, marriage, comforts, vehicles, arts, luxury and financial growth",
            "Saturn", "hard work, responsibility, structure, patience and long-lasting, slowly-built achievements",
            "Rahu", "ambition, foreign links, technology, sudden changes and unconventional opportunities",
            "Ketu", "introspection, research, spirituality, detachment and letting go of the old");

    // ------------------------------------------------------------------ divisional charts
    public record Varga(int n, String code, String name, String governs) {}
    public static final List<Varga> VARGAS = List.of(
            new Varga(1, "D1", "Rasi", "the physical body, overall life and every area — the root chart"),
            new Varga(2, "D2", "Hora", "wealth, assets and the capacity to sustain the family"),
            new Varga(3, "D3", "Drekkana", "siblings, courage, initiative and teamwork"),
            new Varga(4, "D4", "Chaturthamsa", "property, home, vehicles and fixed assets — your fortune in settled comforts"),
            new Varga(7, "D7", "Saptamsa", "children, grandchildren and creative output"),
            new Varga(9, "D9", "Navamsa", "marriage, spouse, dharma and the inner strength of every planet — the most important varga"),
            new Varga(10, "D10", "Dasamsa", "career, profession, status and achievements in the world"),
            new Varga(12, "D12", "Dwadasamsa", "parents, ancestry and inherited traits"),
            new Varga(16, "D16", "Shodasamsa", "vehicles, comforts and happiness from conveyances"),
            new Varga(20, "D20", "Vimsamsa", "spiritual progress, worship and religious inclination"),
            new Varga(24, "D24", "Chaturvimsamsa", "education, learning and academic achievement"),
            new Varga(27, "D27", "Saptavimsamsa", "physical and mental strength, stamina and weaknesses"),
            new Varga(30, "D30", "Trimsamsa", "misfortunes, challenges and how you overcome them"),
            new Varga(40, "D40", "Khavedamsa", "maternal lineage and auspicious / inauspicious effects in life"),
            new Varga(45, "D45", "Akshavedamsa", "paternal lineage, character and general conduct"),
            new Varga(60, "D60", "Shashtiamsa", "past-life karma and the finest details of the whole chart"));

    /** Sign index (0-11) of a sidereal longitude in the given divisional chart (Parashara rules). */
    public static int vargaSign(double lon, int n) {
        int s = (int) Math.floor(lon / 30);
        double d = lon - s * 30;
        boolean odd = s % 2 == 0; // Aries (0) is an odd sign
        int q = s % 3;            // 0 movable, 1 fixed, 2 dual
        int el = s % 4;           // 0 fire, 1 earth, 2 air, 3 water
        int part = (int) Math.floor(d / (30.0 / n));
        switch (n) {
            case 1: return s;
            case 2: return odd ? (d < 15 ? 4 : 3) : (d < 15 ? 3 : 4); // Leo (Sun) / Cancer (Moon)
            case 3: return (s + part * 4) % 12;
            case 4: return (s + part * 3) % 12;
            case 7: return ((odd ? s : s + 6) + part) % 12;
            case 9: return (new int[]{0, 9, 6, 3}[el] + part) % 12;
            case 10: return ((odd ? s : s + 8) + part) % 12;
            case 12: return (s + part) % 12;
            case 16: return (new int[]{0, 4, 8}[q] + part) % 12;
            case 20: return (new int[]{0, 8, 4}[q] + part) % 12;
            case 24: return ((odd ? 4 : 3) + part) % 12;
            case 27: return (new int[]{0, 3, 6, 9}[el] + part) % 12;
            case 30: {
                if (odd) return d < 5 ? 0 : d < 10 ? 10 : d < 18 ? 8 : d < 25 ? 2 : 6;
                return d < 5 ? 1 : d < 12 ? 5 : d < 20 ? 11 : d < 25 ? 9 : 7;
            }
            case 40: return ((odd ? 0 : 6) + part) % 12;
            case 45: return (new int[]{0, 4, 8}[q] + part) % 12;
            case 60: return (s + part) % 12;
            default: return (s + part) % 12;
        }
    }

    /** Rasi-style grid (sign -> planet abbreviations) for a divisional chart, plus its ascendant sign. */
    public static List<List<String>> vargaGrid(Chart c, int n) {
        List<List<String>> g = new ArrayList<>();
        for (int i = 0; i < 12; i++) g.add(new ArrayList<>());
        for (Chart.Point p : c.planets) g.get(vargaSign(p.longitude, n)).add(p.abbr);
        return g;
    }

    public static int vargaAsc(Chart c, int n) { return vargaSign(c.cusps.get(0).longitude, n); }

    // ------------------------------------------------------------------ panchang
    static final String[] TITHI = {"Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami", "Ashtami", "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi"};
    static final String[] YOGA = {"Vishkumbha", "Priti", "Ayushman", "Saubhagya", "Shobhana", "Atiganda", "Sukarma", "Dhriti", "Shula", "Ganda", "Vriddhi", "Dhruva", "Vyaghata", "Harshana", "Vajra", "Siddhi", "Vyatipata", "Variyana", "Parigha", "Shiva", "Siddha", "Sadhya", "Shubha", "Shukla", "Brahma", "Indra", "Vaidhriti"};
    static final String[] KARANA = {"Bava", "Balava", "Kaulava", "Taitila", "Gara", "Vanija", "Vishti"};
    static final String[] WEEKDAY = {"Sunday (Ravivar)", "Monday (Somvar)", "Tuesday (Mangalvar)", "Wednesday (Budhvar)", "Thursday (Guruvar)", "Friday (Shukravar)", "Saturday (Shanivar)"};

    public static Map<String, String> panchang(Chart c) {
        double sun = c.planet("Sun").longitude, moon = c.planet("Moon").longitude;
        double diff = ((moon - sun) % 360 + 360) % 360;
        int t = (int) Math.floor(diff / 12); // 0..29
        String tithi = t == 14 ? "Purnima" : t == 29 ? "Amavasya" : TITHI[t % 15 == 14 ? 13 : t % 15];
        String paksha = t < 15 ? "Shukla Paksha (waxing)" : "Krishna Paksha (waning)";
        int y = (int) Math.floor(((sun + moon) % 360) / (360.0 / 27));
        int k = (int) Math.floor(diff / 6); // 0..59
        String karana = k == 0 ? "Kimstughna" : k >= 57 ? new String[]{"Shakuni", "Chatushpada", "Naga"}[k - 57] : KARANA[(k - 1) % 7];
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Weekday (Vaar)", WEEKDAY[weekday(c)]);
        m.put("Tithi", tithi + ", " + paksha);
        m.put("Nakshatra", c.nakshatra + " pada " + c.planet("Moon").pada);
        m.put("Yoga", YOGA[y]);
        m.put("Karana", karana);
        m.put("Moon sign (Rasi)", c.moonSign);
        m.put("Sun sign (sidereal)", c.sunSign);
        m.put("Lagna (Ascendant)", c.ascendant.sign + " " + c.ascendant.dms);
        m.put("Gana", NAKS[nakIdx(c.nakshatra)].gana());
        m.put("Nakshatra deity", NAKS[nakIdx(c.nakshatra)].deity());
        m.put("Birth star lord", c.planet("Moon").starLord);
        return m;
    }

    static int weekday(Chart c) {
        String dl = c.rulingPlanets.get("dayLord");
        return Arrays.asList("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn").indexOf(dl);
    }

    public static int nakIdx(String name) { return Arrays.asList(KpEngine.NAKSHATRAS).indexOf(name); }

    // ------------------------------------------------------------------ transits
    /** Sidereal sign index of a slow planet on a date (for yearly forecasts). */
    public static int transitSign(Body body, java.time.LocalDate date) {
        long ms = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        Time t = Time.fromMillisecondsSince1970(ms);
        double trop = KpEngine.tropical(body, t);
        double ayan = KpEngine.kpAyanamsa(t.getUt() + 2451545.0);
        return (int) Math.floor((((trop - ayan) % 360) + 360) % 360 / 30);
    }

    public static String jupiterTransit(int fromMoon) {
        return switch (fromMoon) {
            case 2, 5, 7, 9, 11 -> "favourable — Jupiter blesses growth, income, family events and good decisions";
            case 1 -> "mixed — new responsibilities and changes of place; stay grounded";
            case 3, 6, 10 -> "moderate — progress needs more effort; avoid over-commitment";
            case 4, 8, 12 -> "demanding — be careful with expenses, health and big risks";
            default -> "mixed";
        };
    }

    public static String saturnTransit(int fromMoon) {
        return switch (fromMoon) {
            case 3, 6, 11 -> "favourable — Saturn rewards hard work with stable gains and victory over obstacles";
            case 12, 1, 2 -> "Sade Sati phase — a period of maturity, responsibility and restructuring; patience and discipline bring lasting results";
            case 4, 8 -> "Dhaiya (Kantaka) phase — slow down, look after health and home, avoid shortcuts";
            default -> "neutral — steady effort gives steady results";
        };
    }

    // ------------------------------------------------------------------ glossary
    public static final String[][] GLOSSARY = {
            {"KP (Krishnamurti Paddhati)", "A precise system of Vedic astrology developed by Prof. K. S. Krishnamurti, based on star lords and sub lords."},
            {"Ayanamsa", "The difference between the tropical and sidereal zodiacs. KP uses the Krishnamurti ayanamsa."},
            {"Cusp", "The starting point of a house. KP uses Placidus cusps calculated for the exact birth time and place."},
            {"Sign lord", "The planet ruling the zodiac sign in which a planet or cusp falls."},
            {"Star lord (Nakshatra lord)", "The planet ruling the nakshatra. In KP a planet gives the results of its star lord."},
            {"Sub lord", "The lord of the 1/9th unequal division of a nakshatra. The deciding factor in KP: it tells whether an event will happen."},
            {"Sub-sub lord", "A finer division of the sub, used for fine-tuning."},
            {"Significator", "A planet connected to a house through occupation, ownership or its star lord. Significators deliver the results of that house."},
            {"Ruling planets", "The planets ruling the moment of judgement (day lord, ascendant and Moon's sign/star/sub lords) — used to confirm timing."},
            {"Vimshottari dasha", "A 120-year planetary period system based on the Moon's nakshatra at birth, used to time events."},
            {"Mahadasha / Antardasha / Pratyantar", "Major period, sub-period and sub-sub-period of the Vimshottari system."},
            {"Divisional chart (Varga)", "A chart made by dividing each sign into parts, giving detail on a specific life area (e.g. D9 for marriage, D10 for career)."},
            {"Sade Sati", "The roughly 7.5-year transit of Saturn through the 12th, 1st and 2nd signs from the natal Moon."},
            {"Retrograde (R)", "A planet appearing to move backwards; it gives intensified, internalised or delayed results."}};
}
