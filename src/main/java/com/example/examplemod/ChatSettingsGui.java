package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class ChatSettingsGui {
    private final ChatTabData data;
    private GuiTextField filterInput, exclusionInput, prefixInput, suffixInput;
    private String currentPage = "CUSTOM";
    private int selectedFilterTab = 0;

    // --- Color Picker State ---
    // Which target is being edited: 0=selection,1=topbar,2=bg,3=text,4=time. -1=none
    private int editingColorIndex = -1;
    private float pickerH = 0, pickerS = 1, pickerB = 1; // HSB 0-1
    private int pickerOpacity = 255;
    private GuiTextField pickerHexField;

    // Dragging inside the picker
    private boolean draggingHueRing = false;
    private boolean draggingSBSquare = false;
    private boolean draggingOpacitySlider = false;

    // Picker layout constants (relative to picker window top-left)
    private static final int PW = 210, PH = 245; // reduced height now that hex/buttons are anchored to preview
    private static final int WHEEL_CX = 85, WHEEL_CY = 90; // center of hue ring
    private static final int WHEEL_R_OUTER = 65, WHEEL_R_INNER = 48; // bigger ring so SB square fits inside without clipping
    private static final int SB_SIZE = 66; // slightly smaller square so it sits clearly inside the ring
    private static final int SB_X = WHEEL_CX - SB_SIZE / 2, SB_Y = WHEEL_CY - SB_SIZE / 2;

    public ChatSettingsGui(ChatTabData data) {
        this.data = data;
        Minecraft mc = Minecraft.getMinecraft();
        filterInput    = new GuiTextField(5, mc.fontRendererObj, 0, 0, 110, 10);
        exclusionInput = new GuiTextField(8, mc.fontRendererObj, 0, 0, 110, 10);
        prefixInput    = new GuiTextField(6, mc.fontRendererObj, 0, 0, 70,  10);
        suffixInput    = new GuiTextField(7, mc.fontRendererObj, 0, 0, 70,  10);
        pickerHexField = new GuiTextField(9, mc.fontRendererObj, 0, 0, 80,  10);
        pickerHexField.setMaxStringLength(6);
        updateFilterPage();
    }

    private void updateFilterPage() {
        if (selectedFilterTab >= data.tabs.size()) selectedFilterTab = 0;
        filterInput.setText(data.tabFilters.getOrDefault(selectedFilterTab, ""));
        exclusionInput.setText(data.tabExclusions.getOrDefault(selectedFilterTab, ""));
        prefixInput.setText(data.tabPrefixes.getOrDefault(selectedFilterTab, ""));
        suffixInput.setText(data.tabSuffixes.getOrDefault(selectedFilterTab, ""));
    }

    // -------------------------------------------------------------------------
    // HSB <-> RGB helpers
    // -------------------------------------------------------------------------
    private static int hsbToRgb(float h, float s, float b) {
        // Returns 0xRRGGBB
        if (s == 0) { int v = (int)(b * 255); return (v << 16) | (v << 8) | v; }
        float hh = (h % 1f) * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = b * (1 - s), q = b * (1 - s * f), t = b * (1 - s * (1 - f));
        float r, g, bl;
        switch (i) {
            case 0:  r=b; g=t; bl=p; break;
            case 1:  r=q; g=b; bl=p; break;
            case 2:  r=p; g=b; bl=t; break;
            case 3:  r=p; g=q; bl=b; break;
            case 4:  r=t; g=p; bl=b; break;
            default: r=b; g=p; bl=q; break;
        }
        return ((int)(r*255) << 16) | ((int)(g*255) << 8) | (int)(bl*255);
    }

    private static float[] rgbToHsb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >>  8) & 0xFF) / 255f;
        float b = ( rgb        & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = 0;
        if (delta != 0) {
            if (max == r)      hue = ((g - b) / delta) % 6;
            else if (max == g) hue = (b - r) / delta + 2;
            else               hue = (r - g) / delta + 4;
            hue /= 6f;
            if (hue < 0) hue += 1;
        }
        float sat = (max == 0) ? 0 : delta / max;
        return new float[]{hue, sat, max};
    }

    private String[] getColorAndOpac(int idx) {
        switch (idx) {
            case 0: return new String[]{data.colorSelection,      String.valueOf(data.opacSelection)};
            case 1: return new String[]{data.colorTopBar,         String.valueOf(data.opacTopBar)};
            case 2: return new String[]{data.colorBackground,     String.valueOf(data.opacBackground)};
            case 3: return new String[]{data.colorText,           String.valueOf(data.opacText)};
            case 4: return new String[]{data.colorTime,           String.valueOf(data.opacTime)};
            case 5: return new String[]{data.colorInput,          String.valueOf(data.opacInput)};
            case 6: return new String[]{data.colorFadeTopBar,     String.valueOf(data.opacFadeTopBar)};
            case 7: return new String[]{data.colorFadeBackground, String.valueOf(data.opacFadeBackground)};
        }
        return new String[]{"FFFFFF", "255"};
    }

    private void applyColorAndOpac(int idx, int rgb, int opac) {
        String hex = String.format("%06X", rgb & 0xFFFFFF);
        switch (idx) {
            case 0: data.colorSelection      = hex; data.opacSelection      = opac; break;
            case 1: data.colorTopBar         = hex; data.opacTopBar         = opac; break;
            case 2: data.colorBackground     = hex; data.opacBackground     = opac; break;
            case 3: data.colorText           = hex; data.opacText           = opac; break;
            case 4: data.colorTime           = hex; data.opacTime           = opac; break;
            case 5: data.colorInput          = hex; data.opacInput          = opac; break;
            case 6: data.colorFadeTopBar     = hex; data.opacFadeTopBar     = opac; break;
            case 7: data.colorFadeBackground = hex; data.opacFadeBackground = opac; break;
        }
    }

    private void openPicker(int idx) {
        editingColorIndex = idx;
        String[] ca = getColorAndOpac(idx);
        try {
            int rgb = (int) Long.parseLong(ca[0].replace("#",""), 16);
            float[] hsb = rgbToHsb(rgb);
            pickerH = hsb[0]; pickerS = hsb[1]; pickerB = hsb[2];
            pickerHexField.setText(ca[0].replace("#","").toUpperCase());
        } catch (Exception e) {
            pickerH = 0; pickerS = 1; pickerB = 1;
            pickerHexField.setText("FFFFFF");
        }
        pickerOpacity = Integer.parseInt(ca[1]);
        draggingHueRing = false; draggingSBSquare = false; draggingOpacitySlider = false;
    }

    // -------------------------------------------------------------------------
    // Draw
    // -------------------------------------------------------------------------
    public void draw(int mx, int my) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = (sr.getScaledWidth()  - 300) / 2;
        int y = (sr.getScaledHeight() - 290) / 2;

        Gui.drawRect(x, y, x + 300, y + 290, 0xFF1A1E24);
        Gui.drawRect(x, y, x + 300, y + 25,  0xFF000000);
        drawNavButton("Colors & Style", x + 5,   y + 5, currentPage.equals("CUSTOM"), mx, my);
        drawNavButton("Filters",        x + 110, y + 5, currentPage.equals("FILTER"), mx, my);

        if (currentPage.equals("CUSTOM")) {
            drawColorsPage(mc, x, y, mx, my);
        } else {
            drawFiltersPage(mc, x, y, mx, my);
        }

        if (editingColorIndex != -1) {
            drawColorPicker(mc, sr, mx, my);
        }
    }

    private static final String[] COLOR_LABELS = {"Selection", "Top Bar", "Background", "Text", "Timestamp", "Input Bar", "Top Bar (Fade)", "Background (Fade)"};

    private void drawColorsPage(Minecraft mc, int x, int y, int mx, int my) {
        int row = y + 35;
        for (int i = 0; i < 8; i++) {
            drawColorEntry(mc, i, x + 10, row, mx, my);
            row += 22;
        }
        row += 8;
        drawCheckbox(x + 10, row, data.hideDefaultChat,   "Hide Default Chat",  mc); row += 14;
        drawCheckbox(x + 10, row, data.saveChatLog,       "Save Chat History",  mc); row += 14;
        drawCheckbox(x + 10, row, data.isLocked,          "Lock Chat Position", mc); row += 14;
        drawCheckbox(x + 10, row, data.showTimeStamps,    "Show Timestamps",    mc); row += 14;
        drawCheckbox(x + 10, row, data.showNotifications, "Show Notifications", mc);

        // Reset button anchored near the bottom right
        int resetY = y + 270;
        boolean resetHover = mx >= x + 195 && mx <= x + 290 && my >= resetY && my <= resetY + 15;
        Gui.drawRect(x + 195, resetY, x + 290, resetY + 15, resetHover ? 0xFF993333 : 0xFF662222);
        mc.fontRendererObj.drawString("Reset Defaults", x + 200, resetY + 4, 0xFFFFFF);
    }

    /** Draw a single labeled color entry: label + preview swatch (click to open picker). No opacity bar here. */
    private void drawColorEntry(Minecraft mc, int idx, int x, int y, int mx, int my) {
        String[] ca = getColorAndOpac(idx);
        int rgb;
        try { rgb = (int) Long.parseLong(ca[0].replace("#",""), 16); } catch (Exception e) { rgb = 0xFFFFFF; }
        int opac = Integer.parseInt(ca[1]);
        int fullColor  = (0xFF    << 24) | rgb;
        int fadedColor = (opac    << 24) | rgb;

        mc.fontRendererObj.drawString(COLOR_LABELS[idx], x, y + 2, 0xCCCCCC);

        // Swatch — left half = full color, right half = with opacity applied
        int swatchX = x + 75;
        // Checkerboard bg hint for transparency
        Gui.drawRect(swatchX,      y,     swatchX + 10, y + 10, 0xFF888888);
        Gui.drawRect(swatchX + 10, y,     swatchX + 20, y + 10, 0xFF888888);
        Gui.drawRect(swatchX,      y + 5, swatchX + 10, y + 10, 0xFF444444);
        Gui.drawRect(swatchX + 10, y,     swatchX + 20, y + 5,  0xFF444444);
        Gui.drawRect(swatchX,      y,     swatchX + 10, y + 10, fullColor);
        Gui.drawRect(swatchX + 10, y,     swatchX + 20, y + 10, fadedColor);

        // Border — highlight on hover to hint it's clickable
        boolean swatchHover = mx >= swatchX && mx <= swatchX + 20 && my >= y && my <= y + 10;
        int borderColor = swatchHover ? 0xFF00FFFF : 0xFF555555;
        Gui.drawRect(swatchX - 1, y - 1, swatchX + 21, y,       borderColor);
        Gui.drawRect(swatchX - 1, y + 10, swatchX + 21, y + 11, borderColor);
        Gui.drawRect(swatchX - 1, y - 1, swatchX,       y + 11, borderColor);
        Gui.drawRect(swatchX + 20, y - 1, swatchX + 21, y + 11, borderColor);

        // Hex + opacity label to the right of swatch (read-only display)
        mc.fontRendererObj.drawString("#" + ca[0].toUpperCase() + "  A:" + opac, swatchX + 25, y + 2, 0x888888);
        // "click to edit" hint on hover
        if (swatchHover) mc.fontRendererObj.drawString("click to edit", swatchX + 25, y + 2, 0x00FFFF);
    }

    // -------------------------------------------------------------------------
    // Color Picker Window
    // -------------------------------------------------------------------------
    private void drawColorPicker(Minecraft mc, ScaledResolution sr, int mx, int my) {
        int px = (sr.getScaledWidth()  - PW) / 2 + 60;
        int py = (sr.getScaledHeight() - PH) / 2;

        // Background + border
        Gui.drawRect(px - 2, py - 2, px + PW + 2, py + PH + 2, 0xFF00FFFF);
        Gui.drawRect(px, py, px + PW, py + PH, 0xFF1A1E24);
        mc.fontRendererObj.drawString("Color Picker \u2014 " + COLOR_LABELS[editingColorIndex], px + 5, py + 4, 0x00FFFF);

        int wcx = px + WHEEL_CX;
        int wcy = py + WHEEL_CY;

        // --- Hue ring ---
        int outerR2 = WHEEL_R_OUTER * WHEEL_R_OUTER;
        int innerR2 = WHEEL_R_INNER * WHEEL_R_INNER;
        for (int ry = -WHEEL_R_OUTER; ry <= WHEEL_R_OUTER; ry++) {
            for (int rx = -WHEEL_R_OUTER; rx <= WHEEL_R_OUTER; rx++) {
                int dist2 = rx * rx + ry * ry;
                if (dist2 > outerR2 || dist2 < innerR2) continue;
                double angle = Math.atan2(ry, rx);
                float hue = (float)(angle / (2 * Math.PI));
                if (hue < 0) hue += 1;
                int rgb = hsbToRgb(hue, 1, 1);
                Gui.drawRect(wcx + rx, wcy + ry, wcx + rx + 1, wcy + ry + 1, 0xFF000000 | rgb);
            }
        }
        // Hue indicator
        double selAngle = pickerH * 2 * Math.PI;
        int indicR = (WHEEL_R_OUTER + WHEEL_R_INNER) / 2;
        int indX = wcx + (int)(Math.cos(selAngle) * indicR);
        int indY = wcy + (int)(Math.sin(selAngle) * indicR);
        Gui.drawRect(indX - 3, indY - 3, indX + 3, indY + 3, 0xFFFFFFFF);
        Gui.drawRect(indX - 2, indY - 2, indX + 2, indY + 2, 0xFF000000);

        // --- SB square ---
        int sbScreenX = px + SB_X;
        int sbScreenY = py + SB_Y;
        for (int sy = 0; sy < SB_SIZE; sy++) {
            for (int sx = 0; sx < SB_SIZE; sx++) {
                float s = sx / (float)(SB_SIZE - 1);
                float b = 1f - sy / (float)(SB_SIZE - 1);
                int rgb = hsbToRgb(pickerH, s, b);
                Gui.drawRect(sbScreenX + sx, sbScreenY + sy, sbScreenX + sx + 1, sbScreenY + sy + 1, 0xFF000000 | rgb);
            }
        }
        // SB cursor
        int cursorSX = sbScreenX + (int)(pickerS * (SB_SIZE - 1));
        int cursorSY = sbScreenY + (int)((1 - pickerB) * (SB_SIZE - 1));
        Gui.drawRect(cursorSX - 3, cursorSY - 3, cursorSX + 3, cursorSY + 3, 0xFFFFFFFF);
        Gui.drawRect(cursorSX - 2, cursorSY - 2, cursorSX + 2, cursorSY + 2, 0xFF000000);

        // --- Opacity slider ---
        int opSliderX = px + WHEEL_CX + WHEEL_R_OUTER + 8;
        int opSliderY = py + 16;
        int opSliderH = 140;

        mc.fontRendererObj.drawString("Opacity", opSliderX, py + 8, 0xAAAAAA);
        for (int oy = 0; oy < opSliderH; oy++) {
            int a   = 255 - (int)((oy / (float)(opSliderH - 1)) * 255);
            int rgb = hsbToRgb(pickerH, pickerS, pickerB);
            int col = (a << 24) | rgb;
            int bg  = ((oy / 4) % 2 == 0) ? 0xFF888888 : 0xFF444444;
            Gui.drawRect(opSliderX, opSliderY + oy, opSliderX + 14, opSliderY + oy + 1, bg);
            Gui.drawRect(opSliderX, opSliderY + oy, opSliderX + 14, opSliderY + oy + 1, col);
        }
        int thumbY = opSliderY + (int)((1 - pickerOpacity / 255.0) * (opSliderH - 1));
        Gui.drawRect(opSliderX - 2, thumbY - 1, opSliderX + 16, thumbY + 2, 0xFFFFFFFF);
        Gui.drawRect(opSliderX - 1, thumbY,     opSliderX + 15, thumbY + 1, 0xFF000000);
        mc.fontRendererObj.drawString(pickerOpacity + "", opSliderX, opSliderY + opSliderH + 4, 0xAAAAAA);

        // --- Preview swatch — 30px gap below the opacity value label ---
        int curRgb   = hsbToRgb(pickerH, pickerS, pickerB);
        int previewX = opSliderX;
        int previewY = opSliderY + opSliderH + 30; // increased from 18 to 30 for more breathing room
        mc.fontRendererObj.drawString("Preview", previewX, previewY - 9, 0xAAAAAA);
        Gui.drawRect(previewX,      previewY, previewX + 15, previewY + 14, 0xFF888888);
        Gui.drawRect(previewX + 15, previewY, previewX + 30, previewY + 7,  0xFF444444);
        Gui.drawRect(previewX,      previewY + 7, previewX + 15, previewY + 14, 0xFF444444);
        Gui.drawRect(previewX,      previewY, previewX + 30, previewY + 14, (pickerOpacity << 24) | curRgb);

        // --- Hex input ---
        int hexY = py + PH - 40;
        mc.fontRendererObj.drawString("#", px + 5, hexY + 2, 0xAAAAAA);
        pickerHexField.xPosition = px + 14; pickerHexField.yPosition = hexY;
        pickerHexField.drawTextBox();

        // --- Done / Cancel buttons ---
        int doneX = px + 5; int doneY = py + PH - 22;
        boolean doneHover = mx >= doneX && mx <= doneX + 80 && my >= doneY && my <= doneY + 14;
        Gui.drawRect(doneX, doneY, doneX + 80, doneY + 14, doneHover ? 0xFF007755 : 0xFF005533);
        mc.fontRendererObj.drawString("Done", doneX + 28, doneY + 3, 0x00FFCC);

        int cancelX = px + 90; int cancelY = doneY;
        boolean cancelHover = mx >= cancelX && mx <= cancelX + 80 && my >= cancelY && my <= cancelY + 14;
        Gui.drawRect(cancelX, cancelY, cancelX + 80, cancelY + 14, cancelHover ? 0xFF993333 : 0xFF662222);
        mc.fontRendererObj.drawString("Cancel", cancelX + 24, cancelY + 3, 0xFF5555);

        // --- Live drag handling ---
        if (Mouse.isButtonDown(0)) {
            int dx = mx - wcx, dy = my - wcy;
            int dist2 = dx * dx + dy * dy;
            if (draggingHueRing || (dist2 >= (WHEEL_R_INNER-4)*(WHEEL_R_INNER-4) && dist2 <= (WHEEL_R_OUTER+4)*(WHEEL_R_OUTER+4))) {
                draggingHueRing = true;
                float angle = (float) Math.atan2(dy, dx);
                pickerH = angle / (float)(2 * Math.PI);
                if (pickerH < 0) pickerH += 1;
                syncPickerToHex();
            }
            if (!draggingHueRing && (draggingSBSquare || (mx >= sbScreenX && mx <= sbScreenX + SB_SIZE && my >= sbScreenY && my <= sbScreenY + SB_SIZE))) {
                draggingSBSquare = true;
                pickerS = Math.max(0, Math.min(1, (mx - sbScreenX) / (float)(SB_SIZE - 1)));
                pickerB = Math.max(0, Math.min(1, 1 - (my - sbScreenY) / (float)(SB_SIZE - 1)));
                syncPickerToHex();
            }
            if (!draggingHueRing && !draggingSBSquare && (draggingOpacitySlider || (mx >= opSliderX - 2 && mx <= opSliderX + 16 && my >= opSliderY && my <= opSliderY + opSliderH))) {
                draggingOpacitySlider = true;
                int clamped = Math.max(opSliderY, Math.min(opSliderY + opSliderH - 1, my));
                pickerOpacity = 255 - (int)(((clamped - opSliderY) / (float)(opSliderH - 1)) * 255);
                pickerOpacity = Math.max(0, Math.min(255, pickerOpacity));
            }
        } else {
            draggingHueRing = false;
            draggingSBSquare = false;
            draggingOpacitySlider = false;
        }
    }

    private void syncPickerToHex() {
        int rgb = hsbToRgb(pickerH, pickerS, pickerB);
        pickerHexField.setText(String.format("%06X", rgb & 0xFFFFFF));
    }

    private void applyPickerFromHex() {
        try {
            int rgb = (int) Long.parseLong(pickerHexField.getText(), 16);
            float[] hsb = rgbToHsb(rgb);
            pickerH = hsb[0]; pickerS = hsb[1]; pickerB = hsb[2];
        } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // Filters page
    // -------------------------------------------------------------------------
    private void drawFiltersPage(Minecraft mc, int x, int y, int mx, int my) {
        if (selectedFilterTab >= data.tabs.size()) selectedFilterTab = 0;
        mc.fontRendererObj.drawString("Tab: " + data.tabs.get(selectedFilterTab), x + 20, y + 35, 0x00FFFF);
        mc.fontRendererObj.drawString("Prefix:", x + 20, y + 50, 0xAAAAAA);
        prefixInput.xPosition = x + 60; prefixInput.yPosition = y + 48; prefixInput.drawTextBox();
        mc.fontRendererObj.drawString("Suffix:", x + 140, y + 50, 0xAAAAAA);
        suffixInput.xPosition = x + 175; suffixInput.yPosition = y + 48; suffixInput.drawTextBox();
        mc.fontRendererObj.drawString("Inclusion (Keywords):", x + 20, y + 68, 0xAAAAAA);
        filterInput.xPosition = x + 20; filterInput.yPosition = y + 78; filterInput.drawTextBox();
        mc.fontRendererObj.drawString("Exclusion (Keywords):", x + 145, y + 68, 0xAAAAAA);
        exclusionInput.xPosition = x + 145; exclusionInput.yPosition = y + 78; exclusionInput.drawTextBox();
        drawCheckbox(x + 20, y + 95,  data.includeAllFilters.getOrDefault(selectedFilterTab, false),             "Include ALL Messages",      mc);
        drawCheckbox(x + 20, y + 110, data.includeCommandsFilters.getOrDefault(selectedFilterTab, false),        "Include Commands",           mc);
        drawCheckbox(x + 20, y + 125, data.serverMessageFilters.getOrDefault(selectedFilterTab, false),          "Include Server Messages",    mc);
        drawCheckbox(x + 20, y + 140, data.includePlayersFilters.getOrDefault(selectedFilterTab, false),         "Include Player Messages",    mc);
        drawCheckbox(x + 20, y + 155, data.includeCommandResponseFilters.getOrDefault(selectedFilterTab, false), "Include Command Responses",  mc);
        int tx = x + 20;
        for (int i = 0; i < data.tabs.size(); i++) {
            int color = (i == selectedFilterTab) ? 0x00FFFF : 0x555555;
            mc.fontRendererObj.drawString("[" + data.tabs.get(i) + "]", tx, y + 172, color);
            tx += mc.fontRendererObj.getStringWidth("[" + data.tabs.get(i) + "]") + 5;
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------
    private void drawCheckbox(int x, int y, boolean active, String label, Minecraft mc) {
        Gui.drawRect(x, y, x + 8, y + 8, 0xFF333333);
        if (active) mc.fontRendererObj.drawString("v", x + 1, y - 1, 0x00FFFF);
        mc.fontRendererObj.drawString(label, x + 12, y, 0xFFFFFF);
    }

    private void drawNavButton(String text, int x, int y, boolean active, int mx, int my) {
        int w = Minecraft.getMinecraft().fontRendererObj.getStringWidth(text) + 10;
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 15;
        Gui.drawRect(x, y, x + w, y + 15, active ? 0xFF333333 : (hover ? 0xFF222222 : 0x00000000));
        Minecraft.getMinecraft().fontRendererObj.drawString(text, x + 5, y + 4, active ? 0x00FFFF : 0xFFFFFF);
    }

    // -------------------------------------------------------------------------
    // Mouse
    // -------------------------------------------------------------------------
    public void mouseClicked(int mx, int my, int btn) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int x = (sr.getScaledWidth()  - 300) / 2;
        int y = (sr.getScaledHeight() - 290) / 2;

        if (editingColorIndex != -1) {
            int px = (sr.getScaledWidth()  - PW) / 2 + 60;
            int py = (sr.getScaledHeight() - PH) / 2;
            pickerHexField.mouseClicked(mx, my, btn);

            int opSliderY  = py + 16;
            int opSliderH  = 140;
            int previewY   = opSliderY + opSliderH + 30;
            int hexY       = previewY + 14 + 10;
            int doneY      = hexY + 16;

            int doneX = px + 5;
            if (btn == 0 && mx >= doneX && mx <= doneX + 80 && my >= doneY && my <= doneY + 14) {
                applyPickerFromHex();
                int rgb = hsbToRgb(pickerH, pickerS, pickerB);
                applyColorAndOpac(editingColorIndex, rgb, pickerOpacity);
                editingColorIndex = -1;
                data.save();
                return;
            }
            int cancelX = px + 90;
            if (btn == 0 && mx >= cancelX && mx <= cancelX + 80 && my >= doneY && my <= doneY + 14) {
                editingColorIndex = -1;
                return;
            }
            return;
        }

        // Nav
        if (mx >= x + 5   && mx <= x + 100 && my >= y + 5 && my <= y + 20) currentPage = "CUSTOM";
        if (mx >= x + 110 && mx <= x + 190 && my >= y + 5 && my <= y + 20) currentPage = "FILTER";

        if (currentPage.equals("CUSTOM")) {
            int row = y + 35;
            for (int i = 0; i < 8; i++) {
                int swatchX = x + 10 + 75;
                if (btn == 0 && mx >= swatchX && mx <= swatchX + 20 && my >= row && my <= row + 10) {
                    openPicker(i);
                    return;
                }
                row += 22;
            }
            row += 8;
            if (mx >= x + 10 && mx <= x + 200 && my >= row && my <= row + 10) data.hideDefaultChat   = !data.hideDefaultChat;   row += 14;
            if (mx >= x + 10 && mx <= x + 200 && my >= row && my <= row + 10) data.saveChatLog       = !data.saveChatLog;       row += 14;
            if (mx >= x + 10 && mx <= x + 200 && my >= row && my <= row + 10) {
                data.isLocked = !data.isLocked;
                if (data.isLocked) {
                    data.lockedX = data.windowX; data.lockedY = data.windowY;
                    data.lockedW = data.windowWidth; data.lockedH = data.windowHeight;
                    data.lockedResW = sr.getScaledWidth(); data.lockedResH = sr.getScaledHeight();
                }
            } row += 14;
            if (mx >= x + 10 && mx <= x + 200 && my >= row && my <= row + 10) data.showTimeStamps    = !data.showTimeStamps;    row += 14;
            if (mx >= x + 10 && mx <= x + 200 && my >= row && my <= row + 10) data.showNotifications = !data.showNotifications;

            int resetY = y + 270;
            if (mx >= x + 195 && mx <= x + 290 && my >= resetY && my <= resetY + 15) { data.resetToDefaults(); }
        } else {
            filterInput.mouseClicked(mx, my, btn); exclusionInput.mouseClicked(mx, my, btn);
            prefixInput.mouseClicked(mx, my, btn); suffixInput.mouseClicked(mx, my, btn);
            if (mx >= x + 20 && mx <= x + 200 && my >= y + 95  && my <= y + 105) { data.includeAllFilters.put(selectedFilterTab,             !data.includeAllFilters.getOrDefault(selectedFilterTab, false));             data.filterVersion++; }
            if (mx >= x + 20 && mx <= x + 200 && my >= y + 110 && my <= y + 120) { data.includeCommandsFilters.put(selectedFilterTab,        !data.includeCommandsFilters.getOrDefault(selectedFilterTab, false));        data.filterVersion++; }
            if (mx >= x + 20 && mx <= x + 200 && my >= y + 125 && my <= y + 135) { data.serverMessageFilters.put(selectedFilterTab,          !data.serverMessageFilters.getOrDefault(selectedFilterTab, false));          data.filterVersion++; }
            if (mx >= x + 20 && mx <= x + 200 && my >= y + 140 && my <= y + 150) { data.includePlayersFilters.put(selectedFilterTab,         !data.includePlayersFilters.getOrDefault(selectedFilterTab, false));         data.filterVersion++; }
            if (mx >= x + 20 && mx <= x + 200 && my >= y + 155 && my <= y + 165) { data.includeCommandResponseFilters.put(selectedFilterTab, !data.includeCommandResponseFilters.getOrDefault(selectedFilterTab, false)); data.filterVersion++; }
            int tx = x + 20;
            for (int i = 0; i < data.tabs.size(); i++) {
                int tw = Minecraft.getMinecraft().fontRendererObj.getStringWidth("[" + data.tabs.get(i) + "]");
                if (mx >= tx && mx <= tx + tw && my >= y + 172 && my <= y + 182) { selectedFilterTab = i; updateFilterPage(); }
                tx += tw + 5;
            }
        }
        data.save();
    }

    // -------------------------------------------------------------------------
    // Keyboard
    // -------------------------------------------------------------------------
    public void keyTyped(char c, int code) {
        if (editingColorIndex != -1) {
            if (pickerHexField.isFocused()) {
                pickerHexField.textboxKeyTyped(c, code);
                applyPickerFromHex();
            }
            return;
        }
        if (currentPage.equals("CUSTOM")) {
            // nothing typed on colors page now (no text fields)
        } else {
            if (selectedFilterTab >= data.tabs.size()) selectedFilterTab = 0;
            if (filterInput.isFocused())    filterInput.textboxKeyTyped(c, code);
            if (exclusionInput.isFocused()) exclusionInput.textboxKeyTyped(c, code);
            if (prefixInput.isFocused())    prefixInput.textboxKeyTyped(c, code);
            if (suffixInput.isFocused())    suffixInput.textboxKeyTyped(c, code);
            String oldFilter = data.tabFilters.getOrDefault(selectedFilterTab, "");
            String oldExcl   = data.tabExclusions.getOrDefault(selectedFilterTab, "");
            data.tabFilters.put(selectedFilterTab,    filterInput.getText());
            data.tabExclusions.put(selectedFilterTab, exclusionInput.getText());
            data.tabPrefixes.put(selectedFilterTab,   prefixInput.getText());
            data.tabSuffixes.put(selectedFilterTab,   suffixInput.getText());
            if (!oldFilter.equals(filterInput.getText()) || !oldExcl.equals(exclusionInput.getText()))
                data.filterVersion++;
        }
        data.save();
    }
}
