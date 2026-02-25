package com.betterchat;

import com.betterchat.settings.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

import static com.betterchat.settings.ColorUtils.*;
import static com.betterchat.settings.SettingsConstants.*;

/**
 * The in-game settings panel drawn on top of the chat GUI.
 *
 * This class is the thin top-level coordinator.  All page-specific drawing
 * and interaction logic lives in the {@code com.betterchat.settings} package:
 *
 * <ul>
 *   <li>{@link AppearancePage}      — colours, option toggles, font controls</li>
 *   <li>{@link FiltersPage}         — per-tab keyword filters, prefix/suffix, message types</li>
 *   <li>{@link SearchPage}          — chat-log search</li>
 *   <li>{@link KeybindsPage}        — keybinds + auto-responses</li>
 *   <li>{@link MutesPage}           — muted-player list</li>
 *   <li>{@link HelpPage}            — read-only controls reference</li>
 *   <li>{@link ColorPickerOverlay}  — inline HSBA colour picker</li>
 * </ul>
 *
 * {@link com.betterchat.ChatTabHandler} calls {@link #draw}, {@link #mouseClicked},
 * and {@link #keyTyped} every frame.  When the [x] button is clicked,
 * {@link #isCloseRequested()} returns {@code true}.
 */
public class ChatSettingsGui {

    // ── Page index constants ──────────────────────────────────────────────────
    private static final int PAGE_APPEARANCE = 0;
    private static final int PAGE_FILTERS    = 1;
    private static final int PAGE_SEARCH     = 2;
    private static final int PAGE_KEYBINDS   = 3;
    private static final int PAGE_MUTES      = 4;
    private static final int PAGE_HELP       = 5;

    // ── Core state ────────────────────────────────────────────────────────────
    private final ChatTabData data;
    private int  currentPage  = PAGE_APPEARANCE;

    // Hover animation arrays
    private final float[] navHover   = new float[PAGE_NAMES.length];
    private float         donateHover = 0f;

    // Close-request flag (read + cleared by ChatTabHandler each frame)
    private boolean closeRequested = false;
    public boolean isCloseRequested() { boolean v = closeRequested; closeRequested = false; return v; }

    // ── Page renderers / controllers ──────────────────────────────────────────
    private final AppearancePage     appearancePage;
    private final FiltersPage        filtersPage;
    private final SearchPage         searchPage;
    private final KeybindsPage       keybindsPage;
    private final MutesPage          mutesPage;
    private final HelpPage           helpPage;
    private final ColorPickerOverlay colorPicker;

    // ─────────────────────────────────────────────────────────────────────────

    public ChatSettingsGui(ChatTabData data) {
        this.data = data;

        colorPicker    = new ColorPickerOverlay(data, this::applyColorAndOpac);
        appearancePage = new AppearancePage(data, this::openColorPicker);
        filtersPage    = new FiltersPage(data);
        searchPage     = new SearchPage(data);
        keybindsPage   = new KeybindsPage(data);
        mutesPage      = new MutesPage(data);
        helpPage       = new HelpPage();
    }

    /** Delegated from {@link AppearancePage.ColorSwatchClickListener}. */
    private void openColorPicker(int colorIndex) {
        String[] ca = getColorAndOpac(colorIndex);
        colorPicker.open(colorIndex, ca[0], Integer.parseInt(ca[1]));
    }

    /** Delegated from {@link ColorPickerOverlay.ApplyCallback}. */
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
            case 8: data.colorWindowBorder   = hex; data.opacWindowBorder   = opac; break;
        }
    }

    /** Returns the stored hex + opacity for a given colour index. */
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

    /** Public accessor needed by ChatTabHandler. */
    public boolean isRecordingKeybind() {
        return keybindsPage.isRecordingKeybind();
    }

    // ── Main draw entry point ─────────────────────────────────────────────────

    public void draw(int mx, int my) {
        Minecraft mc        = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = (sr.getScaledWidth()  - W) / 2;
        int y = (sr.getScaledHeight() - H) / 2;

        // Dimmed backdrop
        Gui.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), 0x88000000);

        // Panel shadow
        Gui.drawRect(x + 3, y + 3, x + W + 3, y + H + 3, 0x55000000);
        Gui.drawRect(x + 2, y + 2, x + W + 2, y + H + 2, 0x33000000);

        // Main panel
        Gui.drawRect(x, y, x + W, y + H, C_BG);

        // ── Header ───────────────────────────────────────────────────────────
        Gui.drawRect(x, y, x + W, y + 26, C_HEADER);
        Gui.drawRect(x, y + 25, x + W, y + 27, C_ACCENT);
        mc.fontRendererObj.drawString("BetterChat  Settings", x + 10, y + 8, C_TEXT);
        boolean closeHov = mx >= x + W - 18 && mx <= x + W - 4 && my >= y + 5 && my <= y + 21;
        Gui.drawRect(x + W - 18, y + 5, x + W - 4, y + 21, closeHov ? 0xFF882222 : 0xFF441111);
        mc.fontRendererObj.drawString("x", x + W - 13, y + 8, 0xFFCCCCCC);

        // ── Sidebar ───────────────────────────────────────────────────────────
        Gui.drawRect(x, y + 27, x + SW, y + H, C_SIDEBAR);
        Gui.drawRect(x + SW, y + 27, x + SW + 1, y + H, C_DIVIDER);

        for (int i = 0; i < PAGE_NAMES.length; i++) {
            int ny     = y + 38 + i * 28;
            boolean hov    = mx >= x + 4 && mx <= x + SW - 4 && my >= ny && my <= ny + 20;
            navHover[i]    = lerp(navHover[i], hov ? 1f : 0f, 0.3f);
            boolean active = (currentPage == i);
            if (active) Gui.drawRect(x + 4, ny, x + 6, ny + 20, C_ACCENT);
            int bg     = active ? C_NAV_ACTIVE : blendColor(C_SIDEBAR, C_NAV_ACTIVE, navHover[i] * 0.6f);
            Gui.drawRect(x + 6, ny, x + SW - 4, ny + 20, bg);
            int txtCol = active ? C_ACCENT : blendColor(C_TEXT_DIM, C_TEXT, navHover[i]);
            mc.fontRendererObj.drawString(PAGE_NAMES[i], x + 12, ny + 6, txtCol);
        }

        // ── Donate button ─────────────────────────────────────────────────────
        int dbY = y + H - 22;
        boolean dHov = mx >= x + 6 && mx <= x + SW - 6 && my >= dbY && my <= dbY + 14;
        donateHover  = lerp(donateHover, dHov ? 1f : 0f, 0.3f);
        int donBg  = blendColor(0xFF3A2800, 0xFF6A4A00, donateHover);
        int donBrd = blendColor(0xFFAA7700, 0xFFFFCC00, donateHover);
        int donTxt = blendColor(0xFFCCAA00, 0xFFFFEE44, donateHover);
        Gui.drawRect(x + 6, dbY, x + SW - 6, dbY + 14, donBg);
        drawBorder(x + 6, dbY, x + SW - 6, dbY + 14, donBrd);
        String donLbl = "\u2665 Donate";
        int donW = mc.fontRendererObj.getStringWidth(donLbl);
        mc.fontRendererObj.drawString(donLbl, x + 6 + (SW - 12 - donW) / 2, dbY + 3, donTxt);

        // ── Content area ──────────────────────────────────────────────────────
        int cx = x + SW + 8;
        int cy = y + 30;
        switch (currentPage) {
            case PAGE_APPEARANCE: appearancePage.draw(mc, cx, cy, mx, my); break;
            case PAGE_FILTERS:    filtersPage.draw(mc, cx, cy, mx, my);    break;
            case PAGE_SEARCH:     searchPage.draw(mc, cx, cy, mx, my);     break;
            case PAGE_KEYBINDS:   keybindsPage.draw(mc, cx, cy, mx, my);   break;
            case PAGE_MUTES:      mutesPage.draw(mc, cx, cy, mx, my);      break;
            default:              helpPage.draw(mc, cx, cy, mx, my);       break;
        }

        // ── Colour picker overlay (always drawn last — on top) ────────────────
        if (colorPicker.isOpen()) colorPicker.draw(mc, sr, mx, my);
    }

    // ── Mouse click ───────────────────────────────────────────────────────────

    public void mouseClicked(int mx, int my, int btn) {
        Minecraft mc        = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = (sr.getScaledWidth()  - W) / 2;
        int y = (sr.getScaledHeight() - H) / 2;

        // Colour picker intercepts ALL clicks while open
        if (colorPicker.mouseClicked(mx, my, btn, sr)) return;

        // Close button
        if (btn == 0 && mx >= x + W - 18 && mx <= x + W - 4 && my >= y + 5 && my <= y + 21) {
            closeRequested = true; return;
        }

        // Sidebar navigation
        for (int i = 0; i < PAGE_NAMES.length; i++) {
            int ny = y + 38 + i * 28;
            if (btn == 0 && mx >= x + 4 && mx <= x + SW - 4 && my >= ny && my <= ny + 20) {
                currentPage = i; return;
            }
        }

        // Donate button
        int dbY = y + H - 22;
        if (btn == 0 && mx >= x + 6 && mx <= x + SW - 6 && my >= dbY && my <= dbY + 14) {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://ko-fi.com/danielromanovski"));
            } catch (Exception ignored) {}
            return;
        }

        int cx = x + SW + 8;
        int cy = y + 30;
        switch (currentPage) {
            case PAGE_APPEARANCE: appearancePage.mouseClicked(mx, my, btn, cx, y, sr); break;
            case PAGE_FILTERS:    filtersPage.mouseClicked(mx, my, btn, cx, cy);        break;
            case PAGE_SEARCH:     searchPage.mouseClicked(mx, my, btn);                 break;
            case PAGE_KEYBINDS:   keybindsPage.mouseClicked(mx, my, btn, cx, cy, sr);  break;
            case PAGE_MUTES:      mutesPage.mouseClicked(mx, my, btn, cx, cy);          break;
            // PAGE_HELP has no interactive elements
        }
        data.save();
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    public void keyTyped(char c, int code) {
        // Colour picker intercepts while open
        if (colorPicker.isOpen()) {
            colorPicker.keyTyped(c, code);
            return;
        }

        switch (currentPage) {
            case PAGE_APPEARANCE: appearancePage.keyTyped(c, code); break;
            case PAGE_FILTERS:    filtersPage.keyTyped(c, code);    break;
            case PAGE_SEARCH:     searchPage.keyTyped(c, code);     break;
            case PAGE_KEYBINDS:   keybindsPage.keyTyped(c, code);   break;
            case PAGE_MUTES:      mutesPage.keyTyped(c, code);      break;
        }
        data.save();
    }
}
