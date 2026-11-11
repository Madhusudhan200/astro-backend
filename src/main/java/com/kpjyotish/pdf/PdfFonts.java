package com.kpjyotish.pdf;

import com.kpjyotish.i18n.Lang;
import com.lowagie.text.Chunk;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.LayoutProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unicode fonts for reports in Indian and world languages. Noto fonts are downloaded once into
 * data/fonts. Complex scripts (Telugu, Hindi, Tamil, Arabic...) are shaped by OpenPDF's
 * LayoutProcessor (Java's text layout engine). Latin letters/digits inside translated text use
 * Noto Sans, so mixed text never shows blank boxes.
 */
public final class PdfFonts {
    private PdfFonts() {}
    private static final Logger log = LoggerFactory.getLogger(PdfFonts.class);
    private static final Path DIR = Paths.get("data", "fonts");
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
    private static final Map<String, BaseFont> CACHE = new ConcurrentHashMap<>();

    /** Fonts in use for the current render (null = classic Helvetica / English). */
    record Set(BaseFont reg, BaseFont bold, BaseFont latin, BaseFont latinBold, boolean rtl) {}
    static final ThreadLocal<Set> CURRENT = new ThreadLocal<>();

    static { LayoutProcessor.enableKernLiga(); }

    /** Select fonts for a language code for the current thread ("en" or null resets to Helvetica). */
    public static void use(String langCode) throws Exception {
        Lang l = Lang.get(langCode);
        if (l == null || l.code().equals("en")) { CURRENT.remove(); return; }
        BaseFont latin = font("NotoSans", false), latinBold = font("NotoSans", true);
        String fam = l.fontFamily();
        BaseFont reg = fam.equals("NotoSans") ? latin : font(fam, false);
        BaseFont bold = fam.equals("NotoSans") ? latinBold : font(fam, true);
        CURRENT.set(new Set(reg, bold, latin, latinBold, l.rtl()));
    }

    public static void reset() { CURRENT.remove(); }

    static boolean active() { return CURRENT.get() != null; }

    /** Downloads (once) and loads a Noto font. CJK families come as single variable fonts (no separate bold). */
    static BaseFont font(String family, boolean bold) throws Exception {
        String key = family + (bold ? "-Bold" : "-Regular");
        BaseFont cached = CACHE.get(key);
        if (cached != null) return cached;
        synchronized (PdfFonts.class) {
            cached = CACHE.get(key);
            if (cached != null) return cached;
            Files.createDirectories(DIR);
            boolean cjk = family.startsWith("NotoSansSC") || family.startsWith("NotoSansTC") || family.startsWith("NotoSansJP") || family.startsWith("NotoSansKR");
            Path file = DIR.resolve(cjk ? family + ".ttf" : key + ".ttf");
            if (!Files.exists(file) || Files.size(file) < 1000) {
                String url = cjk
                        ? "https://github.com/google/fonts/raw/main/ofl/" + family.toLowerCase() + "/" + family + "%5Bwght%5D.ttf"
                        : "https://github.com/notofonts/notofonts.github.io/raw/main/fonts/" + family + "/hinted/ttf/" + key + ".ttf";
                log.info("downloading font {}", url);
                HttpResponse<Path> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).build(), HttpResponse.BodyHandlers.ofFile(file));
                if (r.statusCode() != 200) {
                    Files.deleteIfExists(file);
                    if (bold) return font(family, false); // some scripts have no bold file
                    throw new IllegalStateException("Font download failed (" + r.statusCode() + "): " + url);
                }
            }
            BaseFont bf = BaseFont.createFont(file.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            LayoutProcessor.loadFont(bf, file.toString());
            CACHE.put(key, bf);
            return bf;
        }
    }

    /** Font for the current language (or Helvetica). */
    static Font font(float size, int style, java.awt.Color c) {
        Set s = CURRENT.get();
        if (s == null) return new Font(Font.HELVETICA, size, style, c);
        boolean b = (style & Font.BOLD) != 0;
        Font f = new Font(b ? s.bold() : s.reg(), size, Font.NORMAL, c);
        if (s.rtl()) LayoutProcessor.setRunDirectionRtl(f);
        return f;
    }

    /** Characters drawn with Noto Sans: Latin letters, digits, punctuation, dashes, bullets, degree signs. */
    static boolean isLatin(char ch) { return ch != ' ' && (ch < 0x250 || (ch >= 0x2000 && ch <= 0x206F)); }

    /** Phrase that uses the script font for native text and Noto Sans for Latin letters/digits. */
    static Phrase phrase(String text, Font f) {
        Set s = CURRENT.get();
        if (s == null || text.isEmpty()) return new Phrase(text, f);
        boolean bold = f.getBaseFont() == s.bold() || f.getBaseFont() == s.latinBold();
        Font latin = new Font(bold ? s.latinBold() : s.latin(), f.getSize(), Font.NORMAL, f.getColor());
        Font script = new Font(bold ? s.bold() : s.reg(), f.getSize(), Font.NORMAL, f.getColor());
        if (s.rtl()) LayoutProcessor.setRunDirectionRtl(script);
        Phrase p = new Phrase();
        p.setFont(script);
        StringBuilder run = new StringBuilder();
        Boolean runLatin = null;
        for (char ch : text.toCharArray()) {
            boolean lat = ch == ' ' ? (runLatin != null && runLatin) : isLatin(ch);
            if (runLatin != null && lat != runLatin) { p.add(new Chunk(run.toString(), runLatin ? latin : script)); run.setLength(0); }
            runLatin = lat;
            run.append(ch);
        }
        if (run.length() > 0) p.add(new Chunk(run.toString(), Boolean.TRUE.equals(runLatin) ? latin : script));
        return p;
    }
}
