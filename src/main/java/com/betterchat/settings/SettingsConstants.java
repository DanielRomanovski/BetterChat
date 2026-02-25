package com.betterchat.settings;

/**
 * Shared layout constants, colour palette, and label arrays used across
 * all settings page renderers.
 */
public final class SettingsConstants {

    private SettingsConstants() {}

    // ── Panel geometry ────────────────────────────────────────────────────────
    /** Total panel width. */
    public static final int W  = 380;
    /** Total panel height. */
    public static final int H  = 400;
    /** Sidebar width. */
    public static final int SW = 90;
    /** Content area width (right of sidebar). */
    public static final int CW = W - SW - 8;

    // ── Colour picker popup ───────────────────────────────────────────────────
    public static final int PW   = 220;
    public static final int PH   = 232;
    /** Saturation-brightness square side length. */
    public static final int SB   = 100;
    /** Hue / opacity bar width. */
    public static final int HB_W = 12;
    /** Hue / opacity bar height. */
    public static final int HB_H = SB;

    // ── Font dropdown ─────────────────────────────────────────────────────────
    public static final int FONT_DROPDOWN_VISIBLE = 6;

    // ── Panel colour palette ──────────────────────────────────────────────────
    public static final int C_BG         = 0xF0101318;
    public static final int C_SIDEBAR    = 0xF00B0D10;
    public static final int C_HEADER     = 0xFF0D1015;
    public static final int C_ACCENT     = 0xFF4E9EFF;
    public static final int C_ACCENT2    = 0xFF2E6ECC;
    public static final int C_CARD       = 0xFF181C22;
    public static final int C_CARD_H     = 0xFF1E2530;
    public static final int C_TEXT       = 0xFFDDDDDD;
    public static final int C_TEXT_DIM   = 0xFF888899;
    public static final int C_DIVIDER    = 0xFF1E2530;
    public static final int C_NAV_ACTIVE = 0xFF1A2540;

    // ── Labels ────────────────────────────────────────────────────────────────
    public static final String[] PAGE_NAMES  = {
        "Appearance", "Filters", "Search", "Keybinds", "Mutes", "Help"
    };

    public static final String[] COLOR_LABELS = {
        "Selection", "Top Bar", "Background", "Text",
        "Timestamp", "Input Bar", "Top Bar Fade", "BG Fade",
        "Multi Window Border"
    };
}

