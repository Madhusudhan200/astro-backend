package com.kpjyotish.places;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Builds data/places/places.tsv from GeoNames (CC-BY 4.0, https://www.geonames.org):
 * every populated place (villages included) for the given countries (default IN)
 * plus all world towns/cities with population >= 500.
 * Run: java -cp target/kp-jyotish.jar -Dloader.main=com.kpjyotish.places.PlaceBuilder org.springframework.boot.loader.launch.PropertiesLauncher IN NP
 */
public final class PlaceBuilder {
    private static final String BASE = "https://download.geonames.org/export/dump/";
    public static final Path OUT_DIR = Paths.get("data", "places");
    public static final Path FILE = OUT_DIR.resolve("places.tsv");
    private static final Path CACHE = OUT_DIR.resolve("cache");
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public static void main(String[] args) throws Exception {
        build(args.length == 0 ? List.of("IN") : Arrays.stream(args).map(String::toUpperCase).toList());
    }

    static Path download(String file) throws Exception {
        Path dest = CACHE.resolve(file);
        if (Files.exists(dest) && Files.size(dest) > 0) return dest;
        System.out.println("  downloading " + file + " ...");
        HttpResponse<Path> r = HTTP.send(HttpRequest.newBuilder(URI.create(BASE + file)).build(), HttpResponse.BodyHandlers.ofFile(dest));
        if (r.statusCode() != 200) { Files.deleteIfExists(dest); throw new IOException(file + ": HTTP " + r.statusCode()); }
        return dest;
    }

    static Map<String, String> table(Path p, int keyCol, int valCol) throws IOException {
        Map<String, String> m = new HashMap<>();
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            if (f.length > Math.max(keyCol, valCol)) m.put(f[keyCol], f[valCol]);
        }
        return m;
    }

    static String clean(String s) { return s == null ? "" : s.replaceAll("[\\t\\r\\n]", " ").trim(); }

    public static synchronized void build(List<String> countries) throws Exception {
        Files.createDirectories(CACHE);
        System.out.println("[places] building birth-place database from GeoNames…");
        Map<String, String> countryNames = table(download("countryInfo.txt"), 0, 4);
        Map<String, String> admin1 = table(download("admin1CodesASCII.txt"), 0, 1);
        Map<String, String> admin2 = table(download("admin2Codes.txt"), 0, 1);
        Set<String> seen = new HashSet<>();
        Path tmp = OUT_DIR.resolve("places.tsv.tmp");
        try (BufferedWriter out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            out.write("name\tascii\talts\tdistrict\tstate\tcountry\tcc\tlat\tlon\ttz\tpop\n");
            for (String cc : countries) {
                long n = addRows(download(cc + ".zip"), cc + ".txt", null, seen, countryNames, admin1, admin2, out);
                System.out.printf("  %s: %,d places%n", cc, n);
            }
            Set<String> skip = new HashSet<>(countries);
            long n = addRows(download("cities500.zip"), "cities500.txt", skip, seen, countryNames, admin1, admin2, out);
            System.out.printf("  world (pop >= 500): %,d places%n", n);
        }
        Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("[places] done -> " + FILE);
    }

    static long addRows(Path zip, String entry, Set<String> skipCountries, Set<String> seen, Map<String, String> countryNames,
                        Map<String, String> admin1, Map<String, String> admin2, BufferedWriter out) throws IOException {
        long n = 0;
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.getName().equals(entry)) continue;
                BufferedReader br = new BufferedReader(new InputStreamReader(zin, StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) {
                    String[] f = line.split("\t", -1);
                    if (f.length < 18 || !"P".equals(f[6])) continue; // populated places only
                    String cc = f[8];
                    if (skipCountries != null && skipCountries.contains(cc)) continue;
                    if (!seen.add(f[0])) continue;
                    StringJoiner alts = new StringJoiner("|");
                    int k = 0;
                    for (String a : f[3].split(",")) {
                        if (k < 4 && a.matches("[A-Za-z][A-Za-z .'-]{1,39}") && !a.equals(f[2])) { alts.add(a); k++; }
                    }
                    out.write(String.join("\t", clean(f[1]), clean(f[2]), clean(alts.toString()),
                            clean(admin2.get(cc + "." + f[10] + "." + f[11])), clean(admin1.get(cc + "." + f[10])),
                            clean(countryNames.getOrDefault(cc, cc)), cc,
                            String.format(Locale.ROOT, "%.4f", Double.parseDouble(f[4])), String.format(Locale.ROOT, "%.4f", Double.parseDouble(f[5])),
                            f[17], f[14].isEmpty() ? "0" : f[14]));
                    out.write('\n');
                    n++;
                }
                break;
            }
        }
        return n;
    }
}
