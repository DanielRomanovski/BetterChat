package com.betterchat;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;

/**
 * Small hit-test data classes used to track interactive regions inside chat windows.
 * Each frame the renderer populates lists of these so that mouse events can check
 * whether the cursor is over a hoverable or clickable chat component.
 */
public class ChatTargets {

    /** A screen rectangle that triggers a tooltip when the mouse hovers over it. */
    public static class HoverTarget {
        public final int x1, y1, x2, y2;
        public final HoverEvent hoverEvent;

        public HoverTarget(int x1, int y1, int x2, int y2, HoverEvent hoverEvent) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.hoverEvent = hoverEvent;
        }
    }

    /** A screen rectangle that fires a chat click-event when left-clicked. */
    public static class ClickTarget {
        public final int x1, y1, x2, y2;
        public final ClickEvent clickEvent;

        public ClickTarget(int x1, int y1, int x2, int y2, ClickEvent clickEvent) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.clickEvent = clickEvent;
        }
    }

    /** One line of text inside a hover tooltip, with its own colour. */
    public static class TooltipLine {
        public final String text;
        public final int color;

        public TooltipLine(String text, int color) {
            this.text  = text;
            this.color = color;
        }
    }
}
