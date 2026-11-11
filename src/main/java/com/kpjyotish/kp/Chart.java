package com.kpjyotish.kp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** KP chart data. Public fields are serialised to JSON with these exact names for the frontend. */
public class Chart {
    public Map<String, Object> input = new LinkedHashMap<>();
    public Ayanamsa ayanamsa = new Ayanamsa();
    public String houseSystem = "Placidus";
    public Ascendant ascendant = new Ascendant();
    public List<Point> planets = new ArrayList<>();
    public List<Point> cusps = new ArrayList<>();
    public List<Significator> significators = new ArrayList<>();
    public List<HouseSignificator> houseSignificators = new ArrayList<>();
    public Map<String, String> rulingPlanets = new LinkedHashMap<>();
    public Dasha dasha = new Dasha();
    public CurrentDasha currentDasha = new CurrentDasha();
    public List<List<String>> rasi = new ArrayList<>();
    public String nakshatra;
    public String moonSign;
    public String sunSign;

    public static class Ayanamsa { public String name = "Krishnamurti (KP)"; public double value; public String dms; }

    public static class Ascendant { public String sign; public int signIdx; public String dms; public String starLord; public String subLord; }

    /** A planet or a house cusp. */
    public static class Point {
        public String name;          // planets only
        public String abbr;          // planets only
        public Integer house;        // planet: house occupied; cusp: cusp number
        public double longitude;
        public String dms;
        public Boolean retrograde;   // planets only
        public String sign;
        public String signLord;
        public double degInSign;
        public String nakshatra;
        public int pada;
        public String starLord;
        public String subLord;
        public String subSubLord;
    }

    public static class Significator {
        public String planet;
        public List<Integer> A, B, C, D, all;
    }

    public static class HouseSignificator {
        public int house;
        public List<String> A = new ArrayList<>(), B = new ArrayList<>(), C = new ArrayList<>(), D = new ArrayList<>();
    }

    public static class Dasha {
        public Balance balance = new Balance();
        public List<Period> periods = new ArrayList<>();
    }

    public static class Balance { public String lord; public double years; }

    public static class Period {
        public String lord;
        public Integer years;
        public String start;
        public String end;
        public List<Period> antardashas;
    }

    public static class CurrentDasha { public String mahadasha, antardasha, mdEnd, adEnd; }

    // ---- helpers used by the AI / rule-based astrologer ----
    public Point planet(String name) {
        return planets.stream().filter(p -> p.name.equals(name)).findFirst().orElseThrow();
    }

    public List<Integer> housesSignified(String planet) {
        return significators.stream().filter(s -> s.planet.equals(planet)).findFirst().map(s -> s.all).orElse(List.of());
    }
}
