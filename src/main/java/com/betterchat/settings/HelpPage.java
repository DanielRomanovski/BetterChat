package com.betterchat.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import static com.betterchat.settings.ColorUtils.*;
import static com.betterchat.settings.SettingsConstants.*;

/**
 * Renders the Help page.  This page is entirely read-only — no click or key
 * handling is needed.
 */
public class HelpPage {

    private static final String[][] ENTRIES = {
        {"Drag top bar",       "Move the chat window"},
        {"Drag bottom-right",  "Resize the chat window"},
        {"Scroll wheel",       "Scroll through messages"},
        {"Drag a tab out",     "Detach tab into new window"},
        {"Drag tab onto tab",  "Merge two windows together"},
        {"Drag tab in bar",    "Reorder tabs within window"},
        {"Right-click tab",    "Delete tab (confirm: 2nd click)"},
        {"Double-click tab",   "Rename the tab"},
        {"[+] button",         "Add a new tab"},
        {"\u2699 button",      "Open settings"},
        {"Shift+click name",   "Mute / ignore a player"},
        {"Scroll bar",         "Day-aware: thumb = current day"},
        {"\u25B2 / \u25BC",   "Jump to previous / next day"},
        {"Appearance page",    "Colours, toggles, font options"},
        {"Filters page",       "Per-tab keyword filters"},
        {"Search page",        "Search the full chat history"},
    };

    // ── Drawing ───────────────────────────────────────────────────────────────

    public void draw(Minecraft mc, int cx, int cy, int mx, int my) {
        drawSectionHeader(mc, cx, cy, "Controls & Help");
        cy += 14;

        int panelBottomY = (new ScaledResolution(mc).getScaledHeight() - H) / 2 + H - 4;
        for (String[] e : ENTRIES) {
            if (cy + 12 > panelBottomY) break;
            String key  = "\u2022 " + e[0] + ":";
            String val  = e[1];
            mc.fontRendererObj.drawString(key, cx + 4, cy + 1, C_ACCENT);
            mc.fontRendererObj.drawString(val,
                    cx + mc.fontRendererObj.getStringWidth(key + "  ") + 4,
                    cy + 1, C_TEXT);
            cy += 12;
        }
    }
}

