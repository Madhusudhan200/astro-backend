package com.kpjyotish.pdf;

import java.util.List;

/** Simple document model: the report builder produces blocks, the PDF renderer draws them. */
public final class Doc {
    private Doc() {}

    public sealed interface Block permits Chapter, Heading, Para, Bullets, Table, KeyValue, Charts, Note {}

    /** Starts a new page with a chapter title band (listed in the table of contents). */
    public record Chapter(String title, String subtitle) implements Block {}

    public record Heading(String text) implements Block {}

    /** Paragraphs separated by blank lines. */
    public record Para(String text) implements Block {}

    public record Bullets(List<String> items) implements Block {}

    public record Table(List<String> headers, List<List<String>> rows, float[] widths) implements Block {}

    public record KeyValue(List<String[]> pairs) implements Block {}

    /** One or two South-Indian charts side by side. */
    public record Charts(List<ChartGrid> charts) implements Block {}

    public record ChartGrid(String title, String subtitle, List<List<String>> grid, int ascSign) {}

    /** Small italic note. */
    public record Note(String text) implements Block {}
}
