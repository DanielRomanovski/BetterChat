package com.betterchat.settings;

import com.betterchat.ChatTabData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

import static com.betterchat.settings.ColorUtils.*;
import static com.betterchat.settings.SettingsConstants.*;

/**
 * Inline colour-picker overlay (SB square + hue bar + opacity bar + hex field).
 *
 * Opens when a colour swatch is clicked and returns the chosen ARGB value via
 * {@link #applyCallback} when "Done" is pressed.
 */
public class ColorPickerOverlay {

    /** Called when the user confirms a colour change. */
    public interface ApplyCallback {
        void onApply(int colorIndex, int rgb, int opacity);
    }

    private final ChatTabData    data;
    private final ApplyCallback  callback;

    // Picker state
    private int   editingColorIndex = -1;
    private float pickerH = 0, pickerS = 1, pickerB = 1;
    private int   pickerOpacity = 255;

    private boolean draggingHueBar   = false;
    private boolean draggingSBSquare = false;
    private boolean draggingOpSlider = false;

    public final GuiTextField pickerHexField;

    public ColorPickerOverlay(ChatTabData data, ApplyCallback callback) {
        this.data     = data;
        this.callback = callback;
        Minecraft mc  = Minecraft.getMinecraft();
        pickerHexField = new GuiTextField(9, mc.fontRendererObj, 0, 0, 76, 10);
        pickerHexField.setMaxStringLength(6);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isOpen() { return editingColorIndex != -1; }

    public void open(int colorIndex, String hexColor, int opacity) {
        editingColorIndex = colorIndex;
        try {
            int rgb = (int) Long.parseLong(hexColor.replace("#", ""), 16);
            float[] hsb = rgbToHsb(rgb);
            pickerH = hsb[0]; pickerS = hsb[1]; pickerB = hsb[2];
            pickerHexField.setText(hexColor.replace("#", "").toUpperCase());
        } catch (Exception e) {
            pickerH = 0; pickerS = 1; pickerB = 1;
            pickerHexField.setText("FFFFFF");
        }
        pickerOpacity     = opacity;
        draggingHueBar    = false;
        draggingSBSquare  = false;
        draggingOpSlider  = false;
    }

    public void close() { editingColorIndex = -1; }

    // ── Drawing ───────────────────────────────────────────────────────────────

    public void draw(Minecraft mc, ScaledResolution sr, int mx, int my) {
        int px = (sr.getScaledWidth()  - PW) / 2;
        int py = (sr.getScaledHeight() - PH) / 2;

        // Shadow + panel
        Gui.drawRect(px + 3, py + 3, px + PW + 3, py + PH + 3, 0x66000000);
        Gui.drawRect(px, py, px + PW, py + PH, 0xFF13161C);
        Gui.drawRect(px, py, px + PW, py + 18, 0xFF0D1015);
        Gui.drawRect(px, py + 17, px + PW, py + 19, C_ACCENT);
        String titleStr = "Edit: " + COLOR_LABELS[editingColorIndex];
        int titleW = mc.fontRendererObj.getStringWidth(titleStr);
        mc.fontRendererObj.drawString(titleStr, px + (PW - titleW) / 2, py + 5, C_TEXT);

        int totalW = SB + 6 + HB_W + 6 + HB_W;
        int sbX    = px + (PW - totalW) / 2;

        int contentH = SB + 10 + 12 + 6 + 10;
        int availH   = (py + PH - 18) - (py + 19);
        int sbY      = py + 19 + Math.max(0, (availH - contentH) / 2);

        // Saturation-Brightness square
        for (int sy = 0; sy < SB; sy++) {
            for (int sx2 = 0; sx2 < SB; sx2++) {
                float s = sx2 / (float)(SB - 1), b = 1f - sy / (float)(SB - 1);
                Gui.drawRect(sbX + sx2, sbY + sy, sbX + sx2 + 1, sbY + sy + 1,
                        0xFF000000 | hsbToRgb(pickerH, s, b));
            }
        }
        int csx = sbX + (int)(pickerS * (SB - 1));
        int csy = sbY + (int)((1 - pickerB) * (SB - 1));
        Gui.drawRect(csx - 3, csy - 3, csx + 3, csy + 3, 0xFFFFFFFF);
        Gui.drawRect(csx - 2, csy - 2, csx + 2, csy + 2, 0xFF000000);

        // Hue bar
        int hbX = sbX + SB + 6, hbY = sbY;
        for (int hy = 0; hy < HB_H; hy++)
            Gui.drawRect(hbX, hbY + hy, hbX + HB_W, hbY + hy + 1,
                    0xFF000000 | hsbToRgb(hy / (float)(HB_H - 1), 1, 1));
        int htY = hbY + (int)(pickerH * (HB_H - 1));
        Gui.drawRect(hbX - 2, htY - 1, hbX + HB_W + 2, htY + 2, 0xFFFFFFFF);
        Gui.drawRect(hbX - 1, htY,     hbX + HB_W + 1, htY + 1, 0xFF000000);

        // Opacity bar
        int obX  = hbX + HB_W + 6, obY = sbY;
        int curRgb = hsbToRgb(pickerH, pickerS, pickerB);
        for (int oy = 0; oy < HB_H; oy++) {
            int a2 = 255 - (int)((oy / (float)(HB_H - 1)) * 255);
            Gui.drawRect(obX, obY + oy, obX + HB_W, obY + oy + 1,
                    ((oy / 4) % 2 == 0) ? 0xFF888888 : 0xFF444444);
            Gui.drawRect(obX, obY + oy, obX + HB_W, obY + oy + 1, (a2 << 24) | curRgb);
        }
        int otY = obY + (int)((1 - pickerOpacity / 255.0) * (HB_H - 1));
        Gui.drawRect(obX - 2, otY - 1, obX + HB_W + 2, otY + 2, 0xFFFFFFFF);
        Gui.drawRect(obX - 1, otY,     obX + HB_W + 1, otY + 1, 0xFF000000);

        // Bar labels
        int hLblW = mc.fontRendererObj.getStringWidth("H");
        int aLblW = mc.fontRendererObj.getStringWidth("A");
        mc.fontRendererObj.drawString("H", hbX + (HB_W - hLblW) / 2, hbY - 8, C_TEXT_DIM);
        mc.fontRendererObj.drawString("A", obX + (HB_W - aLblW) / 2, obY - 8, C_TEXT_DIM);
        String opacStr = "" + pickerOpacity;
        mc.fontRendererObj.drawString(opacStr,
                obX + (HB_W - mc.fontRendererObj.getStringWidth(opacStr)) / 2,
                obY + HB_H + 2, C_TEXT_DIM);

        // Preview
        int pvY = sbY + SB + 10;
        int pvW = 40, pvH = 12;
        int pvX = sbX + (SB - pvW) / 2;
        mc.fontRendererObj.drawString("Preview",
                pvX + (pvW - mc.fontRendererObj.getStringWidth("Preview")) / 2, pvY - 8, C_TEXT_DIM);
        Gui.drawRect(pvX,          pvY,           pvX + pvW / 2,  pvY + pvH / 2, 0xFF888888);
        Gui.drawRect(pvX + pvW / 2, pvY,           pvX + pvW,      pvY + pvH / 2, 0xFF444444);
        Gui.drawRect(pvX,           pvY + pvH / 2, pvX + pvW / 2,  pvY + pvH,    0xFF444444);
        Gui.drawRect(pvX + pvW / 2, pvY + pvH / 2, pvX + pvW,      pvY + pvH,    0xFF888888);
        Gui.drawRect(pvX, pvY, pvX + pvW, pvY + pvH, (pickerOpacity << 24) | curRgb);
        drawBorder(pvX - 1, pvY - 1, pvX + pvW + 1, pvY + pvH + 1, C_DIVIDER);

        // Hex input
        int hexY       = pvY + pvH + 6;
        int hashW      = mc.fontRendererObj.getStringWidth("#");
        int hexFieldW  = 52;
        int hexStartX  = px + (PW - hashW - 4 - hexFieldW) / 2;
        mc.fontRendererObj.drawString("#", hexStartX, hexY + 2, C_TEXT_DIM);
        pickerHexField.xPosition = hexStartX + hashW + 4;
        pickerHexField.yPosition = hexY;
        pickerHexField.drawTextBox();

        // Done / Cancel buttons
        int btnY  = py + PH - 18;
        int btnGap = 4;
        int btnW2  = (PW - 12 - btnGap) / 2;
        int dX = px + 6, cX = px + 6 + btnW2 + btnGap;
        boolean dHov = mx >= dX && mx <= dX + btnW2 && my >= btnY && my <= btnY + 14;
        boolean cHov = mx >= cX && mx <= cX + btnW2 && my >= btnY && my <= btnY + 14;
        Gui.drawRect(dX, btnY, dX + btnW2, btnY + 14, dHov ? 0xFF1A6644 : 0xFF114433);
        Gui.drawRect(cX, btnY, cX + btnW2, btnY + 14, cHov ? 0xFF882222 : 0xFF441111);
        drawBorder(dX, btnY, dX + btnW2, btnY + 14, dHov ? 0xFF22CC77 : 0xFF228855);
        drawBorder(cX, btnY, cX + btnW2, btnY + 14, cHov ? 0xFFCC4444 : 0xFF882222);
        String doneStr   = "Done", cancelStr = "Cancel";
        mc.fontRendererObj.drawString(doneStr,
                dX + (btnW2 - mc.fontRendererObj.getStringWidth(doneStr))   / 2, btnY + 3,
                dHov ? 0xFF44FFAA : 0xFF22CC77);
        mc.fontRendererObj.drawString(cancelStr,
                cX + (btnW2 - mc.fontRendererObj.getStringWidth(cancelStr)) / 2, btnY + 3,
                cHov ? 0xFFFF7777 : 0xFFCC4444);

        // Live drag on SB square / hue bar / opacity bar
        if (Mouse.isButtonDown(0)) {
            if (draggingHueBar || (mx >= hbX - 2 && mx <= hbX + HB_W + 2 && my >= hbY && my <= hbY + HB_H)) {
                draggingHueBar = true;
                pickerH = Math.max(0, Math.min(1, (my - hbY) / (float)(HB_H - 1)));
                syncPickerToHex();
            }
            if (!draggingHueBar && (draggingSBSquare || (mx >= sbX && mx <= sbX + SB && my >= sbY && my <= sbY + SB))) {
                draggingSBSquare = true;
                pickerS = Math.max(0, Math.min(1, (mx - sbX) / (float)(SB - 1)));
                pickerB = Math.max(0, Math.min(1, 1 - (my - sbY) / (float)(SB - 1)));
                syncPickerToHex();
            }
            if (!draggingHueBar && !draggingSBSquare
                && (draggingOpSlider || (mx >= obX - 2 && mx <= obX + HB_W + 2 && my >= obY && my <= obY + HB_H))) {
                draggingOpSlider = true;
                pickerOpacity = 255 - (int)(Math.max(0, Math.min(1, (my - obY) / (float)(HB_H - 1))) * 255);
            }
        } else {
            draggingHueBar = false; draggingSBSquare = false; draggingOpSlider = false;
        }
    }

    // ── Mouse click ───────────────────────────────────────────────────────────

    /**
     * @return true if the click was consumed by the picker (caller should not
     *         process it further), false if the picker was not open.
     */
    public boolean mouseClicked(int mx, int my, int btn, ScaledResolution sr) {
        if (!isOpen()) return false;

        int px = (sr.getScaledWidth()  - PW) / 2;
        int py = (sr.getScaledHeight() - PH) / 2;

        pickerHexField.mouseClicked(mx, my, btn);

        int btnY  = py + PH - 18;
        int btnGap = 4;
        int btnW2  = (PW - 12 - btnGap) / 2;
        int dX = px + 6, cX = px + 6 + btnW2 + btnGap;

        if (btn == 0 && mx >= dX && mx <= dX + btnW2 && my >= btnY && my <= btnY + 14) {
            applyPickerFromHex();
            callback.onApply(editingColorIndex, hsbToRgb(pickerH, pickerS, pickerB), pickerOpacity);
            editingColorIndex = -1;
            data.save();
            return true;
        }
        if (btn == 0 && mx >= cX && mx <= cX + btnW2 && my >= btnY && my <= btnY + 14) {
            editingColorIndex = -1;
            return true;
        }
        return true; // picker is open — consume all clicks
    }

    // ── Key handling ──────────────────────────────────────────────────────────

    public void keyTyped(char c, int code) {
        if (!isOpen()) return;
        if (pickerHexField.isFocused()) {
            pickerHexField.textboxKeyTyped(c, code);
            applyPickerFromHex();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void syncPickerToHex() {
        pickerHexField.setText(String.format("%06X", hsbToRgb(pickerH, pickerS, pickerB) & 0xFFFFFF));
    }

    private void applyPickerFromHex() {
        try {
            int rgb = (int) Long.parseLong(pickerHexField.getText(), 16);
            float[] h = rgbToHsb(rgb);
            pickerH = h[0]; pickerS = h[1]; pickerB = h[2];
        } catch (Exception ignored) {}
    }
}

