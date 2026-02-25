package com.betterchat.settings;

import com.betterchat.AwtFontRenderer;
import com.betterchat.ChatTabData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.List;

import static com.betterchat.settings.ColorUtils.*;
import static com.betterchat.settings.SettingsConstants.*;

/**
 * Renders the Appearance settings page and handles its mouse clicks.
 *
 * Covers: colour swatches, option toggles (hide chat, save log, lock position,
 * timestamps, message combining, strip brackets), and the custom-fonts section
 * (master toggle + per-target sub-cards with size slider and font picker).
 */
public class AppearancePage {

    // ── External state (shared with ChatSettingsGui) ──────────────────────────
    private final ChatTabData data;

    // Font dropdown state — text
    public boolean fontDropdownOpen      = false;
    public int     fontDropdownScroll    = 0;
    public GuiTextField fontSearchField;
    public String  fontSearchText        = "";

    // Font dropdown state — tabs
    public boolean fontTabsDropdownOpen  = false;
    public int     fontTabsDropdownScroll = 0;
    public GuiTextField fontTabsSearchField;
    public String  fontTabsSearchText    = "";

    // Font dropdown state — timestamps
    public boolean fontTimeDropdownOpen  = false;
    public int     fontTimeDropdownScroll = 0;
    public GuiTextField fontTimeSearchField;
    public String  fontTimeSearchText    = "";

    // Font-size slider dragging state
    public boolean draggingFontSize  = false;
    public int     draggingFontSlot  = -1; // 0=text, 1=tabs, 2=timestamps

    // Scrollbar for the settings content area
    public int     settingsScrollY       = 0;
    public boolean draggingSettingsBar   = false;

    // Hover animation state
    private final float[] colorHover = new float[9];
    private final float[] cbHover    = new float[13]; // option toggle rows
    public        float   resetHover  = 0f;

    // ── Colour-picker open callback (calls back to ChatSettingsGui) ───────────
    public interface ColorSwatchClickListener {
        void onSwatchClicked(int colorIndex);
    }
    private final ColorSwatchClickListener swatchListener;

    // ─────────────────────────────────────────────────────────────────────────

    public AppearancePage(ChatTabData data, ColorSwatchClickListener swatchListener) {
        this.data          = data;
        this.swatchListener = swatchListener;

        Minecraft mc = Minecraft.getMinecraft();
        fontSearchField = new GuiTextField(10, mc.fontRendererObj, 0, 0, 100, 10);
        fontSearchField.setMaxStringLength(64);
        fontTabsSearchField = new GuiTextField(11, mc.fontRendererObj, 0, 0, 100, 10);
        fontTabsSearchField.setMaxStringLength(64);
        fontTimeSearchField = new GuiTextField(12, mc.fontRendererObj, 0, 0, 100, 10);
        fontTimeSearchField.setMaxStringLength(64);
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    public void draw(Minecraft mc, int cx, int cy, int mx, int my) {
        int contentAreaTop    = cy;
        int contentAreaHeight = H - (cy - (new ScaledResolution(mc).getScaledHeight() - H) / 2) - 4;
        int scrollBarX        = cx + CW + 2;
        int scrollBarW        = 5;

        int totalContentH = measureContent(mc);
        int maxScroll     = Math.max(0, totalContentH - contentAreaHeight);
        settingsScrollY   = Math.max(0, Math.min(maxScroll, settingsScrollY));

        // Mouse-wheel scroll
        if (Mouse.hasWheel()) {
            ScaledResolution sr2 = new ScaledResolution(mc);
            int panelX = (sr2.getScaledWidth() - W) / 2;
            if (mx >= panelX + SW && mx <= panelX + W - scrollBarW - 2
                    && my >= (sr2.getScaledHeight() - H) / 2
                    && my <= (sr2.getScaledHeight() - H) / 2 + H) {
                int wheel = Mouse.getDWheel();
                if (wheel != 0)
                    settingsScrollY = Math.max(0, Math.min(maxScroll,
                            settingsScrollY + (wheel > 0 ? -16 : 16)));
            }
        }

        // Scrollbar
        if (maxScroll > 0) {
            int barH   = Math.max(20, contentAreaHeight * contentAreaHeight / Math.max(1, totalContentH));
            boolean barHov = mx >= scrollBarX && mx <= scrollBarX + scrollBarW
                    && my >= contentAreaTop && my <= contentAreaTop + contentAreaHeight;
            if (Mouse.isButtonDown(0) && (draggingSettingsBar || barHov)) {
                draggingSettingsBar = true;
                float frac = Math.max(0f, Math.min(1f,
                        (float)(my - contentAreaTop - barH / 2) / (contentAreaHeight - barH)));
                settingsScrollY = (int)(frac * maxScroll);
            } else if (!Mouse.isButtonDown(0)) {
                draggingSettingsBar = false;
            }
            Gui.drawRect(scrollBarX, contentAreaTop,
                    scrollBarX + scrollBarW, contentAreaTop + contentAreaHeight, 0x22FFFFFF);
            int barY2 = contentAreaTop + (maxScroll == 0 ? 0 :
                    (int)((long)(contentAreaHeight - barH) * settingsScrollY / maxScroll));
            Gui.drawRect(scrollBarX, barY2, scrollBarX + scrollBarW, barY2 + barH,
                    draggingSettingsBar || barHov ? C_ACCENT : C_ACCENT2);
        }

        // GL scissor clip
        ScaledResolution sr = new ScaledResolution(mc);
        int scale    = sr.getScaleFactor();
        int scissorX = (cx - 2) * scale;
        int scissorY = sr.getScaledHeight() * scale - (contentAreaTop + contentAreaHeight) * scale;
        int scissorW = (CW + 2) * scale;
        int scissorH = contentAreaHeight * scale;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        drawContent(mc, cx, cy - settingsScrollY, mx, my,
                contentAreaTop, contentAreaTop + contentAreaHeight);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Font-dropdown scroll via mouse wheel
        if (Mouse.hasWheel()) {
            int wheel = Mouse.getDWheel();
            if (wheel != 0) {
                List<String> allFonts = com.betterchat.ChatRenderer.getSystemFonts();
                if (fontDropdownOpen && data.fontEnabled) {
                    int ms = Math.max(0, allFonts.size() - FONT_DROPDOWN_VISIBLE);
                    fontDropdownScroll = Math.max(0, Math.min(ms, fontDropdownScroll + (wheel > 0 ? -1 : 1)));
                }
                if (fontTabsDropdownOpen && data.fontTabsEnabled) {
                    int ms = Math.max(0, allFonts.size() - FONT_DROPDOWN_VISIBLE);
                    fontTabsDropdownScroll = Math.max(0, Math.min(ms, fontTabsDropdownScroll + (wheel > 0 ? -1 : 1)));
                }
                if (fontTimeDropdownOpen && data.fontTimestampsEnabled) {
                    int ms = Math.max(0, allFonts.size() - FONT_DROPDOWN_VISIBLE);
                    fontTimeDropdownScroll = Math.max(0, Math.min(ms, fontTimeDropdownScroll + (wheel > 0 ? -1 : 1)));
                }
            }
        }
    }

    // ── Content measurement ───────────────────────────────────────────────────

    private int measureContent(Minecraft mc) {
        int h = 0;
        h += 13 + 9 * 20 + 5;  // Colors header + 9 rows + gap
        h += 13 + 4 * 16 + 5;  // Options header + 4 toggles + gap
        h += 13 + 16;           // Chat Display header + combine toggle
        h += 16;                // stripPlayerBrackets toggle
        h += 16;                // Master custom fonts toggle
        if (data.fontSizeEnabled) {
            h += fontSubCardHeight(data.fontEnabled,           fontDropdownOpen)
               + fontSubCardHeight(data.fontTabsEnabled,       fontTabsDropdownOpen)
               + fontSubCardHeight(data.fontTimestampsEnabled, fontTimeDropdownOpen)
               + 4 + 3;
        }
        h += 5 + 16 + 8;        // gap + reset button + bottom padding
        return h;
    }

    // ── Content drawing ───────────────────────────────────────────────────────

    private void drawContent(Minecraft mc, int cx, int cy, int mx, int my,
                             int clipTop, int clipBottom) {
        drawSectionHeader(mc, cx, cy, "Colors");
        cy += 13;
        for (int i = 0; i < 9; i++) {
            boolean hov = mx >= cx && mx <= cx + CW && my >= cy && my <= cy + 18
                    && my >= clipTop && my <= clipBottom;
            colorHover[i] = lerp(colorHover[i], hov ? 1f : 0f, 0.3f);
            if (cy + 18 > clipTop && cy < clipBottom)
                drawColorRow(mc, i, cx, cy, mx, my, clipTop, clipBottom);
            cy += 20;
        }

        cy += 5;
        drawSectionHeader(mc, cx, cy, "Options");
        cy += 13;

        String[]  labels = {"Hide Default Chat","Save Chat History","Lock Chat Position","Show Timestamps"};
        boolean[] values = {data.hideDefaultChat, data.saveChatLog, data.isLocked, data.showTimeStamps};
        for (int i = 0; i < 4; i++) {
            boolean hov = mx >= cx && mx <= cx + CW && my >= cy && my <= cy + 14
                    && my >= clipTop && my <= clipBottom;
            cbHover[i] = lerp(cbHover[i], hov ? 1f : 0f, 0.3f);
            if (cy + 14 > clipTop && cy < clipBottom) {
                Gui.drawRect(cx, cy, cx + CW, cy + 14, blendColor(C_CARD, C_CARD_H, cbHover[i]));
                drawTogglePill(cx + CW - 22, cy + 3, values[i]);
                mc.fontRendererObj.drawString(labels[i], cx + 7, cy + 4, C_TEXT);
            }
            cy += 16;
        }

        cy += 5;
        drawSectionHeader(mc, cx, cy, "Chat Display");
        cy += 13;

        // Combine Repeated Messages
        { boolean hov = mx >= cx && mx <= cx + CW && my >= cy && my <= cy + 14
                     && my >= clipTop && my <= clipBottom;
          cbHover[5] = lerp(cbHover[5], hov ? 1f : 0f, 0.3f);
          if (cy + 14 > clipTop && cy < clipBottom) {
              Gui.drawRect(cx, cy, cx + CW, cy + 14, blendColor(C_CARD, C_CARD_H, cbHover[5]));
              drawTogglePill(cx + CW - 22, cy + 3, data.messageCombining);
              mc.fontRendererObj.drawString("Combine Repeated Messages", cx + 7, cy + 4, C_TEXT);
          }
          cy += 16; }

        // Strip Player Brackets
        { boolean hov = mx >= cx && mx <= cx + CW && my >= cy && my <= cy + 14
                     && my >= clipTop && my <= clipBottom;
          cbHover[12] = lerp(cbHover[12], hov ? 1f : 0f, 0.3f);
          if (cy + 14 > clipTop && cy < clipBottom) {
              Gui.drawRect(cx, cy, cx + CW, cy + 14, blendColor(C_CARD, C_CARD_H, cbHover[12]));
              drawTogglePill(cx + CW - 22, cy + 3, data.stripPlayerBrackets);
              mc.fontRendererObj.drawString("Remove <> From Player Names", cx + 7, cy + 4, C_TEXT);
          }
          cy += 16; }

        // Master Custom Fonts toggle
        { boolean hov = mx >= cx && mx <= cx + CW && my >= cy && my <= cy + 14
                     && my >= clipTop && my <= clipBottom;
          cbHover[6] = lerp(cbHover[6], hov ? 1f : 0f, 0.3f);
          if (cy + 14 > clipTop && cy < clipBottom) {
              Gui.drawRect(cx, cy, cx + CW, cy + 14, blendColor(C_CARD, C_CARD_H, cbHover[6]));
              drawTogglePill(cx + CW - 22, cy + 3, data.fontSizeEnabled);
              mc.fontRendererObj.drawString("Custom Fonts", cx + 7, cy + 4, C_TEXT);
          }
          cy += 16; }

        if (data.fontSizeEnabled) {
            cy = drawFontSubCard(mc, cx, cy, mx, my, clipTop, clipBottom,
                    "Text", data.fontEnabled, data.fontName, data.fontSize,
                    fontDropdownOpen, fontDropdownScroll, fontSearchField, fontSearchText, 0);
            cy = drawFontSubCard(mc, cx, cy, mx, my, clipTop, clipBottom,
                    "Tabs", data.fontTabsEnabled, data.fontNameTabs, data.fontSizeTabs,
                    fontTabsDropdownOpen, fontTabsDropdownScroll, fontTabsSearchField, fontTabsSearchText, 1);
            cy = drawFontSubCard(mc, cx, cy, mx, my, clipTop, clipBottom,
                    "Timestamps", data.fontTimestampsEnabled, data.fontNameTimestamps, data.fontSizeTimestamps,
                    fontTimeDropdownOpen, fontTimeDropdownScroll, fontTimeSearchField, fontTimeSearchText, 2);
            cy += 4;
        }

        cy += 5;
        boolean rHov = mx >= cx && mx <= cx + CW && my >= cy && my <= cy + 16
                    && my >= clipTop && my <= clipBottom;
        resetHover = lerp(resetHover, rHov ? 1f : 0f, 0.3f);
        if (cy + 16 > clipTop && cy < clipBottom) {
            Gui.drawRect(cx, cy, cx + CW, cy + 16, blendColor(0xFF1A0A0A, 0xFF3A1010, resetHover));
            drawBorder(cx, cy, cx + CW, cy + 16, blendColor(0xFF552222, 0xFF994444, resetHover));
            int rtc = blendColor(0xFFAA4444, 0xFFFF7777, resetHover);
            int rw  = mc.fontRendererObj.getStringWidth("Reset to Defaults");
            mc.fontRendererObj.drawString("Reset to Defaults", cx + (CW - rw) / 2, cy + 4, rtc);
        }
    }

    // ── Font sub-card ─────────────────────────────────────────────────────────

    private int fontSubCardHeight(boolean enabled, boolean dropdownOpen) {
        int h = 14 + 2;
        if (enabled) {
            h += 14;
            h += 14 + (dropdownOpen ? (13 + 14 + FONT_DROPDOWN_VISIBLE * 12) : 0);
        }
        return h;
    }

    private int drawFontSubCard(Minecraft mc, int cx, int cy, int mx, int my,
                                int clipTop, int clipBottom,
                                String label, boolean enabled, String currentFont,
                                float fontSize, boolean dropOpen, int dropScroll,
                                GuiTextField searchField, String searchText, int slot) {
        int indX  = cx + 6;
        int indW  = CW - 6;
        int cardH = fontSubCardHeight(enabled, dropOpen);

        if (cy + cardH > clipTop && cy < clipBottom) {
            Gui.drawRect(indX, cy, indX + indW, cy + cardH, 0xFF141820);
            Gui.drawRect(indX, cy, indX + 2,    cy + cardH, C_ACCENT2);
        }

        int cbIdx = 7 + slot;
        boolean togHov = mx >= indX + 2 && mx <= indX + indW && my >= cy && my <= cy + 14
                      && my >= clipTop && my <= clipBottom;
        cbHover[cbIdx] = lerp(cbHover[cbIdx], togHov ? 1f : 0f, 0.3f);
        if (cy + 14 > clipTop && cy < clipBottom) {
            Gui.drawRect(indX + 2, cy, indX + indW, cy + 14,
                    blendColor(0xFF141820, 0xFF1C2430, cbHover[cbIdx]));
            drawTogglePill(indX + indW - 22, cy + 3, enabled);
            mc.fontRendererObj.drawString(label + " Font", indX + 8, cy + 4, C_TEXT);
        }
        cy += 14 + 2;

        if (!enabled) return cy;

        // Size slider
        {
            int sx   = indX + 28;
            int sw2  = indW - 52;
            float pct = (fontSize - 0.5f) / 2.5f;
            int hx2  = sx + (int)(pct * sw2);
            if (cy + 14 > clipTop && cy < clipBottom) {
                mc.fontRendererObj.drawString("Size", indX + 8, cy + 3, C_TEXT_DIM);
                Gui.drawRect(sx, cy + 4, sx + sw2, cy + 7, C_DIVIDER);
                Gui.drawRect(sx, cy + 4, hx2,      cy + 7, C_ACCENT2);
                Gui.drawRect(hx2 - 2, cy + 2, hx2 + 2, cy + 11, 0xFFEEEEEE);
                mc.fontRendererObj.drawString(String.format("%.1fx", fontSize),
                        sx + sw2 + 4, cy + 3, C_TEXT_DIM);
            }
            // Live drag
            if (Mouse.isButtonDown(0)) {
                if (draggingFontSlot == slot && draggingFontSize) {
                    float np = Math.max(0f, Math.min(1f, (float)(mx - sx) / sw2));
                    float ns = Math.round((0.5f + np * 2.5f) * 10) / 10.0f;
                    setFontSize(slot, ns);
                    data.filterVersion++;
                }
            } else if (draggingFontSlot == slot && draggingFontSize) {
                draggingFontSize = false;
                draggingFontSlot = -1;
                data.save();
            }
            cy += 14;
        }

        // Font picker dropdown row
        if (cy + 14 > clipTop && cy < clipBottom) {
            drawFontDropdownGeneric(mc, indX + 2, cy, mx, my, clipTop, clipBottom,
                    currentFont, dropOpen, dropScroll, searchField, searchText);
        }
        cy += 14 + (dropOpen ? 13 + 14 + FONT_DROPDOWN_VISIBLE * 12 : 0);
        return cy + 1;
    }

    private void drawFontDropdownGeneric(Minecraft mc, int cx, int cy, int mx, int my,
                                         int clipTop, int clipBottom,
                                         String currentFont, boolean dropOpen,
                                         int dropScroll, GuiTextField searchField, String searchText) {
        List<String> allFonts = com.betterchat.ChatRenderer.getSystemFonts();
        List<String> fonts = new java.util.ArrayList<>();
        for (String f : allFonts)
            if (searchText.isEmpty() || f.toLowerCase().contains(searchText.toLowerCase()))
                fonts.add(f);
        int maxScroll = Math.max(0, fonts.size() - FONT_DROPDOWN_VISIBLE);
        if (dropScroll > maxScroll) dropScroll = maxScroll;

        String current = currentFont.isEmpty() ? "-- Select Font --" : currentFont;
        boolean hov = mx >= cx && mx <= cx + CW && my >= cy && my <= cy + 12
                   && my >= clipTop && my <= clipBottom;

        if (cy + 12 > clipTop && cy < clipBottom) {
            Gui.drawRect(cx, cy, cx + CW, cy + 12, blendColor(C_CARD, C_CARD_H, hov ? 1f : 0f));
            drawBorder(cx, cy, cx + CW, cy + 12, dropOpen ? C_ACCENT : C_DIVIDER);
            String disp = current;
            while (disp.length() > 1 && mc.fontRendererObj.getStringWidth(disp + "...") > CW - 20)
                disp = disp.substring(0, disp.length() - 1);
            if (!disp.equals(current)) disp += "...";
            mc.fontRendererObj.drawString(disp, cx + 4, cy + 2, C_TEXT);
            mc.fontRendererObj.drawString(dropOpen ? "\u25B2" : "\u25BC", cx + CW - 10, cy + 2, C_TEXT_DIM);
        }

        if (!dropOpen) return;

        int searchY = cy + 13;
        if (searchY + 13 > clipTop && searchY < clipBottom) {
            Gui.drawRect(cx, searchY, cx + CW, searchY + 13, C_CARD);
            drawBorder(cx, searchY, cx + CW, searchY + 13, C_DIVIDER);
            searchField.xPosition = cx + 4;
            searchField.yPosition = searchY + 2;
            searchField.width     = CW - 8;
            searchField.setEnableBackgroundDrawing(false);
            searchField.drawTextBox();
            if (searchField.getText().isEmpty() && !searchField.isFocused())
                mc.fontRendererObj.drawString("Search...", cx + 6, searchY + 2, C_TEXT_DIM);
        }

        int listY = searchY + 14;
        for (int i = 0; i < FONT_DROPDOWN_VISIBLE && dropScroll + i < fonts.size(); i++) {
            String fname = fonts.get(dropScroll + i);
            boolean sel  = fname.equals(currentFont);
            boolean fhov = mx >= cx && mx <= cx + CW && my >= listY && my <= listY + 11
                        && my >= clipTop && my <= clipBottom;
            if (listY + 11 > clipTop && listY < clipBottom) {
                Gui.drawRect(cx, listY, cx + CW, listY + 11,
                        sel ? C_NAV_ACTIVE : blendColor(C_CARD, C_CARD_H, fhov ? 1f : 0f));
                if (sel) Gui.drawRect(cx, listY, cx + 2, listY + 11, C_ACCENT);
                String fn = fname;
                while (fn.length() > 1 && mc.fontRendererObj.getStringWidth(fn) > CW - 8)
                    fn = fn.substring(0, fn.length() - 1);
                mc.fontRendererObj.drawString(fn, cx + 4, listY + 2, sel ? C_ACCENT : C_TEXT);
            }
            listY += 12;
        }
        if (fonts.isEmpty() && listY > clipTop && listY < clipBottom)
            mc.fontRendererObj.drawString("No results", cx + 4, listY + 2, C_TEXT_DIM);
    }

    private void drawColorRow(Minecraft mc, int idx, int x, int y, int mx, int my,
                               int clipTop, int clipBottom) {
        if (y + 18 <= clipTop || y >= clipBottom) return;
        String[] ca = getColorAndOpac(idx);
        int rgb; try { rgb = (int) Long.parseLong(ca[0].replace("#", ""), 16); } catch (Exception e) { rgb = 0xFFFFFF; }
        int opac = Integer.parseInt(ca[1]);

        Gui.drawRect(x, y, x + CW, y + 18, blendColor(C_CARD, C_CARD_H, colorHover[idx]));
        mc.fontRendererObj.drawString(COLOR_LABELS[idx], x + 7, y + 5, C_TEXT);

        int sw = x + CW - 44;
        // Checker background for transparency preview
        Gui.drawRect(sw,      y + 3, sw + 10, y + 8,  0xFF888888);
        Gui.drawRect(sw + 10, y + 3, sw + 20, y + 8,  0xFF444444);
        Gui.drawRect(sw,      y + 8, sw + 10, y + 13, 0xFF444444);
        Gui.drawRect(sw + 10, y + 8, sw + 20, y + 13, 0xFF888888);
        Gui.drawRect(sw,      y + 3, sw + 10, y + 13, 0xFF000000 | rgb);
        Gui.drawRect(sw + 10, y + 3, sw + 20, y + 13, (opac << 24) | rgb);
        boolean swHov = mx >= sw - 1 && mx <= sw + 21 && my >= y + 2 && my <= y + 14;
        drawBorder(sw - 1, y + 2, sw + 21, y + 14, swHov ? C_ACCENT : C_DIVIDER);
        if (swHov) mc.fontRendererObj.drawString("edit", sw - 22, y + 5, C_ACCENT);
    }

    // ── Colour data access helpers ────────────────────────────────────────────

    private String[] getColorAndOpac(int idx) {
        switch (idx) {
            case 0: return new String[]{data.colorSelection,      "" + data.opacSelection};
            case 1: return new String[]{data.colorTopBar,         "" + data.opacTopBar};
            case 2: return new String[]{data.colorBackground,     "" + data.opacBackground};
            case 3: return new String[]{data.colorText,           "" + data.opacText};
            case 4: return new String[]{data.colorTime,           "" + data.opacTime};
            case 5: return new String[]{data.colorInput,          "" + data.opacInput};
            case 6: return new String[]{data.colorFadeTopBar,     "" + data.opacFadeTopBar};
            case 7: return new String[]{data.colorFadeBackground, "" + data.opacFadeBackground};
            case 8: return new String[]{data.colorWindowBorder,   "" + data.opacWindowBorder};
        }
        return new String[]{"FFFFFF", "255"};
    }

    // ── Font size helpers ─────────────────────────────────────────────────────

    public float getFontSize(int slot) {
        if (slot == 0) return data.fontSize;
        if (slot == 1) return data.fontSizeTabs;
        return data.fontSizeTimestamps;
    }

    public void setFontSize(int slot, float v) {
        if      (slot == 0) data.fontSize           = v;
        else if (slot == 1) data.fontSizeTabs        = v;
        else                data.fontSizeTimestamps  = v;
    }

    // ── Mouse click handling ──────────────────────────────────────────────────

    /**
     * Processes a click on the Appearance page.
     * @param mx  mouse X (screen coords)
     * @param my  mouse Y (screen coords)
     * @param btn mouse button
     * @param cx  content-area left X
     * @param panelY top-Y of the panel (used to compute virtualCy base)
     * @param sr  current scaled resolution
     */
    public void mouseClicked(int mx, int my, int btn, int cx, int panelY,
                             ScaledResolution sr) {
        int virtualCy = panelY + 30 - settingsScrollY;

        // Colors section
        virtualCy += 13;
        for (int i = 0; i < 9; i++) {
            int sw2 = cx + CW - 44;
            if (btn == 0 && mx >= sw2 - 1 && mx <= sw2 + 21
                    && my >= virtualCy + 2 && my <= virtualCy + 14) {
                swatchListener.onSwatchClicked(i);
                return;
            }
            virtualCy += 20;
        }

        // Options section
        virtualCy += 5 + 13;
        for (int i = 0; i < 4; i++) {
            if (btn == 0 && mx >= cx && mx <= cx + CW && my >= virtualCy && my <= virtualCy + 14) {
                switch (i) {
                    case 0: data.hideDefaultChat = !data.hideDefaultChat; break;
                    case 1: data.saveChatLog     = !data.saveChatLog;     break;
                    case 2:
                        data.isLocked = !data.isLocked;
                        if (data.isLocked) {
                            data.lockedX    = data.windowX;
                            data.lockedY    = data.windowY;
                            data.lockedW    = data.windowWidth;
                            data.lockedH    = data.windowHeight;
                            data.lockedResW = sr.getScaledWidth();
                            data.lockedResH = sr.getScaledHeight();
                        }
                        break;
                    case 3: data.showTimeStamps = !data.showTimeStamps; data.filterVersion++; break;
                }
                data.save();
                return;
            }
            virtualCy += 16;
        }

        // Chat Display section
        virtualCy += 5 + 13;

        // Combine Repeated Messages
        if (btn == 0 && mx >= cx && mx <= cx + CW && my >= virtualCy && my <= virtualCy + 14) {
            data.messageCombining = !data.messageCombining;
            data.filterVersion++;
            data.save();
            return;
        }
        virtualCy += 16;

        // Strip Player Brackets
        if (btn == 0 && mx >= cx && mx <= cx + CW && my >= virtualCy && my <= virtualCy + 14) {
            data.stripPlayerBrackets = !data.stripPlayerBrackets;
            data.filterVersion++;
            data.save();
            return;
        }
        virtualCy += 16;

        // Master Custom Fonts toggle
        if (btn == 0 && mx >= cx && mx <= cx + CW && my >= virtualCy && my <= virtualCy + 14) {
            data.fontSizeEnabled = !data.fontSizeEnabled;
            if (!data.fontSizeEnabled) {
                data.fontEnabled           = false; data.fontName              = ""; data.fontSize              = 1.0f;
                data.fontTabsEnabled       = false; data.fontNameTabs          = ""; data.fontSizeTabs          = 1.0f;
                data.fontTimestampsEnabled = false; data.fontNameTimestamps    = ""; data.fontSizeTimestamps    = 1.0f;
                fontDropdownOpen     = false; fontSearchField.setText("");     fontSearchText     = "";
                fontTabsDropdownOpen = false; fontTabsSearchField.setText(""); fontTabsSearchText = "";
                fontTimeDropdownOpen = false; fontTimeSearchField.setText(""); fontTimeSearchText = "";
                AwtFontRenderer.clearCache();
            }
            data.filterVersion++;
            data.save();
            return;
        }
        virtualCy += 16;

        if (data.fontSizeEnabled) {
            virtualCy = handleFontSubCardClick(mx, my, btn, cx, virtualCy, 0);
            virtualCy = handleFontSubCardClick(mx, my, btn, cx, virtualCy, 1);
            virtualCy = handleFontSubCardClick(mx, my, btn, cx, virtualCy, 2);
            virtualCy += 4;
        }

        // Reset button
        virtualCy += 5;
        if (btn == 0 && mx >= cx && mx <= cx + CW && my >= virtualCy && my <= virtualCy + 16) {
            data.resetToDefaults();
            data.save();
        }
    }

    private int handleFontSubCardClick(int mx, int my, int btn, int cx, int virtualCy, int slot) {
        int indX = cx + 6, indW = CW - 6;
        boolean enabled  = slot == 0 ? data.fontEnabled : slot == 1 ? data.fontTabsEnabled : data.fontTimestampsEnabled;
        boolean dropOpen = slot == 0 ? fontDropdownOpen : slot == 1 ? fontTabsDropdownOpen : fontTimeDropdownOpen;
        GuiTextField sf  = slot == 0 ? fontSearchField  : slot == 1 ? fontTabsSearchField  : fontTimeSearchField;
        String searchTxt = slot == 0 ? fontSearchText   : slot == 1 ? fontTabsSearchText   : fontTimeSearchText;

        // Row 1: enable toggle
        if (btn == 0 && mx >= indX + 2 && mx <= indX + indW && my >= virtualCy && my <= virtualCy + 14) {
            if (slot == 0) {
                data.fontEnabled = !data.fontEnabled;
                if (!data.fontEnabled) { data.fontName = ""; data.fontSize = 1.0f; fontDropdownOpen = false; fontSearchField.setText(""); fontSearchText = ""; }
            } else if (slot == 1) {
                data.fontTabsEnabled = !data.fontTabsEnabled;
                if (!data.fontTabsEnabled) { data.fontNameTabs = ""; data.fontSizeTabs = 1.0f; fontTabsDropdownOpen = false; fontTabsSearchField.setText(""); fontTabsSearchText = ""; }
            } else {
                data.fontTimestampsEnabled = !data.fontTimestampsEnabled;
                if (!data.fontTimestampsEnabled) { data.fontNameTimestamps = ""; data.fontSizeTimestamps = 1.0f; fontTimeDropdownOpen = false; fontTimeSearchField.setText(""); fontTimeSearchText = ""; }
            }
            AwtFontRenderer.clearCache(); data.filterVersion++; data.save();
            return virtualCy + 14 + 2;
        }
        virtualCy += 14 + 2;

        if (!enabled) return virtualCy + 1;

        // Row 2: size slider
        int sx  = indX + 28, sw2 = indW - 52;
        if (btn == 0 && mx >= sx && mx <= sx + sw2 && my >= virtualCy && my <= virtualCy + 14) {
            draggingFontSize = true; draggingFontSlot = slot;
            float np = Math.max(0f, Math.min(1f, (float)(mx - sx) / sw2));
            setFontSize(slot, Math.round((0.5f + np * 2.5f) * 10) / 10.0f);
            data.filterVersion++; data.save();
        }
        if (!Mouse.isButtonDown(0) && draggingFontSlot == slot) { draggingFontSize = false; draggingFontSlot = -1; }
        virtualCy += 14;

        // Row 3: dropdown header
        if (btn == 0 && mx >= indX + 2 && mx <= indX + indW && my >= virtualCy && my <= virtualCy + 12) {
            if      (slot == 0) { fontDropdownOpen     = !fontDropdownOpen;     if (fontDropdownOpen)     fontSearchField.setFocused(true); }
            else if (slot == 1) { fontTabsDropdownOpen = !fontTabsDropdownOpen; if (fontTabsDropdownOpen) fontTabsSearchField.setFocused(true); }
            else                { fontTimeDropdownOpen = !fontTimeDropdownOpen; if (fontTimeDropdownOpen) fontTimeSearchField.setFocused(true); }
            return virtualCy + 14 + (dropOpen ? 13 + 14 + FONT_DROPDOWN_VISIBLE * 12 : 0) + 1;
        }

        if (dropOpen) {
            List<String> allFonts = com.betterchat.ChatRenderer.getSystemFonts();
            List<String> fonts    = new java.util.ArrayList<>();
            for (String f : allFonts)
                if (searchTxt.isEmpty() || f.toLowerCase().contains(searchTxt.toLowerCase()))
                    fonts.add(f);
            int clampedScroll = Math.min(slot == 0 ? fontDropdownScroll : slot == 1 ? fontTabsDropdownScroll : fontTimeDropdownScroll,
                    Math.max(0, fonts.size() - FONT_DROPDOWN_VISIBLE));

            int searchY = virtualCy + 13;
            if (mx >= indX + 2 && mx <= indX + indW && my >= searchY && my <= searchY + 13) {
                sf.mouseClicked(mx, my, btn); sf.setFocused(true);
                return virtualCy + 14 + 13 + 14 + FONT_DROPDOWN_VISIBLE * 12 + 1;
            }
            int listY = searchY + 14;
            for (int i = 0; i < FONT_DROPDOWN_VISIBLE && clampedScroll + i < fonts.size(); i++) {
                if (btn == 0 && mx >= indX + 2 && mx <= indX + indW && my >= listY && my <= listY + 11) {
                    String chosen = fonts.get(clampedScroll + i);
                    if      (slot == 0) { data.fontName           = chosen; fontDropdownOpen     = false; fontSearchField.setText(""); fontSearchText     = ""; }
                    else if (slot == 1) { data.fontNameTabs        = chosen; fontTabsDropdownOpen = false; fontTabsSearchField.setText(""); fontTabsSearchText = ""; }
                    else                { data.fontNameTimestamps  = chosen; fontTimeDropdownOpen = false; fontTimeSearchField.setText(""); fontTimeSearchText = ""; }
                    AwtFontRenderer.clearCache(); data.filterVersion++; data.save();
                    return virtualCy + 14 + 13 + 14 + FONT_DROPDOWN_VISIBLE * 12 + 1;
                }
                listY += 12;
            }
        }
        return virtualCy + 14 + (dropOpen ? 13 + 14 + FONT_DROPDOWN_VISIBLE * 12 : 0) + 1;
    }

    // ── Key routing ───────────────────────────────────────────────────────────

    /** Routes keyboard input to whichever font-search field is focused. */
    public void keyTyped(char c, int code) {
        if (fontDropdownOpen && fontSearchField.isFocused()) {
            fontSearchField.textboxKeyTyped(c, code);
            fontSearchText = fontSearchField.getText(); fontDropdownScroll = 0;
            return;
        }
        if (fontTabsDropdownOpen && fontTabsSearchField.isFocused()) {
            fontTabsSearchField.textboxKeyTyped(c, code);
            fontTabsSearchText = fontTabsSearchField.getText(); fontTabsDropdownScroll = 0;
            return;
        }
        if (fontTimeDropdownOpen && fontTimeSearchField.isFocused()) {
            fontTimeSearchField.textboxKeyTyped(c, code);
            fontTimeSearchText = fontTimeSearchField.getText(); fontTimeDropdownScroll = 0;
        }
    }
}

