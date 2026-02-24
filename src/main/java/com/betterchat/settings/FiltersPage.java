package com.betterchat.settings;

import com.betterchat.ChatTabData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;

import static com.betterchat.settings.ColorUtils.*;
import static com.betterchat.settings.SettingsConstants.*;

/**
 * Renders the Filters page and handles its mouse clicks.
 *
 * Covers: tab-selector pills, prefix/suffix fields, include/exclude keyword
 * pill UIs, message-type toggles, and the notifications section.
 */
public class FiltersPage {

    private final ChatTabData data;

    // Input fields (shared state, initialised here)
    public final GuiTextField filterInput;
    public final GuiTextField exclusionInput;
    public final GuiTextField prefixInput;
    public final GuiTextField suffixInput;

    public int selectedFilterTab = 0;

    public FiltersPage(ChatTabData data) {
        this.data = data;
        Minecraft mc = Minecraft.getMinecraft();
        filterInput    = new GuiTextField(5,  mc.fontRendererObj, 0, 0, 120, 10);
        exclusionInput = new GuiTextField(8,  mc.fontRendererObj, 0, 0, 120, 10);
        prefixInput    = new GuiTextField(6,  mc.fontRendererObj, 0, 0, 76,  10);
        suffixInput    = new GuiTextField(7,  mc.fontRendererObj, 0, 0, 76,  10);
        syncFromData();
    }

    // ── Sync helpers ──────────────────────────────────────────────────────────

    /** Called whenever the selected tab changes. */
    public void syncFromData() {
        if (selectedFilterTab >= data.tabs.size()) selectedFilterTab = 0;
        filterInput.setText("");
        exclusionInput.setText("");
        prefixInput.setText(data.tabPrefixes.getOrDefault(selectedFilterTab, ""));
        suffixInput.setText(data.tabSuffixes.getOrDefault(selectedFilterTab, ""));
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    public void draw(Minecraft mc, int cx, int cy, int mx, int my) {
        // Tab selector pills
        int tx = cx;
        for (int i = 0; i < data.tabs.size(); i++) {
            String lbl = data.tabs.get(i);
            int tw  = mc.fontRendererObj.getStringWidth(lbl) + 10;
            boolean sel = (i == selectedFilterTab);
            boolean hov = mx >= tx && mx <= tx + tw && my >= cy && my <= cy + 13;
            Gui.drawRect(tx, cy, tx + tw, cy + 13,
                    sel ? C_ACCENT2 : blendColor(C_CARD, C_CARD_H, hov ? 1f : 0f));
            mc.fontRendererObj.drawString(lbl, tx + 5, cy + 2, sel ? 0xFFFFFFFF : C_TEXT_DIM);
            tx += tw + 4;
        }
        cy += 17;

        // Prefix / Suffix
        mc.fontRendererObj.drawString("Prefix", cx, cy + 2, C_TEXT_DIM);
        prefixInput.xPosition = cx + 38; prefixInput.yPosition = cy; prefixInput.drawTextBox();
        mc.fontRendererObj.drawString("Suffix", cx + CW / 2, cy + 2, C_TEXT_DIM);
        suffixInput.xPosition = cx + CW / 2 + 38; suffixInput.yPosition = cy; suffixInput.drawTextBox();
        cy += 18;

        drawSectionHeader(mc, cx, cy, "Include Keywords");
        cy += 13;
        cy = drawKeywordPills(mc, cx, cy, mx, my,
                data.tabFilters.getOrDefault(selectedFilterTab, ""),
                filterInput, true);

        drawSectionHeader(mc, cx, cy, "Exclude Keywords");
        cy += 13;
        cy = drawKeywordPills(mc, cx, cy, mx, my,
                data.tabExclusions.getOrDefault(selectedFilterTab, ""),
                exclusionInput, false);

        drawSectionHeader(mc, cx, cy, "Message Types");
        cy += 13;
        String[] fl = {"All Messages","Commands","Server Messages","Player Messages","Command Responses","Messages Sent by Me"};
        boolean[] fv = {
            data.includeAllFilters.getOrDefault(selectedFilterTab, false),
            data.includeCommandsFilters.getOrDefault(selectedFilterTab, false),
            data.serverMessageFilters.getOrDefault(selectedFilterTab, false),
            data.includePlayersFilters.getOrDefault(selectedFilterTab, false),
            data.includeCommandResponseFilters.getOrDefault(selectedFilterTab, false),
            data.sentByMeFilters.getOrDefault(selectedFilterTab, false)
        };
        for (int i = 0; i < fl.length; i++) {
            boolean hov = mx >= cx && mx <= cx + CW && my >= cy && my <= cy + 14;
            Gui.drawRect(cx, cy, cx + CW, cy + 14, blendColor(C_CARD, C_CARD_H, hov ? 1f : 0f));
            drawTogglePill(cx + CW - 22, cy + 3, fv[i]);
            mc.fontRendererObj.drawString(fl[i], cx + 7, cy + 4, C_TEXT);
            cy += 16;
        }

        cy += 5;
        drawSectionHeader(mc, cx, cy, "Notifications");
        cy += 13;

        { boolean hov = mx >= cx && mx <= cx + CW && my >= cy && my <= cy + 14;
          Gui.drawRect(cx, cy, cx + CW, cy + 14, blendColor(C_CARD, C_CARD_H, hov ? 1f : 0f));
          drawTogglePill(cx + CW - 22, cy + 3, data.showNotifications);
          mc.fontRendererObj.drawString("Enable Notifications", cx + 7, cy + 4, C_TEXT);
          cy += 16; }

        if (data.showNotifications) {
            int indX = cx + 16, indW = CW - 16;
            boolean soundDimmed = data.windowsNotifications;
            { boolean hov = !soundDimmed && mx >= indX && mx <= indX + indW && my >= cy && my <= cy + 14;
              Gui.drawRect(indX, cy, indX + indW, cy + 14, blendColor(C_CARD, C_CARD_H, hov ? 1f : 0f));
              drawTogglePill(indX + indW - 22, cy + 3, data.soundNotifications);
              mc.fontRendererObj.drawString("Sound Notification", indX + 7, cy + 4, soundDimmed ? C_TEXT_DIM : C_TEXT);
              cy += 16; }

            boolean winDimmed = data.soundNotifications;
            { boolean hov = !winDimmed && mx >= indX && mx <= indX + indW && my >= cy && my <= cy + 14;
              Gui.drawRect(indX, cy, indX + indW, cy + 14, blendColor(C_CARD, C_CARD_H, hov ? 1f : 0f));
              drawTogglePill(indX + indW - 22, cy + 3, data.windowsNotifications);
              mc.fontRendererObj.drawString("Windows Notification", indX + 7, cy + 4, winDimmed ? C_TEXT_DIM : C_TEXT);
              cy += 16; }
        }
    }

    // ── Keyword pill renderer ─────────────────────────────────────────────────

    private int drawKeywordPills(Minecraft mc, int cx, int cy, int mx, int my,
                                  String csv, GuiTextField inputField, boolean isInclude) {
        int addBtnW = 30;
        int fieldW  = CW - addBtnW - 4;
        Gui.drawRect(cx, cy, cx + fieldW, cy + 14, C_CARD);
        drawBorder(cx, cy, cx + fieldW, cy + 14,
                inputField.isFocused() ? C_ACCENT : C_DIVIDER);
        inputField.xPosition = cx + 3; inputField.yPosition = cy + 2;
        inputField.width = fieldW - 6; inputField.setEnableBackgroundDrawing(false);
        inputField.drawTextBox();
        if (inputField.getText().isEmpty() && !inputField.isFocused())
            mc.fontRendererObj.drawString(isInclude ? "Add include..." : "Add exclude...",
                    cx + 5, cy + 3, C_TEXT_DIM);

        int addX = cx + fieldW + 4;
        boolean addHov = mx >= addX && mx <= addX + addBtnW && my >= cy && my <= cy + 14;
        Gui.drawRect(addX, cy, addX + addBtnW, cy + 14, addHov ? 0xFF1A3A6A : 0xFF112244);
        drawBorder(addX, cy, addX + addBtnW, cy + 14, C_ACCENT2);
        int plusW = mc.fontRendererObj.getStringWidth("+ Add");
        mc.fontRendererObj.drawString("+ Add", addX + (addBtnW - plusW) / 2, cy + 3, C_TEXT);
        cy += 18;

        if (!csv.trim().isEmpty()) {
            String[] kws  = csv.split(",");
            int pillX = cx;
            for (String kw : kws) {
                String k = kw.trim();
                if (k.isEmpty()) continue;
                int pillW = mc.fontRendererObj.getStringWidth(k) + 20;
                if (pillX + pillW > cx + CW) { pillX = cx; cy += 14; }
                int pillColor  = isInclude ? 0xFF153520 : 0xFF351515;
                int pillBorder = isInclude ? 0xFF22AA66 : 0xFFAA2222;
                Gui.drawRect(pillX, cy, pillX + pillW, cy + 12, pillColor);
                drawBorder(pillX, cy, pillX + pillW, cy + 12, pillBorder);
                mc.fontRendererObj.drawString(k, pillX + 3, cy + 2, C_TEXT);
                int xX  = pillX + pillW - 10;
                boolean xHov = mx >= xX && mx <= xX + 8 && my >= cy && my <= cy + 12;
                mc.fontRendererObj.drawString("x", xX + 1, cy + 2, xHov ? 0xFFFF6666 : 0xFF884444);
                pillX += pillW + 3;
            }
            cy += 14;
        }
        cy += 4;
        return cy;
    }

    // ── Mouse click handling ──────────────────────────────────────────────────

    public void mouseClicked(int mx, int my, int btn, int cx, int cy) {
        Minecraft mc = Minecraft.getMinecraft();

        // Tab selector pills
        int tx = cx;
        for (int i = 0; i < data.tabs.size(); i++) {
            int tw = mc.fontRendererObj.getStringWidth(data.tabs.get(i)) + 10;
            if (btn == 0 && mx >= tx && mx <= tx + tw && my >= cy && my <= cy + 13) {
                selectedFilterTab = i; syncFromData(); data.save(); return;
            }
            tx += tw + 4;
        }
        cy += 17;

        // Prefix / Suffix
        prefixInput.mouseClicked(mx, my, btn);
        suffixInput.mouseClicked(mx, my, btn);
        cy += 18;

        // Include keywords
        cy += 13;
        cy = handleKeywordPillClick(mx, my, btn, cx, cy, true);

        // Exclude keywords
        cy += 13;
        cy = handleKeywordPillClick(mx, my, btn, cx, cy, false);

        // Message Types
        cy += 13;
        @SuppressWarnings("unchecked")
        java.util.Map<Integer, Boolean>[] maps = new java.util.Map[]{
            data.includeAllFilters, data.includeCommandsFilters,
            data.serverMessageFilters, data.includePlayersFilters,
            data.includeCommandResponseFilters, data.sentByMeFilters
        };
        for (int i = 0; i < 6; i++) {
            if (btn == 0 && mx >= cx && mx <= cx + CW && my >= cy && my <= cy + 14) {
                maps[i].put(selectedFilterTab, !maps[i].getOrDefault(selectedFilterTab, false));
                data.filterVersion++; data.save(); return;
            }
            cy += 16;
        }

        // Notifications
        cy += 5 + 13;
        if (btn == 0 && mx >= cx && mx <= cx + CW && my >= cy && my <= cy + 14) {
            data.showNotifications = !data.showNotifications;
            if (!data.showNotifications) { data.soundNotifications = false; data.windowsNotifications = false; }
            data.save(); return;
        }
        cy += 16;
        if (data.showNotifications) {
            int indX = cx + 16, indW = CW - 16;
            if (btn == 0 && mx >= indX && mx <= indX + indW && my >= cy && my <= cy + 14) {
                data.soundNotifications = !data.soundNotifications;
                if (data.soundNotifications) data.windowsNotifications = false;
                data.save(); return;
            }
            cy += 16;
            if (btn == 0 && mx >= indX && mx <= indX + indW && my >= cy && my <= cy + 14) {
                data.windowsNotifications = !data.windowsNotifications;
                if (data.windowsNotifications) data.soundNotifications = false;
                data.save(); return;
            }
        }
    }

    private int handleKeywordPillClick(int mx, int my, int btn, int cx, int cy, boolean isInclude) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiTextField inputField = isInclude ? filterInput : exclusionInput;
        String csv = isInclude
                ? data.tabFilters.getOrDefault(selectedFilterTab, "")
                : data.tabExclusions.getOrDefault(selectedFilterTab, "");

        int addBtnW = 30;
        int fieldW  = CW - addBtnW - 4;

        inputField.mouseClicked(mx, my, btn);

        int addX = cx + fieldW + 4;
        if (btn == 0 && mx >= addX && mx <= addX + addBtnW && my >= cy && my <= cy + 14) {
            String newKw = inputField.getText().trim();
            if (!newKw.isEmpty()) {
                java.util.List<String> kwList = new java.util.ArrayList<>();
                for (String k : csv.split(",")) { String t = k.trim(); if (!t.isEmpty()) kwList.add(t); }
                if (!kwList.contains(newKw)) kwList.add(newKw);
                String joined = String.join(",", kwList);
                if (isInclude) data.tabFilters.put(selectedFilterTab, joined);
                else           data.tabExclusions.put(selectedFilterTab, joined);
                inputField.setText("");
                data.filterVersion++; data.save();
            }
            cy += 18;
            String newCsv = isInclude
                    ? data.tabFilters.getOrDefault(selectedFilterTab, "")
                    : data.tabExclusions.getOrDefault(selectedFilterTab, "");
            if (!newCsv.trim().isEmpty()) cy += 14;
            cy += 4;
            return cy;
        }
        cy += 18;

        if (!csv.trim().isEmpty()) {
            String[] kws = csv.split(",");
            int pillX    = cx, pillRowY = cy;
            java.util.List<String> remaining = new java.util.ArrayList<>();
            boolean removed = false;
            for (String kw : kws) {
                String k = kw.trim();
                if (k.isEmpty()) continue;
                int pillW = mc.fontRendererObj.getStringWidth(k) + 20;
                if (pillX + pillW > cx + CW) { pillX = cx; pillRowY += 14; }
                int xX = pillX + pillW - 10;
                if (!removed && btn == 0 && mx >= xX && mx <= xX + 8
                        && my >= pillRowY && my <= pillRowY + 12) {
                    removed = true;
                } else {
                    remaining.add(k);
                }
                pillX += pillW + 3;
            }
            if (removed) {
                String joined = String.join(",", remaining);
                if (isInclude) data.tabFilters.put(selectedFilterTab, joined);
                else           data.tabExclusions.put(selectedFilterTab, joined);
                data.filterVersion++; data.save();
            }
            cy += 14;
        }
        cy += 4;
        return cy;
    }

    // ── Key routing ───────────────────────────────────────────────────────────

    public void keyTyped(char c, int code) {
        if (selectedFilterTab >= data.tabs.size()) selectedFilterTab = 0;
        if (filterInput.isFocused())    { filterInput.textboxKeyTyped(c, code);    return; }
        if (exclusionInput.isFocused()) { exclusionInput.textboxKeyTyped(c, code); return; }
        if (prefixInput.isFocused())    prefixInput.textboxKeyTyped(c, code);
        if (suffixInput.isFocused())    suffixInput.textboxKeyTyped(c, code);
        data.tabPrefixes.put(selectedFilterTab, prefixInput.getText());
        data.tabSuffixes.put(selectedFilterTab, suffixInput.getText());
    }
}

