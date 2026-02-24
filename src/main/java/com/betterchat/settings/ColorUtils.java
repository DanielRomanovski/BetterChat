package com.betterchat.settings;

import net.minecraft.client.gui.Gui;

/**
 * Utility methods for colour math and primitive drawing used throughout
 * the settings panel.
 */
public final class ColorUtils {

    private ColorUtils() {}

    // ── HSB ↔ RGB ─────────────────────────────────────────────────────────────

    public static int hsbToRgb(float h, float s, float b) {
        if (s == 0) { int v = (int)(b * 255); return (v << 16) | (v << 8) | v; }
        float hh = (h % 1f) * 6f;
        int   i  = (int) hh;
        float f  = hh - i;
        float p  = b * (1 - s), q = b * (1 - s * f), t = b * (1 - s * (1 - f));
        float r, g, bl;
        switch (i) {
            case 0:  r = b; g = t; bl = p; break;
            case 1:  r = q; g = b; bl = p; break;
            case 2:  r = p; g = b; bl = t; break;
            case 3:  r = p; g = q; bl = b; break;
            case 4:  r = t; g = p; bl = b; break;
            default: r = b; g = p; bl = q; break;
        }
        return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(bl * 255);
    }

    public static float[] rgbToHsb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >>  8) & 0xFF) / 255f;
        float b = (rgb         & 0xFF) / 255f;
        float mx = Math.max(r, Math.max(g, b));
        float mn = Math.min(r, Math.min(g, b));
        float d  = mx - mn;
        float hh = 0;
        if (d != 0) {
            if      (mx == r) hh = ((g - b) / d) % 6;
            else if (mx == g) hh = (b - r) / d + 2;
            else              hh = (r - g) / d + 4;
            hh /= 6f;
            if (hh < 0) hh += 1;
        }
        return new float[]{ hh, mx == 0 ? 0 : d / mx, mx };
    }

    // ── Colour blending ───────────────────────────────────────────────────────

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Linearly blends two ARGB colours by t (0 = a, 1 = b). */
    public static int blendColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb2 = b & 0xFF, ba = (b >> 24) & 0xFF;
        return (((int) lerp(aa, ba, t)) << 24)
             | (((int) lerp(ar, br, t)) << 16)
             | (((int) lerp(ag, bg, t)) <<  8)
             |  (int)  lerp(ab, bb2, t);
    }

    // ── Drawing primitives ────────────────────────────────────────────────────

    /** Draws a 1-pixel border rectangle. */
    public static void drawBorder(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1,     y1,     x2,     y1 + 1, color);
        Gui.drawRect(x1,     y2 - 1, x2,     y2,     color);
        Gui.drawRect(x1,     y1,     x1 + 1, y2,     color);
        Gui.drawRect(x2 - 1, y1,     x2,     y2,     color);
    }

    /** Draws a toggle pill (on = blue / off = grey). */
    public static void drawTogglePill(int x, int y, boolean on) {
        Gui.drawRect(x, y, x + 20, y + 8, on ? SettingsConstants.C_ACCENT2 : 0xFF2A2A3A);
        int kx = on ? x + 13 : x + 1;
        Gui.drawRect(kx, y + 1, kx + 6, y + 7, 0xFFEEEEEE);
    }

    /** Draws an upper-case section header with an accent divider line below it. */
    public static void drawSectionHeader(net.minecraft.client.Minecraft mc, int x, int y, String title) {
        mc.fontRendererObj.drawString(title.toUpperCase(), x + 2, y, SettingsConstants.C_ACCENT);
        Gui.drawRect(x, y + 10, x + SettingsConstants.CW, y + 11, SettingsConstants.C_DIVIDER);
    }
}

