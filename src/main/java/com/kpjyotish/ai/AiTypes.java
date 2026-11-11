package com.kpjyotish.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Structured-output types for Claude (JSON schema is derived from these records). */
public final class AiTypes {
    private AiTypes() {}

    public record ChatTurn(
            @JsonPropertyDescription("Your message to the client, plain text, max ~180 words, in the client's language.")
            String reply,
            @JsonPropertyDescription("NEW durable facts learned about the client in their latest message (education, work, goals, interests, family situation, concerns, preferences). Short third-person sentences. Empty if nothing new.")
            List<String> newMemories,
            @JsonPropertyDescription("True once you have understood their question, asked your follow-up questions and given your answer, so a personalised PDF report can be generated.")
            boolean readyForReport,
            @JsonPropertyDescription("Short label of what this consultation is about, e.g. 'Career: MPC vs BiPC after 10th'. Empty if not known yet.")
            String topic) {}

    public record Hook(String headline, String teaser, List<String> secrets) {}

    public record Section(String title, String content) {}

    public record ReportContent(
            @JsonPropertyDescription("Page 1 hook: gripping headline (max 14 words), ~90-word teaser revealing one specific insight and ending on a cliff-hanger, and 4 one-line teasers of what the locked pages reveal.")
            Hook hook,
            @JsonPropertyDescription("~120-word overview of the chart and of the client's question.")
            String summary,
            @JsonPropertyDescription("3-4 sections that answer the client's own consultation questions in depth (220-320 words each).")
            List<Section> consultation,
            @JsonPropertyDescription("Life-area chapters in the requested order (300-450 words each).")
            List<Section> sections) {}

    /** Report content plus where it came from (ai:model or rule-based). */
    public record ReportResult(ReportContent content, String source) {}
}
