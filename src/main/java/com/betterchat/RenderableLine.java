package com.betterchat;

/**
 * One line of text ready to be drawn on screen.
 * A single chat message may produce several RenderableLines when it word-wraps.
 * Date separators (e.g. "2026/02/24") are also stored as RenderableLines with
 * {@code isSeparator = true}.
 */
public class RenderableLine {
    public final String text;
    public final boolean isSeparator;
    public final String time;
    public final String date;
    public final ChatTabData.ChatMessage sourceMsg;
    /** Character offset within the source message where this wrapped line starts. */
    public final int lineCharOffset;

    public RenderableLine(String text, boolean isSeparator, String time, String date,
                          ChatTabData.ChatMessage sourceMsg, int lineCharOffset) {
        this.text          = text;
        this.isSeparator   = isSeparator;
        this.time          = time;
        this.date          = date;
        this.sourceMsg     = sourceMsg;
        this.lineCharOffset = lineCharOffset;
    }
}
