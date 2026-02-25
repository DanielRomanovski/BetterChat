package com.betterchat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the content area of each chat window every frame.
 *
 * Owns:
 *  - Line cache  — wraps raw chat messages into RenderableLines and rebuilds
 *    automatically when new messages arrive or filters change.
 *  - Scroll bar  — day-aware: the thumb size and range reflect only the day
 *    currently in view, with ▲/▼ buttons to jump between days.
 *  - Hover/click target cache — maps screen rectangles to chat component events
 *    so ChatTabHandler can handle tooltips and clicks without re-scanning lines.
 *  - Hover tooltip — draws the floating tooltip for hovered chat components.
 */
public class ChatRenderer {

    private final ChatTabData data;

    // -------------------------------------------------------------------------
    // Line caches (keyed by global tab index)
    // -------------------------------------------------------------------------
    final Map<Integer, List<RenderableLine>> lineCache           = new HashMap<>();
    final Map<Integer, Integer>              lineCacheHistorySize   = new HashMap<>();
    final Map<Integer, Integer>              lineCacheFilterVersion = new HashMap<>();
    final Map<Integer, Integer>              lineCacheWidth         = new HashMap<>();

    // -------------------------------------------------------------------------
    // Hover / click target caches (keyed by window index)
    // -------------------------------------------------------------------------
    final Map<Integer, List<ChatTargets.HoverTarget>> hoverTargetCache     = new HashMap<>();
    final Map<Integer, List<ChatTargets.ClickTarget>> clickTargetCache     = new HashMap<>();
    final Map<Integer, Integer>                       targetCacheScrollOffset = new HashMap<>();
    final Map<Integer, Integer>                       targetCacheWindowX   = new HashMap<>();
    final Map<Integer, Integer>                       targetCacheWindowY   = new HashMap<>();
    final Map<Integer, Integer>                       targetCacheWindowH   = new HashMap<>();

    // Active targets rebuilt at the start of each draw call
    final List<ChatTargets.HoverTarget> activeHoverTargets = new ArrayList<>();
    final List<ChatTargets.ClickTarget> activeClickTargets = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Day-scroll state (keyed by window index)
    // Populated by renderScrollBar; read by ChatTabHandler for click/drag handling.
    // -------------------------------------------------------------------------
    final Map<Integer, Integer> dayScrollMin = new HashMap<>(); // lowest scroll offset in the current day
    final Map<Integer, Integer> dayScrollMax = new HashMap<>(); // highest scroll offset in the current day
    final Map<Integer, Integer> dayNavPrevY  = new HashMap<>(); // screen Y of the ▲ (older) button, -1 if absent
    final Map<Integer, Integer> dayNavNextY  = new HashMap<>(); // screen Y of the ▼ (newer) button, -1 if absent
    final Map<Integer, Integer> dayNavBarX   = new HashMap<>(); // screen X of the scroll bar

    // Cached tooltip state — rebuilt only when the hovered component changes
    private HoverEvent                    lastHoveredEvent   = null;
    private List<ChatTargets.TooltipLine> cachedTooltipLines = null;

    public ChatRenderer(ChatTabData data) {
        this.data = data;
    }

    // -------------------------------------------------------------------------
    // System font list (cached on first call)
    // -------------------------------------------------------------------------

    private static List<String> cachedSystemFonts = null;

    /** Returns a sorted list of all font family names installed on the system. */
    public static List<String> getSystemFonts() {
        if (cachedSystemFonts == null) {
            String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
            cachedSystemFonts = new ArrayList<>(Arrays.asList(families));
        }
        return cachedSystemFonts;
    }

    // -------------------------------------------------------------------------
    // Font drawing helpers
    // -------------------------------------------------------------------------

    /**
     * Draws a string using either the custom AWT font renderer or Minecraft's built-in
     * FontRenderer (with optional GL11 matrix scaling for font size).
     *
     * When data.fontEnabled is true and a valid fontName is set, strips MC colour codes
     * and delegates to AwtFontRenderer which uses a pre-baked atlas texture.
     * Otherwise uses Minecraft's FontRenderer, optionally scaled via GL11 matrix.
     */
    // MC colour palette: index 0-15 maps §0-§f
    private static final int[] MC_COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    /**
     * Draws a formatted MC string using the custom AWT font, honouring every
     * §-colour code by splitting the text into monochrome runs and drawing
     * each with its correct colour.  Falls back to MC's renderer if the AWT
     * renderer is unavailable.
     *
     * @param fontName   font family name (from data.fontName / fontNameTimestamps etc.)
     * @param text       formatted text (may contain §-codes)
     * @param x          left edge in screen pixels
     * @param y          top edge in screen pixels
     * @param baseColor  ARGB fallback colour used when no §-code is active
     * @param scale      font-size scale factor
     * @return x position after the last drawn character
     */
    private int drawAwtString(String fontName, String text, int x, int y,
                              int baseColor, float scale) {
        AwtFontRenderer renderer = AwtFontRenderer.get(fontName);
        if (renderer == null) return x;
        int targetH = Math.max(6, (int)(10 * scale));
        int alpha   = (baseColor >> 24) & 0xFF;
        if (alpha == 0) alpha = 0xFF;

        int curColor = baseColor; // tracks the currently active colour
        StringBuilder run = new StringBuilder();
        int cx = x;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\u00A7' && i + 1 < text.length()) {
                // Flush pending run before changing colour
                if (run.length() > 0) {
                    cx += renderer.drawString(run.toString(), cx, y, curColor, targetH);
                    run.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(i + 1));
                int idx = -1;
                if (code >= '0' && code <= '9') idx = code - '0';
                else if (code >= 'a' && code <= 'f') idx = 10 + (code - 'a');
                if (idx >= 0) {
                    curColor = (alpha << 24) | MC_COLORS[idx];
                } else if (code == 'r') {
                    curColor = baseColor; // §r resets to the base colour
                }
                i++; // skip the code character
            } else {
                run.append(ch);
            }
        }
        // Flush final run
        if (run.length() > 0) {
            cx += renderer.drawString(run.toString(), cx, y, curColor, targetH);
        }
        return cx;
    }

    private void drawScaledString(Minecraft mc, String text, int x, int y,
                                  int color, boolean shadow, float scale, boolean useAwtFont) {
        if (useAwtFont && data.fontEnabled && !data.fontName.isEmpty()) {
            String plain = EnumChatFormatting.getTextWithoutFormattingCodes(text);
            if (plain == null || plain.isEmpty()) return;
            if (drawAwtString(data.fontName, text, x, y, color, scale) > x) return;
            // Fall through to MC renderer if font couldn't be loaded
        }
        if (scale == 1.0f) {
            if (shadow) mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
            else        mc.fontRendererObj.drawString(text, x, y, color);
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(scale, scale, 1.0f);
        if (shadow) mc.fontRendererObj.drawStringWithShadow(text, 0, 0, color);
        else        mc.fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    /**
     * Draws a plain string using the timestamp font if enabled, otherwise MC's renderer.
     * Used for timestamps and date-divider labels.
     */
    public void drawTimestampString(Minecraft mc, String text, int x, int y, int color, float scale) {
        float tsScale = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTimestamps)) : scale;
        if (data.fontTimestampsEnabled && !data.fontNameTimestamps.isEmpty()) {
            String plain = EnumChatFormatting.getTextWithoutFormattingCodes(text);
            if (plain != null && !plain.isEmpty()) {
                int targetH = Math.max(6, (int)(10 * tsScale));
                AwtFontRenderer r = AwtFontRenderer.get(data.fontNameTimestamps);
                if (r != null) { r.drawString(plain, x, y, color, targetH); return; }
            }
        }
        if (tsScale == 1.0f) mc.fontRendererObj.drawString(text, x, y, color);
        else {
            GL11.glPushMatrix(); GL11.glTranslatef(x, y, 0); GL11.glScalef(tsScale, tsScale, 1f);
            mc.fontRendererObj.drawString(text, 0, 0, color);
            GL11.glPopMatrix();
        }
    }

    /**
     * Draws a tab-label string using the tab font if enabled, otherwise MC's renderer.
     */
    public void drawTabString(Minecraft mc, String text, int x, int y, int color) {
        float tabScale = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTabs)) : 1.0f;
        if (data.fontTabsEnabled && !data.fontNameTabs.isEmpty()) {
            AwtFontRenderer r = AwtFontRenderer.get(data.fontNameTabs);
            if (r != null) {
                int targetH = Math.max(6, (int)(10 * tabScale));
                r.drawString(text, x, y, color, targetH);
                return;
            }
        }
        if (tabScale == 1.0f) mc.fontRendererObj.drawString(text, x, y, color);
        else {
            GL11.glPushMatrix(); GL11.glTranslatef(x, y, 0); GL11.glScalef(tabScale, tabScale, 1f);
            mc.fontRendererObj.drawString(text, 0, 0, color);
            GL11.glPopMatrix();
        }
    }

    // -------------------------------------------------------------------------
    // Render window content
    // -------------------------------------------------------------------------

    /**
     * Draws the message area for one window.
     * Rebuilds the line cache if the message log or filters have changed.
     * Handles scroll-wheel input and draws the scroll bar when content overflows.
     *
     * @param isHUD  pass true when rendering the HUD fade overlay — disables
     *               the scroll bar and the hover/click target cache.
     */
    public void renderWindowContent(Minecraft mc, ChatTabData.ChatWindowInstance win,
                                    int globalAlpha, boolean isHUD) {
        int globalIdx = win.getSelectedGlobalIndex();
        if (globalIdx == -1) return;

        int wrapWidth = win.width - 15;
        // Reserve space for timestamps. The reserve is in MC-pixel units (scale-1.0 space).
        // The text wrap also runs at scale 1.0, so we must divide by scaleText to get
        // how many MC-pixels of wrap width are available at the rendered text size.
        float scaleTextEarly = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSize)) : 1.0f;
        if (data.showTimeStamps) {
            float tsScaleEarly = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTimestamps)) : 1.0f;
            float reserve = mc.fontRendererObj.getStringWidth("00:00") * tsScaleEarly
                    * (data.fontTimestampsEnabled && !data.fontNameTimestamps.isEmpty() ? 1.5f : 1.0f);
            wrapWidth = Math.max(20, wrapWidth - (int) reserve - 6);
        }
        // Divide by text scale so MC's word-wrapper (which operates at scale 1.0)
        // produces lines that fit within the scaled width.
        int wrapWidthMC = Math.max(10, (int)(wrapWidth / scaleTextEarly));
        int winIdx = data.windows.indexOf(win);

        // Rebuild line cache if stale
        boolean stale = !lineCacheWidth.containsKey(globalIdx)
                || lineCacheWidth.get(globalIdx) != wrapWidthMC
                || lineCacheHistorySize.getOrDefault(globalIdx, -1) != data.globalLog.size()
                || lineCacheFilterVersion.getOrDefault(globalIdx, -1) != data.filterVersion
                || !lineCache.containsKey(globalIdx);

        if (stale) {
            List<ChatTabData.ChatMessage> history = data.buildFilteredHistory(globalIdx);

            java.util.Set<Integer> skipIdx = new java.util.HashSet<>();
            if (data.messageCombining) {
                java.util.Map<Integer, Integer> lastGroupPos = new java.util.HashMap<>();
                for (int hi = 0; hi < history.size(); hi++) {
                    ChatTabData.ChatMessage m = history.get(hi);
                    if (!m.isDateSeparator && m.groupId != 0)
                        lastGroupPos.put(m.groupId, hi);
                }
                for (int hi = 0; hi < history.size(); hi++) {
                    ChatTabData.ChatMessage m = history.get(hi);
                    if (!m.isDateSeparator && m.groupId != 0 && hi != lastGroupPos.get(m.groupId))
                        skipIdx.add(hi);
                }
            }

            List<RenderableLine> built = new ArrayList<>();
            for (int hi = 0; hi < history.size(); hi++) {
                if (skipIdx.contains(hi)) continue;
                ChatTabData.ChatMessage msg = history.get(hi);
                if (msg.isDateSeparator) {
                    built.add(new RenderableLine(msg.text, true, msg.time, msg.date, msg, 0));
                } else {
                    String displayText = data.applyBracketStrip(msg.text);
                    if (data.messageCombining && msg.groupId != 0 && msg.repeatCount > 1) {
                        displayText = displayText + " \u00A77<x" + msg.repeatCount + ">";
                    }
                    List<String> wrapped = mc.fontRendererObj.listFormattedStringToWidth(displayText, wrapWidthMC);
                    int charOffset = 0;
                    for (int j = 0; j < wrapped.size(); j++) {
                        built.add(new RenderableLine(wrapped.get(j), false,
                                j == 0 ? msg.time : "", msg.date, msg, charOffset));
                        charOffset += EnumChatFormatting
                                .getTextWithoutFormattingCodes(wrapped.get(j)).length();
                    }
                }
            }
            lineCache.put(globalIdx, built);
            lineCacheHistorySize.put(globalIdx, data.globalLog.size());
            lineCacheFilterVersion.put(globalIdx, data.filterVersion);
            lineCacheWidth.put(globalIdx, wrapWidthMC);
            hoverTargetCache.remove(winIdx);
            clickTargetCache.remove(winIdx);
            targetCacheScrollOffset.put(winIdx, Integer.MIN_VALUE);
        }

        List<RenderableLine> allLines = lineCache.get(globalIdx);
        if (allLines == null || allLines.isEmpty()) return;

        // Per-category font scales
        float scaleText = scaleTextEarly; // already computed above
        float scaleTime = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTimestamps)) : 1.0f;
        float scaleTab  = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTabs))       : 1.0f;

        // Tab bar height scales with the tab font
        int tabBarH  = Math.max(16, (int)(22 * scaleTab));
        // Line height is driven by the text scale
        int lineH    = Math.max(6, (int)(10 * scaleText));
        // Usable content area: below the tab bar to above the input area (8px padding)
        int contentH = win.height - tabBarH - 8;
        int maxLines = Math.max(1, contentH / lineH);
        int totalLines    = allLines.size();
        int currentOffset = data.scrollOffsets.getOrDefault(globalIdx, 0);

        // Handle scroll wheel input
        if (!isHUD && Mouse.hasWheel()) {
            Minecraft mcInst = Minecraft.getMinecraft();
            int rawMx = Mouse.getX() * (mcInst.currentScreen != null ? mcInst.currentScreen.width  : 1) / mcInst.displayWidth;
            int rawMy = (mcInst.currentScreen != null ? mcInst.currentScreen.height : 1)
                    - Mouse.getY() * (mcInst.currentScreen != null ? mcInst.currentScreen.height : 1)
                    / mcInst.displayHeight - 1;
            if (rawMx >= win.x && rawMx <= win.x + win.width
                    && rawMy >= win.y && rawMy <= win.y + win.height) {
                int wheel = Mouse.getDWheel();
                if (wheel != 0) {
                    int newOffset = Math.max(0, Math.min(
                            Math.max(0, totalLines - maxLines),
                            currentOffset + (wheel > 0 ? 1 : -1)));
                    if (newOffset != currentOffset) {
                        currentOffset = newOffset;
                        data.scrollOffsets.put(globalIdx, currentOffset);
                        targetCacheScrollOffset.put(winIdx, Integer.MIN_VALUE);
                    }
                }
            }
        }

        int end   = Math.max(0, totalLines - currentOffset);
        int start = Math.max(0, end - maxLines);
        // Bottom line: its top edge sits lineH+8 px above the window bottom (input bar gap).
        int baseY = win.y + win.height - lineH - 8;
        int y     = baseY;

        // Draw visible lines bottom-to-top
        for (int i = end - 1; i >= start; i--) {
            RenderableLine line = allLines.get(i);
            if (line.isSeparator) {
                int tw = mc.fontRendererObj.getStringWidth(line.text);
                int cx = win.x + win.width / 2;
                // Reset GL color modulator before rects — FontRenderer can leave it dirty.
                GL11.glColor4f(1f, 1f, 1f, 1f);
                Gui.drawRect(win.x + 5, y + 4, cx - tw / 2 - 5, y + 5, 0x22FFFFFF);
                Gui.drawRect(cx + tw / 2 + 5, y + 4, win.x + win.width - 5, y + 5, 0x22FFFFFF);
                int sepColor = 0xAAAAAA | (globalAlpha << 24);
                drawTimestampString(mc, line.text, cx - tw / 2, y, sepColor, scaleTime);
            } else {
                int textColor = data.getHex(data.colorText, (int)(data.opacText / 255.0 * globalAlpha));
                drawScaledString(mc, line.text, win.x + 5, y, textColor, true, scaleText,
                        data.fontEnabled && !data.fontName.isEmpty());
                if (data.showTimeStamps && !line.time.isEmpty() && !isHUD) {
                    int timeW = (int)(mc.fontRendererObj.getStringWidth(line.time) * scaleTime);
                    int timeColor = data.getHex(data.colorTime, (int)(data.opacTime / 255.0 * globalAlpha));
                    drawTimestampString(mc, line.time, win.x + win.width - timeW - 8, y,
                            timeColor, scaleTime);
                }
            }
            y -= lineH;
        }

        if (!isHUD && totalLines > maxLines) {
            renderScrollBar(win, totalLines, maxLines, currentOffset,
                    data.getHex(data.colorSelection, 255));
        }

        // Rebuild hover/click target caches if the scroll position or window bounds changed
        if (!isHUD && winIdx != -1) {
            int cso = targetCacheScrollOffset.getOrDefault(winIdx, Integer.MIN_VALUE);
            int cwx = targetCacheWindowX.getOrDefault(winIdx, Integer.MIN_VALUE);
            int cwy = targetCacheWindowY.getOrDefault(winIdx, Integer.MIN_VALUE);
            int cwh = targetCacheWindowH.getOrDefault(winIdx, Integer.MIN_VALUE);
            if (!hoverTargetCache.containsKey(winIdx)
                    || cso != currentOffset || cwx != win.x || cwy != win.y || cwh != win.height) {
                List<ChatTargets.HoverTarget> nh = new ArrayList<>();
                List<ChatTargets.ClickTarget> nc = new ArrayList<>();
                y = baseY;
                for (int i = end - 1; i >= start; i--) {
                    RenderableLine line = allLines.get(i);
                    if (!line.isSeparator && line.sourceMsg != null
                            && line.sourceMsg.rawComponent != null) {
                        buildTargetsForLine(mc, line, y, win.x, nh, nc);
                    }
                    y -= lineH;
                }
                hoverTargetCache.put(winIdx, nh);
                clickTargetCache.put(winIdx, nc);
                targetCacheScrollOffset.put(winIdx, currentOffset);
                targetCacheWindowX.put(winIdx, win.x);
                targetCacheWindowY.put(winIdx, win.y);
                targetCacheWindowH.put(winIdx, win.height);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scroll bar
    // -------------------------------------------------------------------------

    /**
     * Draws the scroll bar on the right edge of the window.
     * The thumb is sized and positioned relative to the current day's content only —
     * not the entire history — so it stays usable even with thousands of messages.
     * ▲/▼ buttons appear above/below the track when there are adjacent days to jump to.
     */
    private void renderScrollBar(ChatTabData.ChatWindowInstance win,
                                 int total, int visible, int offset, int color) {
        int globalIdx = win.getSelectedGlobalIndex();
        List<RenderableLine> allLines = (globalIdx != -1) ? lineCache.get(globalIdx) : null;
        if (allLines == null || allLines.isEmpty()) return;

        int winIdx   = data.windows.indexOf(win);
        int barX     = win.x + win.width - 4;
        // Use the same dynamic tab bar height as renderWindowContent
        float scaleTab2 = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTabs)) : 1.0f;
        int tabBarH2 = Math.max(16, (int)(22 * scaleTab2));
        int barAreaY = win.y + tabBarH2 + 3;
        int barAreaH = win.height - tabBarH2 - 13;
        if (barAreaH <= 0) return;

        // Build a list of date segments. Each entry is [firstLineIdx, lastLineIdx, contentLineCount].
        // allLines is ordered oldest-first (index 0 = oldest). offset 0 = scrolled to the bottom.
        List<int[]> segs = new ArrayList<>();
        int si = -1;
        for (int i = 0; i < allLines.size(); i++) {
            if (allLines.get(i).isSeparator) {
                si = i;
            } else {
                if (si >= 0) {
                    segs.add(new int[]{si, i, 0});
                    si = -1;
                } else if (segs.isEmpty()) {
                    segs.add(new int[]{0, i, 0});
                }
                segs.get(segs.size() - 1)[1] = i;
                segs.get(segs.size() - 1)[2]++;
            }
        }

        if (segs.isEmpty()) {
            // Fallback: plain scroll bar
            Gui.drawRect(barX, barAreaY, barX + 2, barAreaY + barAreaH, 0x22FFFFFF);
            int thumbH = Math.max(10, (int)(barAreaH * ((double)visible / total)));
            int thumbY = barAreaY + (barAreaH - thumbH)
                    - (int)((barAreaH - thumbH) * ((double)offset / Math.max(1, total - visible)));
            Gui.drawRect(barX, thumbY, barX + 2, thumbY + thumbH, color);
            dayScrollMin.put(winIdx, 0);
            dayScrollMax.put(winIdx, Math.max(0, total - visible));
            dayNavPrevY.put(winIdx, -1);
            dayNavNextY.put(winIdx, -1);
            dayNavBarX.put(winIdx, barX);
            return;
        }

        // Find which date segment the bottom of the current viewport sits in
        int bottomIdx = Math.max(0, total - 1 - offset);
        int curSegIdx = segs.size() - 1;
        for (int s = 0; s < segs.size(); s++) {
            if (bottomIdx <= segs.get(s)[1]) { curSegIdx = s; break; }
        }
        int[] cur      = segs.get(curSegIdx);
        int   dayFirst = cur[0];
        int   dayLast  = cur[1];
        int   dayCount = cur[2];

        // Compute the scroll offset range that keeps the viewport inside this day
        int offsetAtDayBottom = Math.max(0, total - 1 - dayLast);
        int offsetAtDayTop    = Math.min(total - visible, total - 1 - dayFirst);
        if (offsetAtDayTop < offsetAtDayBottom) offsetAtDayTop = offsetAtDayBottom;

        dayScrollMin.put(winIdx, offsetAtDayBottom);
        dayScrollMax.put(winIdx, offsetAtDayTop);
        dayNavBarX.put(winIdx, barX);

        // ── Nav buttons ───────────────────────────────────────────────────────
        boolean hasPrev = curSegIdx > 0;
        boolean hasNext = curSegIdx < segs.size() - 1;

        int trackTop    = barAreaY + (hasPrev ? 11 : 0);
        int trackBottom = barAreaY + barAreaH - (hasNext ? 11 : 0);
        int trackH      = Math.max(1, trackBottom - trackTop);

        Gui.drawRect(barX, trackTop, barX + 2, trackBottom, 0x22FFFFFF);

        Minecraft mc = Minecraft.getMinecraft();

        if (hasPrev) {
            int btnY     = barAreaY;
            boolean onToday = !hasNext;
            drawTimestampString(mc, "\u25B2", barX - 1, btnY, 0xAAFFFFFF, 1.0f);
            if (!onToday) {
                int[]  prevSeg  = segs.get(curSegIdx - 1);
                String prevDate = allLines.get(prevSeg[0]).isSeparator
                        ? allLines.get(prevSeg[0]).text : "";
                if (!prevDate.isEmpty()) {
                    String label = prevDate.length() > 5 ? prevDate.substring(5) : prevDate;
                    drawTimestampString(mc, label,
                            barX - mc.fontRendererObj.getStringWidth(label) - 3, btnY, 0x77FFFFFF, 1.0f);
                }
            }
            dayNavPrevY.put(winIdx, btnY);
        } else {
            dayNavPrevY.put(winIdx, -1);
        }

        if (hasNext) {
            int    btnY    = trackBottom + 1;
            int[]  nextSeg = segs.get(curSegIdx + 1);
            String nextDate = allLines.get(nextSeg[0]).isSeparator
                    ? allLines.get(nextSeg[0]).text : "";
            drawTimestampString(mc, "\u25BC", barX - 1, btnY, 0xAAFFFFFF, 1.0f);
            if (!nextDate.isEmpty()) {
                String label = nextDate.length() > 5 ? nextDate.substring(5) : nextDate;
                drawTimestampString(mc, label,
                        barX - mc.fontRendererObj.getStringWidth(label) - 3, btnY, 0x77FFFFFF, 1.0f);
            }
            dayNavNextY.put(winIdx, btnY);
        } else {
            dayNavNextY.put(winIdx, -1);
        }

        // ── Thumb ─────────────────────────────────────────────────────────────
        int    scrollRangeInDay = Math.max(1, offsetAtDayTop - offsetAtDayBottom);
        double dayFraction      = (offsetAtDayTop == offsetAtDayBottom) ? 0.0
                : (double)(offset - offsetAtDayBottom) / scrollRangeInDay;
        dayFraction = Math.max(0, Math.min(1, dayFraction));

        int thumbH = Math.max(8, (int)(trackH * ((double)visible / Math.max(1, dayCount))));
        thumbH     = Math.min(thumbH, trackH);
        int thumbY = trackTop + (int)((1.0 - dayFraction) * (trackH - thumbH));

        Gui.drawRect(barX, thumbY, barX + 2, thumbY + thumbH, color);
    }

    // -------------------------------------------------------------------------
    // Hover tooltip
    // -------------------------------------------------------------------------

    /** Draws the floating tooltip for whichever chat component the cursor is over. */
    public void drawHoverTooltip(int mx, int my) {
        HoverEvent activeEvent = null;
        for (ChatTargets.HoverTarget t : activeHoverTargets) {
            if (mx >= t.x1 && mx <= t.x2 && my >= t.y1 && my <= t.y2) {
                activeEvent = t.hoverEvent;
                break;
            }
        }
        if (activeEvent == null) { lastHoveredEvent = null; return; }

        Minecraft mc = Minecraft.getMinecraft();
        if (activeEvent != lastHoveredEvent) {
            lastHoveredEvent   = activeEvent;
            cachedTooltipLines = buildTooltipLines(mc, activeEvent);
        }
        List<ChatTargets.TooltipLine> lines = cachedTooltipLines;
        if (lines == null || lines.isEmpty()) return;

        final int PAD = 5, LINE_H = 10;
        int maxW = 0;
        for (ChatTargets.TooltipLine tl : lines) {
            int w = mc.fontRendererObj.getStringWidth(tl.text);
            if (w > maxW) maxW = w;
        }
        int tipW = maxW + PAD * 2;
        int tipH = lines.size() * LINE_H + PAD * 2 - 1;

        ScaledResolution sr = new ScaledResolution(mc);
        int tipX = mx + 8, tipY = my - tipH / 2;
        if (tipX + tipW > sr.getScaledWidth()  - 2) tipX = mx - tipW - 6;
        if (tipY + tipH > sr.getScaledHeight() - 2) tipY = sr.getScaledHeight() - tipH - 2;
        if (tipY < 2) tipY = 2;

        Gui.drawRect(tipX - 1, tipY - 1, tipX + tipW + 1, tipY + tipH + 1, 0xCC111116);
        Gui.drawRect(tipX, tipY, tipX + tipW, tipY + tipH, 0xEE0D0D12);
        Gui.drawRect(tipX, tipY, tipX + 1, tipY + tipH, data.getHex(data.colorSelection, 200));

        int ty = tipY + PAD;
        for (ChatTargets.TooltipLine tl : lines) {
            mc.fontRendererObj.drawStringWithShadow(tl.text, tipX + PAD + 1, ty, tl.color);
            ty += LINE_H;
        }
    }

    // -------------------------------------------------------------------------
    // Target building
    // -------------------------------------------------------------------------

    /**
     * Walks the component tree of one rendered line and records hover/click
     * rectangles for any node that carries a chat event.
     * Character offsets are used to correctly align targets when a message is
     * split across multiple wrapped lines.
     */
    private void buildTargetsForLine(Minecraft mc, RenderableLine line, int lineY,
                                     int windowOffsetX,
                                     List<ChatTargets.HoverTarget> outHover,
                                     List<ChatTargets.ClickTarget> outClick) {
        IChatComponent root = line.sourceMsg.rawComponent;
        String lineUnformatted = EnumChatFormatting.getTextWithoutFormattingCodes(line.text);
        if (lineUnformatted == null || lineUnformatted.isEmpty()) return;

        int lineStartChar = line.lineCharOffset;
        int curChar = 0, curX = windowOffsetX + 5;

        for (IChatComponent comp : root) {
            String nodeText = comp.getUnformattedTextForChat();
            if (nodeText.isEmpty()) continue;
            int nodeLen       = nodeText.length();
            int nodeEnd       = curChar + nodeLen;
            int overlapStart  = Math.max(curChar, lineStartChar);
            int overlapEnd    = Math.min(nodeEnd, lineStartChar + lineUnformatted.length());

            if (overlapStart < overlapEnd) {
                String beforeOnLine = nodeText.substring(
                        Math.max(0, lineStartChar - curChar), overlapStart - curChar);
                String onLine = nodeText.substring(
                        overlapStart - curChar, overlapEnd - curChar);
                int preW = mc.fontRendererObj.getStringWidth(applyStyle(beforeOnLine, comp));
                int onW  = mc.fontRendererObj.getStringWidth(applyStyle(onLine, comp));

                HoverEvent he = comp.getChatStyle().getChatHoverEvent();
                if (he != null && onW > 0) {
                    int tx = curX + preW;
                    outHover.add(new ChatTargets.HoverTarget(tx, lineY - 1, tx + onW, lineY + 9, he));
                }
                ClickEvent ce = comp.getChatStyle().getChatClickEvent();
                if (ce != null && onW > 0) {
                    int tx = curX + preW;
                    outClick.add(new ChatTargets.ClickTarget(tx, lineY - 1, tx + onW, lineY + 9, ce));
                }
            }

            if (curChar >= lineStartChar) {
                curX += mc.fontRendererObj.getStringWidth(applyStyle(nodeText, comp));
            } else if (nodeEnd > lineStartChar) {
                String tail = nodeText.substring(lineStartChar - curChar);
                curX += mc.fontRendererObj.getStringWidth(applyStyle(tail, comp));
            }
            curChar += nodeLen;
        }
    }

    private String applyStyle(String text, IChatComponent comp) {
        if (text.isEmpty()) return text;
        net.minecraft.util.ChatStyle style = comp.getChatStyle();
        StringBuilder prefix = new StringBuilder();
        if (style.getColor() != null)               prefix.append(style.getColor().toString());
        if (style.getBold())                         prefix.append(EnumChatFormatting.BOLD);
        if (style.getItalic())                       prefix.append(EnumChatFormatting.ITALIC);
        if (style.getUnderlined())                   prefix.append(EnumChatFormatting.UNDERLINE);
        if (style.getStrikethrough())                prefix.append(EnumChatFormatting.STRIKETHROUGH);
        if (style.getObfuscated())                   prefix.append(EnumChatFormatting.OBFUSCATED);
        return prefix + text;
    }

    // -------------------------------------------------------------------------
    // Tooltip content
    // -------------------------------------------------------------------------

    /** Builds the list of lines to show inside a tooltip for a given hover event. */
    private List<ChatTargets.TooltipLine> buildTooltipLines(Minecraft mc, HoverEvent event) {
        List<ChatTargets.TooltipLine> lines = new ArrayList<>();
        HoverEvent.Action  action = event.getAction();
        IChatComponent     value  = event.getValue();
        if (value == null) return lines;

        if (action == HoverEvent.Action.SHOW_TEXT) {
            String full = value.getFormattedText();
            for (String part : full.split("\n")) {
                if (!part.isEmpty()) lines.add(new ChatTargets.TooltipLine(part, getColorFromFormatted(part)));
            }
            if (lines.isEmpty() && !full.isEmpty())
                lines.add(new ChatTargets.TooltipLine(full, 0xFFFFFF));

        } else if (action == HoverEvent.Action.SHOW_ACHIEVEMENT) {
            String id = value.getUnformattedText();
            net.minecraft.stats.Achievement ach = null;
            for (Object obj : net.minecraft.stats.AchievementList.achievementList) {
                if (obj instanceof net.minecraft.stats.Achievement) {
                    net.minecraft.stats.Achievement a = (net.minecraft.stats.Achievement) obj;
                    if (a.statId.equals(id) || a.getStatName().getUnformattedText().equals(id)) {
                        ach = a; break;
                    }
                }
            }
            if (ach != null) {
                lines.add(new ChatTargets.TooltipLine("\u2605 " + ach.getStatName().getFormattedText(), 0xFFFF55));
                String desc = ach.getDescription();
                if (desc != null && !desc.isEmpty()) {
                    for (String w : mc.fontRendererObj.listFormattedStringToWidth(desc, 160))
                        lines.add(new ChatTargets.TooltipLine(w, 0xAAAAAA));
                }
            } else {
                lines.add(new ChatTargets.TooltipLine("\u2605 " + value.getFormattedText(), 0xFFFF55));
            }

        } else if (action == HoverEvent.Action.SHOW_ITEM) {
            String raw = value.getUnformattedText();
            lines.add(new ChatTargets.TooltipLine("[Item]", 0x55FF55));
            try {
                net.minecraft.item.ItemStack stack = net.minecraft.item.ItemStack.loadItemStackFromNBT(
                        net.minecraft.nbt.JsonToNBT.getTagFromJson(raw));
                if (stack != null) {
                    lines.set(0, new ChatTargets.TooltipLine(stack.getDisplayName(), 0x55FF55));
                    lines.add(new ChatTargets.TooltipLine(
                            EnumChatFormatting.DARK_GRAY + stack.getItem().getUnlocalizedName(), 0x555555));
                }
            } catch (Exception ignored) {
                lines.add(new ChatTargets.TooltipLine(
                        raw.length() > 40 ? raw.substring(0, 40) + "\u2026" : raw, 0xAAAAAA));
            }

        } else if (action == HoverEvent.Action.SHOW_ENTITY) {
            String raw = value.getUnformattedText();
            lines.add(new ChatTargets.TooltipLine("[Entity]", 0xFF5555));
            lines.add(new ChatTargets.TooltipLine(raw.length() > 40 ? raw.substring(0, 40) + "\u2026" : raw, 0xAAAAAA));

        } else {
            String raw = value.getFormattedText();
            lines.add(new ChatTargets.TooltipLine(raw.length() > 60 ? raw.substring(0, 60) + "\u2026" : raw, 0xFFFFFF));
        }
        return lines;
    }

    private int getColorFromFormatted(String formatted) {
        int[] mcColors = {
            0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,
            0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF
        };
        for (int i = 0; i < formatted.length() - 1; i++) {
            if (formatted.charAt(i) == '\u00A7') {
                char c = Character.toLowerCase(formatted.charAt(i + 1));
                if (c >= '0' && c <= '9') return 0xFF000000 | mcColors[c - '0'];
                if (c >= 'a' && c <= 'f') return 0xFF000000 | mcColors[10 + (c - 'a')];
            }
        }
        return 0xFFFFFF;
    }
}

