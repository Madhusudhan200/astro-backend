package com.kpjyotish.pdf;

import com.kpjyotish.ai.AiTypes.Hook;
import com.kpjyotish.pdf.Doc.*;

import java.util.*;
import java.util.function.UnaryOperator;

/** Collects every text in the report blocks and rebuilds the blocks with translated text. */
public final class BlockTranslator {
    private BlockTranslator() {}

    /** Paragraph texts are split on blank lines so each paragraph is translated separately. */
    public static Set<String> texts(List<Block> blocks, Hook hook) {
        Set<String> s = new LinkedHashSet<>();
        map(blocks, hook, t -> { s.add(t); return t; });
        return s;
    }

    public record Result(List<Block> blocks, Hook hook) {}

    public static Result apply(List<Block> blocks, Hook hook, Map<String, String> tr) {
        return map(blocks, hook, t -> tr.getOrDefault(t, t));
    }

    static String paras(String text, UnaryOperator<String> fn) {
        StringJoiner j = new StringJoiner("\n\n");
        for (String p : text.split("\\n\\s*\\n")) { String x = p.trim(); if (!x.isEmpty()) j.add(fn.apply(x)); }
        return j.toString();
    }

    static Result map(List<Block> blocks, Hook hook, UnaryOperator<String> fn) {
        List<Block> out = new ArrayList<>();
        for (Block b : blocks) {
            if (b instanceof Chapter c) out.add(new Chapter(fn.apply(c.title()), c.subtitle() == null ? null : fn.apply(c.subtitle())));
            else if (b instanceof Heading h) out.add(new Heading(fn.apply(h.text())));
            else if (b instanceof Para p) out.add(new Para(paras(p.text(), fn)));
            else if (b instanceof Bullets bl) out.add(new Bullets(bl.items().stream().map(fn).toList()));
            else if (b instanceof Note n) out.add(new Note(fn.apply(n.text())));
            else if (b instanceof Table t) out.add(new Table(t.headers().stream().map(fn).toList(), t.rows().stream().map(r -> r.stream().map(fn).toList()).toList(), t.widths()));
            else if (b instanceof KeyValue kv) { List<String[]> l = new ArrayList<>(); for (String[] p : kv.pairs()) l.add(new String[]{fn.apply(p[0]), fn.apply(p[1])}); out.add(new KeyValue(l)); }
            else if (b instanceof Charts cs) out.add(new Charts(cs.charts().stream().map(g -> new ChartGrid(fn.apply(g.title()), fn.apply(g.subtitle()), g.grid(), g.ascSign())).toList()));
            else out.add(b);
        }
        Hook h = hook == null ? null : new Hook(fn.apply(hook.headline()), paras(hook.teaser(), fn), hook.secrets() == null ? List.of() : hook.secrets().stream().map(fn).toList());
        return new Result(out, h);
    }
}
