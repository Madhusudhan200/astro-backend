package com.kpjyotish.pdf;

import com.kpjyotish.ai.AiTypes.Hook;
import com.kpjyotish.kp.Chart;
import com.kpjyotish.pdf.Doc.*;
import com.kpjyotish.pdf.Doc.Chapter;
import com.kpjyotish.pdf.Doc.Table;
import com.kpjyotish.store.Entities.User;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;

/**
 * Renders the report with OpenPDF.
 * full()    -> the complete paid report (90+ pages)
 * preview() -> page 1 (hook) is real; every other page is a blurred placeholder showing only its
 *              page number. Locked content is never included in the preview file.
 */
public final class PdfRenderer {
    private PdfRenderer() {}

    static final Color ORANGE = new Color(0xEA580C), AMBER = new Color(0xF59E0B), DEEP = new Color(0x7C2D12), INK = new Color(0x2B1606),
            SOFT = new Color(0xFFF7ED), LINE = new Color(0xFED7AA), MUTED = new Color(0x9A3412), WHITE = Color.WHITE, ASC = new Color(0xFFEDD5);
    static final float W = PageSize.A4.getWidth(), H = PageSize.A4.getHeight(), M = 50;

    static Font f(float size, int style, Color c) { return PdfFonts.font(size, style, c); }
    static Paragraph para(String s, Font font) { Paragraph p = new Paragraph(); p.setFont(font); p.add(PdfFonts.phrase(s, font)); return p; }
    static Paragraph para(float lead, String s, Font font) { Paragraph p = para(s, font); p.setLeading(lead); return p; }
    static Font BODY() { return f(10.5f, Font.NORMAL, INK); }
    static Font BOLD() { return f(10.5f, Font.BOLD, INK); }
    static Font SMALL() { return f(8.5f, Font.NORMAL, INK); }
    static Font SMALLB() { return f(8.5f, Font.BOLD, INK); }
    static Font TH() { return f(8.5f, Font.BOLD, WHITE); }
    static Font H2() { return f(13, Font.BOLD, DEEP); }
    static Font NOTE() { return f(8.5f, Font.ITALIC, MUTED); }

    /** Replace characters the standard PDF fonts can't show. */
    static String safe(String s) {
        if (s == null) return "";
        if (PdfFonts.active()) return s.replace("₹", "Rs.").replaceAll("[\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}]", "");
        // Turn accented letters (e.g. Kondāpur, Guntūr) into plain letters the PDF font can show
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]", "");
        return s.replace("₹", "Rs.").replace("–", "-").replace("—", "-").replace("’", "'").replace("‘", "'").replace("“", "\"").replace("”", "\"")
                .replaceAll("[\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}]", "").replaceAll("[^\\x00-\\x7F\\u00A0-\\u00FF\\u2014\\u2022\\u2026]", "");
    }

    public record Result(byte[] pdf, int pages) {}

    /** Fixed labels drawn by the renderer itself (translated together with the report text). */
    public static final java.util.List<String> LABELS = java.util.List.of("Contents", "Everything inside your KP horoscope", "KP Horoscope Report", "Prepared for",
            "Name", "Date of Birth", "Time of Birth", "Place", "Ascendant", "Moon / Nakshatra", "Ayanamsa", "Running Dasha", "Rasi Chart",
            "What your stars are saying...", "LOCKED INSIDE YOUR FULL REPORT", "INSIDE THIS REPORT", "Unlock all pages of your report in your portal");
    private static final ThreadLocal<Map<String, String>> LABEL_TR = new ThreadLocal<>();
    public static void labels(Map<String, String> tr) { if (tr == null) LABEL_TR.remove(); else LABEL_TR.set(tr); }
    static String L(String en) { Map<String, String> m = LABEL_TR.get(); return m == null ? en : m.getOrDefault(en, en); }
    /** Justified text breaks joined scripts (Telugu, Hindi...), so translated reports are left/right aligned. */
    static int just() { return !PdfFonts.active() ? Element.ALIGN_JUSTIFIED : PdfFonts.CURRENT.get().rtl() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT; }

    // ======================================================================= public API
    public static Result full(User u, Chart c, Hook hook, List<Block> blocks, String site) {
        Map<String, Integer> toc = new LinkedHashMap<>();
        Result pass1 = render(u, c, hook, blocks, site, toc, null);
        Map<String, Integer> pages = new LinkedHashMap<>(toc);
        toc.clear();
        Result pass2 = render(u, c, hook, blocks, site, toc, pages);
        return new Result(pass2.pdf(), Math.max(pass1.pages(), pass2.pages()));
    }

    public static byte[] preview(User u, Chart c, Hook hook, int totalPages, String site) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, M, M, 70, 55);
            PdfWriter w = PdfWriter.getInstance(doc, out);
            w.setPageEvent(new Frame(site, "Preview", totalPages));
            doc.open();
            hookPage(w, u, c, hook, site, true);
            Random rnd = new Random(u.id.hashCode());
            // Three blurred page backgrounds drawn once and reused -> small file, fast to load
            PdfTemplate[] bg = new PdfTemplate[3];
            for (int k = 0; k < 3; k++) { bg[k] = w.getDirectContent().createTemplate(W, H); blurBody(bg[k], rnd); }
            for (int p = 2; p <= totalPages; p++) {
                w.setPageEmpty(false);
                doc.newPage();
                w.getDirectContent().addTemplate(bg[p % 3], 0, 0);
                lockBadge(w.getDirectContent(), p, totalPages);
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("preview render failed", e); }
    }

    // ======================================================================= page frame (header/footer)
    static class Frame extends PdfPageEventHelper {
        final String site, label; final Integer fixedTotal;
        PdfTemplate total; BaseFont bf;
        Frame(String site, String label, Integer fixedTotal) { this.site = site; this.label = label; this.fixedTotal = fixedTotal; }

        @Override public void onOpenDocument(PdfWriter w, Document d) {
            try { bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false); } catch (Exception e) { throw new IllegalStateException(e); }
            total = w.getDirectContent().createTemplate(40, 14);
        }

        @Override public void onEndPage(PdfWriter w, Document d) {
            PdfContentByte cb = w.getDirectContentUnder();
            int n = w.getPageNumber();
            if (n > 1) {
                cb.saveState(); cb.setColorFill(ORANGE); cb.rectangle(0, H - 40, W, 40); cb.fill(); cb.setColorFill(AMBER); cb.rectangle(0, H - 43, W, 3); cb.fill(); cb.restoreState();
                PdfContentByte top = w.getDirectContent();
                top.beginText(); top.setFontAndSize(bf, 11); top.setColorFill(WHITE); top.setTextMatrix(M, H - 25); top.showText(safe(site).toUpperCase(Locale.ROOT)); top.endText();
                top.beginText(); top.setFontAndSize(bf, 8.5f); top.showTextAligned(PdfContentByte.ALIGN_RIGHT, "KP Horoscope - Krishnamurti Paddhati", W - M, H - 24, 0); top.endText();
            }
            PdfContentByte fc = w.getDirectContent();
            String txt = "Page " + n + " of ";
            float tw = bf.getWidthPoint(txt, 8.5f);
            fc.beginText(); fc.setFontAndSize(bf, 8.5f); fc.setColorFill(MUTED);
            fc.setTextMatrix(W / 2 - tw / 2 - 8, 26); fc.showText(txt); fc.endText();
            if (fixedTotal != null) {
                fc.beginText(); fc.setFontAndSize(bf, 8.5f); fc.setTextMatrix(W / 2 + tw / 2 - 8, 26); fc.showText(String.valueOf(fixedTotal)); fc.endText();
            } else fc.addTemplate(total, W / 2 + tw / 2 - 8, 26);
            fc.beginText(); fc.setFontAndSize(bf, 8.5f); fc.setTextMatrix(M, 26); fc.showText(label); fc.endText();
        }

        @Override public void onCloseDocument(PdfWriter w, Document d) {
            total.beginText(); total.setFontAndSize(bf, 8.5f); total.setColorFill(MUTED); total.setTextMatrix(0, 0);
            total.showText(String.valueOf(w.getPageNumber() - 1)); total.endText();
        }
    }

    // ======================================================================= full render
    static Result render(User u, Chart c, Hook hook, List<Block> blocks, String site, Map<String, Integer> tocOut, Map<String, Integer> tocPages) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, M, M, 70, 55);
            PdfWriter w = PdfWriter.getInstance(doc, out);
            w.setPageEvent(new Frame(site, safe(site), null));
            doc.addTitle("KP Horoscope - " + safe(u.fullName));
            doc.addAuthor(safe(site));
            doc.open();
            hookPage(w, u, c, hook, site, false);

            // Table of contents (same size in both passes so pagination is stable)
            w.setPageEmpty(false);
            doc.newPage();
            chapterBand(doc, L("Contents"), L("Everything inside your KP horoscope"));
            PdfPTable t = new PdfPTable(new float[]{85, 15});
            t.setWidthPercentage(100);
            int i = 0;
            for (Block b : blocks) if (b instanceof Chapter ch) {
                Integer pg = tocPages == null ? null : tocPages.get(++i + ":" + ch.title());
                PdfPCell a = cell(safe(ch.title()), SMALL(), i % 2 == 0 ? SOFT : WHITE); a.setPaddingTop(3.5f); a.setPaddingBottom(3.5f);
                PdfPCell z = cell(pg == null ? "00" : String.valueOf(pg), SMALLB(), i % 2 == 0 ? SOFT : WHITE); z.setHorizontalAlignment(Element.ALIGN_RIGHT); z.setPaddingTop(3.5f); z.setPaddingBottom(3.5f);
                t.addCell(a); t.addCell(z);
            }
            doc.add(t);

            int chapterNo = 0;
            for (Block b : blocks) {
                if (b instanceof Chapter ch) {
                    doc.newPage();
                    tocOut.put(++chapterNo + ":" + ch.title(), w.getPageNumber());
                    chapterBand(doc, ch.title(), ch.subtitle());
                } else if (b instanceof Heading h) {
                    heading(doc, h.text());
                } else if (b instanceof Para p) {
                    for (String para : p.text().split("\\n\\s*\\n")) {
                        Paragraph pg = para(15, safe(para.trim()), BODY());
                        pg.setAlignment(just()); pg.setSpacingAfter(7);
                        doc.add(pg);
                    }
                } else if (b instanceof Bullets bl) {
                    for (String item : bl.items()) {
                        Paragraph pg = para(15, "•  " + safe(item), BODY());
                        pg.setIndentationLeft(12); pg.setFirstLineIndent(-10); pg.setSpacingAfter(4);
                        doc.add(pg);
                    }
                } else if (b instanceof Table tb) {
                    table(doc, tb);
                } else if (b instanceof KeyValue kv) {
                    keyValue(doc, kv);
                } else if (b instanceof Charts cs) {
                    charts(doc, cs);
                } else if (b instanceof Note n) {
                    Paragraph pg = para(13, safe(n.text()), NOTE()); pg.setSpacingBefore(6); doc.add(pg);
                }
            }
            doc.close();
            byte[] bytes = out.toByteArray();
            PdfReader rd = new PdfReader(bytes);
            int n = rd.getNumberOfPages();
            rd.close();
            return new Result(bytes, n);
        } catch (Exception e) { throw new IllegalStateException("report render failed: " + e.getMessage(), e); }
    }

    static PdfPCell cell(String text, Font font, Color bg) {
        PdfPCell c = new PdfPCell(PdfFonts.phrase(text, font));
        c.setBackgroundColor(bg); c.setBorderColor(LINE); c.setBorderWidth(0.5f); c.setPadding(5);
        return c;
    }

    static void chapterBand(Document doc, String title, String subtitle) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(ORANGE); c.setBorder(Rectangle.NO_BORDER); c.setPadding(12); c.setPaddingBottom(14);
        Paragraph p = para(safe(title), f(17, Font.BOLD, WHITE));
        c.addElement(p);
        if (subtitle != null && !subtitle.isBlank()) c.addElement(para(safe(subtitle), f(9.5f, Font.NORMAL, new Color(0xFFEDD5))));
        t.addCell(c);
        PdfPCell bar = new PdfPCell(); bar.setFixedHeight(3); bar.setBackgroundColor(AMBER); bar.setBorder(Rectangle.NO_BORDER);
        t.addCell(bar);
        t.setSpacingAfter(14);
        doc.add(t);
    }

    static void heading(Document doc, String text) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{1.2f, 98.8f});
        t.setWidthPercentage(100); t.setSpacingBefore(8); t.setSpacingAfter(8); t.setKeepTogether(true);
        PdfPCell a = new PdfPCell(); a.setBackgroundColor(ORANGE); a.setBorder(Rectangle.NO_BORDER);
        PdfPCell b = new PdfPCell(PdfFonts.phrase(safe(text), H2())); b.setBackgroundColor(SOFT); b.setBorder(Rectangle.NO_BORDER); b.setPadding(6); b.setPaddingLeft(9);
        t.addCell(a); t.addCell(b);
        doc.add(t);
    }

    static void table(Document doc, Table tb) throws DocumentException {
        PdfPTable t = new PdfPTable(tb.widths());
        t.setWidthPercentage(100); t.setHeaderRows(1); t.setSpacingAfter(10);
        for (String h : tb.headers()) t.addCell(cell(safe(h), TH(), ORANGE));
        int r = 0;
        for (List<String> row : tb.rows()) {
            Color bg = r++ % 2 == 0 ? SOFT : WHITE;
            for (String v : row) t.addCell(cell(safe(v), SMALL(), bg));
        }
        doc.add(t);
    }

    static void keyValue(Document doc, KeyValue kv) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{34, 66});
        t.setWidthPercentage(100); t.setSpacingAfter(10);
        int r = 0;
        for (String[] p : kv.pairs()) {
            Color bg = r++ % 2 == 0 ? SOFT : WHITE;
            t.addCell(cell(safe(p[0]), f(9, Font.NORMAL, MUTED), bg));
            t.addCell(cell(safe(p[1]), f(9, Font.BOLD, INK), bg));
        }
        doc.add(t);
    }

    // ======================================================================= south-indian chart
    static final int[] SI_ROW_ORDER = {11, 0, 1, 2, 10, -1, 3, 9, 4, 8, 7, 6, 5}; // -1 = centre block
    static final String[] SHORT = {"Ari", "Tau", "Gem", "Can", "Leo", "Vir", "Lib", "Sco", "Sag", "Cap", "Aqu", "Pis"};

    static PdfPTable chartTable(ChartGrid g, float cellH) {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        for (int sign : SI_ROW_ORDER) {
            if (sign == -1) {
                PdfPCell c = new PdfPCell();
                c.setColspan(2); c.setRowspan(2); c.setBackgroundColor(new Color(0xFFFBF5)); c.setBorderColor(ORANGE);
                c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setHorizontalAlignment(Element.ALIGN_CENTER);
                Paragraph p1 = para(safe(g.title()), f(11, Font.BOLD, ORANGE)); p1.setAlignment(Element.ALIGN_CENTER);
                Paragraph p2 = para(safe(g.subtitle()), f(8, Font.NORMAL, MUTED)); p2.setAlignment(Element.ALIGN_CENTER);
                c.addElement(p1); c.addElement(p2);
                t.addCell(c);
                continue;
            }
            PdfPCell c = new PdfPCell();
            c.setFixedHeight(cellH); c.setBorderColor(ORANGE); c.setBorderWidth(0.8f); c.setPadding(3);
            if (sign == g.ascSign()) c.setBackgroundColor(ASC);
            Paragraph lab = para(SHORT[sign] + (sign == g.ascSign() ? "   Asc" : ""), f(6.5f, sign == g.ascSign() ? Font.BOLD : Font.NORMAL, sign == g.ascSign() ? ORANGE : MUTED));
            c.addElement(lab);
            Paragraph pl = para(10, safe(String.join(" ", g.grid().get(sign))), f(8.5f, Font.BOLD, INK));
            c.addElement(pl);
            t.addCell(c);
        }
        return t;
    }

    static void charts(Document doc, Charts cs) throws DocumentException {
        int n = cs.charts().size();
        PdfPTable outer = new PdfPTable(n);
        outer.setWidthPercentage(n == 1 ? 62 : 100); outer.setSpacingAfter(12); outer.setKeepTogether(true);
        for (ChartGrid g : cs.charts()) {
            PdfPCell c = new PdfPCell(chartTable(g, n == 1 ? 62 : 52));
            c.setBorder(Rectangle.NO_BORDER); c.setPadding(n == 1 ? 0 : 6);
            outer.addCell(c);
        }
        doc.add(outer);
    }

    // ======================================================================= page 1 (hook)
    static void hookPage(PdfWriter w, User u, Chart c, Hook hook, String site, boolean preview) throws DocumentException {
        PdfContentByte cb = w.getDirectContent();
        PdfShading sh = PdfShading.simpleAxial(w, 0, H - 190, W, H, ORANGE, AMBER);
        cb.saveState(); cb.setShadingFill(new PdfShadingPattern(sh)); cb.rectangle(0, H - 190, W, 190); cb.fill(); cb.restoreState();
        PdfGState gs = new PdfGState(); gs.setFillOpacity(0.13f);
        cb.saveState(); cb.setGState(gs); cb.setColorFill(WHITE); cb.circle(W - 70, H - 40, 90); cb.fill(); cb.circle(W - 30, H - 160, 50); cb.fill(); cb.restoreState();

        text(cb, safe(site).toUpperCase(Locale.ROOT) + "  |  KRISHNAMURTI PADDHATI", f(10, Font.NORMAL, WHITE), M, H - 52, W - 2 * M, 14);
        text(cb, L("KP Horoscope Report"), f(26, Font.BOLD, WHITE), M, H - 92, W - 2 * M, 34);
        text(cb, L("Prepared for") + " " + safe(u.fullName), f(13, Font.NORMAL, WHITE), M, H - 112, W - 2 * M, 18);
        text(cb, u.dob + "  |  " + u.tob + "  |  " + safe(u.city), f(10, Font.NORMAL, WHITE), M, H - 130, W - 2 * M, 14);
        text(cb, safe(hook.headline()), f(14.5f, Font.BOLD, WHITE), M, H - 178, W - 2 * M, 40);

        // birth box
        float by = H - 420;
        cb.saveState(); cb.setColorFill(SOFT); cb.roundRectangle(M, by, 255, 200, 8); cb.fill(); cb.restoreState();
        String[][] rows = {{L("Name"), u.fullName}, {L("Date of Birth"), u.dob}, {L("Time of Birth"), u.tob}, {L("Place"), u.city}, {L("Ascendant"), c.ascendant.sign + " " + c.ascendant.dms},
                {L("Moon / Nakshatra"), c.moonSign + " / " + c.nakshatra}, {L("Ayanamsa"), "KP " + c.ayanamsa.dms}, {L("Running Dasha"), c.currentDasha.mahadasha + " / " + c.currentDasha.antardasha}};
        for (int i = 0; i < rows.length; i++) {
            float y = H - 245 - i * 21;
            text(cb, rows[i][0], f(8.5f, Font.NORMAL, MUTED), M + 10, y, 90, 12);
            text(cb, safe(rows[i][1]), f(9, Font.BOLD, INK), M + 100, y, 150, 12);
        }
        // chart (absolute)
        PdfPTable chart = chartTable(new ChartGrid(L("Rasi Chart"), "KP / Sidereal", c.rasi, c.ascendant.signIdx), 50);
        chart.setTotalWidth(215);
        chart.writeSelectedRows(0, -1, W - M - 215, H - 220, cb);

        // teaser
        text(cb, L("What your stars are saying..."), f(13, Font.BOLD, DEEP), M, H - 450, W - 2 * M, 18);
        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(M, H - 600, W - M, H - 458);
        Paragraph tp = para(15, safe(hook.teaser()), BODY()); tp.setAlignment(just());
        ct.addElement(tp); ct.go();

        // secrets box
        float sy = 140;
        cb.saveState(); cb.setColorStroke(ORANGE); cb.setLineWidth(1.5f); cb.roundRectangle(M, sy, W - 2 * M, 155, 10); cb.stroke(); cb.restoreState();
        text(cb, preview ? L("LOCKED INSIDE YOUR FULL REPORT") : L("INSIDE THIS REPORT"), f(11.5f, Font.BOLD, ORANGE), M + 16, sy + 128, W - 2 * M, 16);
        List<String> secrets = hook.secrets() == null ? List.of() : hook.secrets();
        for (int i = 0; i < Math.min(4, secrets.size()); i++) {
            float y = sy + 100 - i * 24;
            cb.saveState(); cb.setColorFill(AMBER); cb.circle(M + 22, y + 4, 3); cb.fill(); cb.restoreState();
            text(cb, safe(secrets.get(i)), f(10, Font.NORMAL, INK), M + 32, y, W - 2 * M - 50, 14);
        }
        if (preview) {
            cb.saveState(); cb.setColorFill(ORANGE); cb.roundRectangle(W / 2 - 150, 92, 300, 34, 17); cb.fill(); cb.restoreState();
            text(cb, L("Unlock all pages of your report in your portal"), f(11, Font.BOLD, WHITE), W / 2 - 150, 104, 300, 14, Element.ALIGN_CENTER);
        }
    }

    static void text(PdfContentByte cb, String s, Font font, float x, float y, float width, float height) { text(cb, s, font, x, y, width, height, Element.ALIGN_LEFT); }

    static void text(PdfContentByte cb, String s, Font font, float x, float y, float width, float height, int align) {
        try {
            ColumnText ct = new ColumnText(cb);
            ct.setSimpleColumn(PdfFonts.phrase(s, font), x, y - 4, x + width, y + height, height, align);
            ct.go();
        } catch (DocumentException e) { throw new IllegalStateException(e); }
    }

    // ======================================================================= blurred locked page
    static void blurBody(PdfContentByte cb, Random rnd) {
        PdfGState g = new PdfGState(); g.setFillOpacity(0.09f);
        float y = H - 90;
        while (y > 110) {
            double k = rnd.nextDouble();
            if (k < 0.12) { blurBar(cb, g, M, y, W - 2 * M, 22, ORANGE); y -= 40; }
            else if (k < 0.24) { for (int r = 0; r < 7; r++) { blurBar(cb, g, M, y, W - 2 * M, 12, r == 0 ? ORANGE : AMBER); y -= 18; } y -= 12; }
            else {
                int lines = 4 + rnd.nextInt(5);
                for (int l = 0; l < lines; l++) {
                    float lw = (float) ((W - 2 * M) * (l == lines - 1 ? 0.35 + rnd.nextDouble() * 0.4 : 0.88 + rnd.nextDouble() * 0.12));
                    blurBar(cb, g, M, y, lw, 7, DEEP); y -= 15;
                }
                y -= 14;
            }
        }
        PdfGState frost = new PdfGState(); frost.setFillOpacity(0.35f);
        cb.saveState(); cb.setGState(frost); cb.setColorFill(SOFT); cb.rectangle(0, 0, W, H - 44); cb.fill(); cb.restoreState();
    }

    static void lockBadge(PdfContentByte cb, int pageNo, int total) {
        float cx = W / 2, cy = H / 2 + 20;
        cb.saveState(); cb.setColorFill(ORANGE); cb.circle(cx, cy, 46); cb.fill(); cb.restoreState();
        cb.saveState(); cb.setColorStroke(WHITE); cb.setLineWidth(5); cb.moveTo(cx - 12, cy + 2); cb.lineTo(cx - 12, cy + 14);
        cb.curveTo(cx - 12, cy + 30, cx + 12, cy + 30, cx + 12, cy + 14); cb.lineTo(cx + 12, cy + 2); cb.stroke(); cb.restoreState();
        cb.saveState(); cb.setColorFill(WHITE); cb.roundRectangle(cx - 18, cy - 22, 36, 26, 4); cb.fill(); cb.setColorFill(ORANGE); cb.circle(cx, cy - 9, 4); cb.fill(); cb.restoreState();
        text(cb, "Page " + pageNo, f(30, Font.BOLD, DEEP), 0, cy - 100, W, 36, Element.ALIGN_CENTER);
        text(cb, "of " + total + "  -  locked", f(11, Font.NORMAL, MUTED), 0, cy - 122, W, 16, Element.ALIGN_CENTER);
    }

    static void blurBar(PdfContentByte cb, PdfGState g, float x, float y, float bw, float bh, Color col) {
        cb.saveState(); cb.setGState(g); cb.setColorFill(col);
        for (int k = 0; k < 6; k++) {
            float s = k * 1.6f;
            cb.roundRectangle(x - s, y - s, bw + 2 * s, bh + 2 * s, (bh + 2 * s) / 2);
            cb.fill();
        }
        cb.restoreState();
    }
}
