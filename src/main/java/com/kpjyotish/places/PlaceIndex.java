package com.kpjyotish.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.time.*;
import java.util.*;

/**
 * Birth-place search: offline GeoNames index (all Indian villages + world towns) loaded in the
 * background at startup, with OpenStreetMap (Photon) online fallback. Time zones are IANA ids;
 * UTC offsets are computed for the actual birth date/time (DST + historical changes).
 */
@Component
public class PlaceIndex {
    private static final Logger log = LoggerFactory.getLogger(PlaceIndex.class);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    public static class Place {
        public String name, district, state, country, cc, tzName, label, source, type;
        public double lat, lon;
        public long pop;
    }

    private volatile String[] recs = new String[0];
    private volatile String[] keys = new String[0];   // sorted normalized names
    private volatile int[] keyRec = new int[0];       // record index per key
    private volatile byte[] keyPrimary = new byte[0]; // 1 = real name, 0 = alternate name
    private volatile Map<Long, Integer> grid = Map.of(); // 0.5° cell -> a record (for nearest time zone)
    private volatile boolean ready;

    public boolean isReady() { return ready; }

    /** Load (or build, first time) in a background thread so the site starts instantly. */
    @jakarta.annotation.PostConstruct
    void init() {
        Thread t = new Thread(() -> {
            try {
                if (!Files.exists(PlaceBuilder.FILE)) {
                    log.info("place database not found - building it now (one-time, ~1 min)");
                    PlaceBuilder.build(List.of("IN"));
                }
                load();
            } catch (Exception e) {
                log.error("place database failed: {}", e.getMessage());
            }
        }, "place-index");
        t.setDaemon(true);
        t.start();
    }

    static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return n.replaceAll("[^a-z0-9 ]+", " ").replaceAll("\\s+", " ").trim();
    }

    private void load() throws Exception {
        long t0 = System.currentTimeMillis();
        List<String> lines = Files.readAllLines(PlaceBuilder.FILE, StandardCharsets.UTF_8);
        lines.remove(0);
        String[] r = lines.toArray(new String[0]);
        List<String> k = new ArrayList<>(r.length * 2);
        Map<Long, Integer> g = new HashMap<>();
        for (int i = 0; i < r.length; i++) {
            String[] f = r[i].split("\t", -1);
            Set<String> primary = new LinkedHashSet<>(List.of(norm(f[0]), norm(f[1])));
            for (String n : primary) if (!n.isEmpty()) k.add(n + '\u0001' + i + '\u0001' + 1);
            if (!f[2].isEmpty()) for (String a : f[2].split("\\|")) {
                String n = norm(a);
                if (!n.isEmpty() && !primary.contains(n)) k.add(n + '\u0001' + i + '\u0001' + 0);
            }
            g.putIfAbsent(cell(Double.parseDouble(f[7]), Double.parseDouble(f[8])), i);
        }
        String[] ks = k.toArray(new String[0]);
        Arrays.parallelSort(ks);
        String[] kk = new String[ks.length];
        int[] kr = new int[ks.length];
        byte[] kp = new byte[ks.length];
        for (int i = 0; i < ks.length; i++) {
            int a = ks[i].indexOf('\u0001'), b = ks[i].lastIndexOf('\u0001');
            kk[i] = ks[i].substring(0, a);
            kr[i] = Integer.parseInt(ks[i].substring(a + 1, b));
            kp[i] = (byte) (ks[i].charAt(b + 1) - '0');
        }
        recs = r; keys = kk; keyRec = kr; keyPrimary = kp; grid = g; ready = true;
        log.info("{} places indexed in {} s", String.format("%,d", r.length), (System.currentTimeMillis() - t0) / 1000.0);
    }

    private static long cell(double lat, double lon) { return (long) Math.floor((lat + 90) * 2) * 1000 + (long) Math.floor((lon + 180) * 2); }

    private Place toPlace(int i) {
        String[] f = recs[i].split("\t", -1);
        Place p = new Place();
        p.name = f[0]; p.district = f[3]; p.state = f[4]; p.country = f[5]; p.cc = f[6];
        p.lat = Double.parseDouble(f[7]); p.lon = Double.parseDouble(f[8]); p.tzName = f[9];
        p.pop = f[10].isEmpty() ? 0 : Long.parseLong(f[10]);
        p.label = label(p.name, p.district, p.state, p.country);
        p.source = "local";
        return p;
    }

    static String label(String name, String district, String state, String country) {
        StringJoiner j = new StringJoiner(", ");
        for (String s : new String[]{name, district != null && !district.equals(name) ? district : "", state, country})
            if (s != null && !s.isBlank()) j.add(s);
        return j.toString();
    }

    public List<Place> searchLocal(String query, int limit) {
        if (!ready || query == null) return List.of();
        String[] parts = Arrays.stream(query.split(",")).map(PlaceIndex::norm).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (parts.length == 0 || parts[0].length() < 2) return List.of();
        String q = parts[0];
        String[] ks = keys;
        int lo = Arrays.binarySearch(ks, q);
        if (lo < 0) lo = -lo - 1;
        while (lo > 0 && ks[lo - 1].equals(q)) lo--;
        Map<Integer, Integer> hits = new LinkedHashMap<>();
        for (int i = lo; i < ks.length && ks[i].startsWith(q) && hits.size() < 4000; i++) {
            // score: 3 exact real name, 2 exact alternate, 1 real-name prefix, 0 alternate prefix
            int score = (ks[i].equals(q) ? 2 : 0) + keyPrimary[i];
            hits.merge(keyRec[i], score, Math::max);
        }
        List<Map.Entry<Place, Integer>> list = new ArrayList<>();
        for (var e : hits.entrySet()) list.add(Map.entry(toPlace(e.getKey()), e.getValue()));
        if (parts.length > 1) {
            list.removeIf(e -> {
                String ctx = norm(e.getKey().district + " " + e.getKey().state + " " + e.getKey().country + " " + e.getKey().cc);
                for (int i = 1; i < parts.length; i++) if (!ctx.contains(parts[i])) return true;
                return false;
            });
        }
        list.sort(Comparator.<Map.Entry<Place, Integer>>comparingInt(e -> -e.getValue())
                .thenComparingInt(e -> "IN".equals(e.getKey().cc) ? 0 : 1)
                .thenComparingLong(e -> -e.getKey().pop)
                .thenComparingInt(e -> e.getKey().name.length()));
        return list.stream().limit(limit).map(Map.Entry::getKey).toList();
    }

    /** Nearest known place's IANA zone (used for online results). */
    public String nearestTz(double lat, double lon) {
        if (!ready) return guessTz(lon);
        long c = cell(lat, lon);
        String best = null; double bestD = Double.MAX_VALUE;
        for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
            Integer i = grid.get(c + dy * 1000L + dx);
            if (i == null) continue;
            String[] f = recs[i].split("\t", -1);
            double d = Math.pow(Double.parseDouble(f[7]) - lat, 2) + Math.pow(Double.parseDouble(f[8]) - lon, 2);
            if (d < bestD) { bestD = d; best = f[9]; }
        }
        return best != null ? best : guessTz(lon);
    }

    private static String guessTz(double lon) {
        int off = (int) Math.round(lon / 15);
        return off == 0 ? "UTC" : "Etc/GMT" + (off > 0 ? "-" : "+") + Math.abs(off);
    }

    public List<Place> searchOnline(String query, int limit) {
        try {
            String url = "https://photon.komoot.io/api/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=" + limit + "&osm_tag=place";
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "KPHoroscopeApp/2.0").build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return List.of();
            List<Place> out = new ArrayList<>();
            for (JsonNode f : JSON.readTree(r.body()).path("features")) {
                JsonNode pr = f.path("properties"), co = f.path("geometry").path("coordinates");
                if (!pr.hasNonNull("name")) continue;
                Place p = new Place();
                p.name = pr.path("name").asText();
                p.district = pr.path("county").asText(pr.path("district").asText(""));
                p.state = pr.path("state").asText("");
                p.country = pr.path("country").asText("");
                p.cc = pr.path("countrycode").asText("").toUpperCase(Locale.ROOT);
                p.lon = co.get(0).asDouble(); p.lat = co.get(1).asDouble();
                p.tzName = nearestTz(p.lat, p.lon);
                p.type = pr.path("osm_value").asText("");
                p.label = label(p.name, p.district, p.state, p.country);
                p.source = "osm";
                out.add(p);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Place> search(String query) {
        List<Place> local = searchLocal(query, 15);
        if (local.size() >= 8) return local;
        List<Place> all = new ArrayList<>(local);
        for (Place o : searchOnline(query, 8)) {
            boolean dup = local.stream().anyMatch(l -> Math.abs(l.lat - o.lat) < 0.05 && Math.abs(l.lon - o.lon) < 0.05);
            if (!dup) all.add(o);
        }
        return all.size() > 15 ? all.subList(0, 15) : all;
    }

    // ---------- time zones ----------
    public static boolean validTz(String tz) {
        try { ZoneId.of(tz); return tz != null && !tz.isBlank(); } catch (Exception e) { return false; }
    }

    /** UTC offset (hours) for a local wall-clock birth time in that zone. */
    public static double offsetForLocal(String tzName, String dob, String tob) {
        LocalDate d = LocalDate.parse(dob);
        String t = tob == null || tob.isBlank() ? "12:00:00" : (tob.length() == 5 ? tob + ":00" : tob);
        ZonedDateTime z = LocalDateTime.of(d, LocalTime.parse(t)).atZone(ZoneId.of(tzName));
        return z.getOffset().getTotalSeconds() / 3600.0;
    }
}
