package com.betterchat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

/**
 * The in-game settings panel, drawn on top of the chat GUI.
 *
 * Two pages:
 *  - Appearance — colour swatches for each UI element, and feature toggle switches.
 *  - Filters    — per-tab keyword filters, prefix/suffix, and message-type toggles.
 *
 * The panel also contains an inline colour picker (SB square + hue bar + opacity bar)
 * that opens when a colour swatch is clicked.
 *
 * ChatTabHandler calls draw(), mouseClicked(), and keyTyped() each frame.
 * When the [x] button is clicked, isCloseRequested() returns true so the handler
 * knows to close the panel.
 */
public class ChatSettingsGui {

    private final ChatTabData data;

    // Input fields for the Filters page
    private GuiTextField filterInput, exclusionInput, prefixInput, suffixInput;
    // Hex input field inside the colour picker
    private GuiTextField pickerHexField;

    // 0 = Appearance page, 1 = Filters page
    private int currentPage = 0;
    private int selectedFilterTab = 0;

    // Font dropdown state — text font
    private boolean fontDropdownOpen = false;
    private int     fontDropdownScroll = 0;
    private static final int FONT_DROPDOWN_VISIBLE = 6;
    private GuiTextField fontSearchField;
    private String       fontSearchText = "";

    // Font dropdown state — tabs font
    private boolean fontTabsDropdownOpen   = false;
    private int     fontTabsDropdownScroll = 0;
    private GuiTextField fontTabsSearchField;
    private String       fontTabsSearchText = "";

    // Font dropdown state — timestamps font
    private boolean fontTimeDropdownOpen   = false;
    private int     fontTimeDropdownScroll = 0;
    private GuiTextField fontTimeSearchField;
    private String       fontTimeSearchText = "";

    // Font-size slider dragging state
    private boolean draggingFontSize = false;
    private int     draggingFontSlot = -1; // 0=text, 1=tabs, 2=timestamps

    // Scrollbar for the settings content area
    private int   settingsScrollY    = 0;   // current scroll offset in pixels
    private boolean draggingSettingsBar = false;

    // -------------------------------------------------------------------------
    // Colour picker state
    // -------------------------------------------------------------------------
    private int   editingColorIndex = -1; // index into COLOR_LABELS, or -1 when closed
    private float pickerH = 0, pickerS = 1, pickerB = 1; // hue / saturation / brightness
    private int   pickerOpacity = 255;

    private boolean draggingHueBar   = false;
    private boolean draggingSBSquare = false;
    private boolean draggingOpSlider = false;

    // Read and reset by ChatTabHandler each frame to know when to close the panel.
    private boolean closeRequested = false;
    public boolean isCloseRequested() { boolean v = closeRequested; closeRequested = false; return v; }

    // -------------------------------------------------------------------------
    // Hover animation state  —  0.0 = not hovered, 1.0 = fully hovered
    // -------------------------------------------------------------------------
    private final float[] navHover   = new float[5];
    private final float[] colorHover = new float[8];
    private final float[] cbHover    = new float[13]; // option toggle rows
    private float resetHover = 0f;
    private float donateHover = 0f;

    // Search page state
    private GuiTextField chatSearchField;
    private String       chatSearchText   = "";
    private int          searchScrollY    = 0;
    private int          searchScrollX    = 0;   // horizontal scroll in pixels
    private int          searchMaxLineW   = 0;   // widest result line in pixels (updated each frame)
    private boolean      draggingSearchVBar = false;
    private boolean      draggingSearchHBar = false;
    private List<String> searchResults    = new java.util.ArrayList<>();
    private boolean      searchDirty      = true;

    // Mutes page state
    private GuiTextField muteAddField;
    private int          muteScrollY      = 0;
    private boolean      draggingMuteVBar = false;

    // -------------------------------------------------------------------------
    // Layout constants
    // -------------------------------------------------------------------------
    private static final int W  = 380;         // total panel width
    private static final int H  = 400;         // total panel height (taller for display section)
    private static final int SW = 90;          // sidebar width
    private static final int CW = W - SW - 8;  // content area width

    // Colour picker popup dimensions
    private static final int PW   = 220, PH   = 232; // popup width / height
    private static final int SB   = 100;              // saturation-brightness square side length
    private static final int HB_W = 12, HB_H = SB;   // hue / opacity bar width / height

    // -------------------------------------------------------------------------
    // Colour palette used to draw the panel itself
    // -------------------------------------------------------------------------
    private static final int C_BG         = 0xF0101318;
    private static final int C_SIDEBAR    = 0xF00B0D10;
    private static final int C_HEADER     = 0xFF0D1015;
    private static final int C_ACCENT     = 0xFF4E9EFF;
    private static final int C_ACCENT2    = 0xFF2E6ECC;
    private static final int C_CARD       = 0xFF181C22;
    private static final int C_CARD_H     = 0xFF1E2530;
    private static final int C_TEXT       = 0xFFDDDDDD;
    private static final int C_TEXT_DIM   = 0xFF888899;
    private static final int C_DIVIDER    = 0xFF1E2530;
    private static final int C_NAV_ACTIVE = 0xFF1A2540;

    private static final String[] PAGE_NAMES  = {"Appearance", "Filters", "Search", "Mutes", "Help"};
    private static final String[] COLOR_LABELS = {
        "Selection", "Top Bar", "Background", "Text",
        "Timestamp", "Input Bar", "Top Bar Fade", "BG Fade"
    };

    // -------------------------------------------------------------------------
    public ChatSettingsGui(ChatTabData data) {
        this.data = data;
        Minecraft mc = Minecraft.getMinecraft();
        filterInput    = new GuiTextField(5, mc.fontRendererObj, 0, 0, 120, 10);
        exclusionInput = new GuiTextField(8, mc.fontRendererObj, 0, 0, 120, 10);
        prefixInput    = new GuiTextField(6, mc.fontRendererObj, 0, 0, 76,  10);
        suffixInput    = new GuiTextField(7, mc.fontRendererObj, 0, 0, 76,  10);
        pickerHexField = new GuiTextField(9, mc.fontRendererObj, 0, 0, 76,  10);
        pickerHexField.setMaxStringLength(6);
        fontSearchField = new GuiTextField(10, mc.fontRendererObj, 0, 0, 100, 10);
        fontSearchField.setMaxStringLength(64);
        fontTabsSearchField = new GuiTextField(11, mc.fontRendererObj, 0, 0, 100, 10);
        fontTabsSearchField.setMaxStringLength(64);
        fontTimeSearchField = new GuiTextField(12, mc.fontRendererObj, 0, 0, 100, 10);
        fontTimeSearchField.setMaxStringLength(64);
        chatSearchField = new GuiTextField(13, mc.fontRendererObj, 0, 0, 200, 10);
        chatSearchField.setMaxStringLength(128);
        muteAddField = new GuiTextField(14, mc.fontRendererObj, 0, 0, 160, 10);
        muteAddField.setMaxStringLength(40);
        updateFilterPage();
    }

    // -------------------------------------------------------------------------
    // HSB / RGB conversion helpers
    // -------------------------------------------------------------------------
    private static int hsbToRgb(float h, float s, float b) {
        if (s == 0) { int v = (int)(b*255); return (v<<16)|(v<<8)|v; }
        float hh = (h%1f)*6f; int i = (int)hh; float f = hh-i;
        float p = b*(1-s), q = b*(1-s*f), t = b*(1-s*(1-f));
        float r, g, bl;
        switch(i){
            case 0: r=b;g=t;bl=p;break; case 1: r=q;g=b;bl=p;break;
            case 2: r=p;g=b;bl=t;break; case 3: r=p;g=q;bl=b;break;
            case 4: r=t;g=p;bl=b;break; default: r=b;g=p;bl=q;break;
        }
        return ((int)(r*255)<<16)|((int)(g*255)<<8)|(int)(bl*255);
    }

    private static float[] rgbToHsb(int rgb) {
        float r=((rgb>>16)&0xFF)/255f, g=((rgb>>8)&0xFF)/255f, b=(rgb&0xFF)/255f;
        float mx=Math.max(r,Math.max(g,b)), mn=Math.min(r,Math.min(g,b)), d=mx-mn;
        float h=0;
        if(d!=0){
            if(mx==r) h=((g-b)/d)%6; else if(mx==g) h=(b-r)/d+2; else h=(r-g)/d+4;
            h/=6f; if(h<0) h+=1;
        }
        return new float[]{h, mx==0?0:d/mx, mx};
    }

    // -------------------------------------------------------------------------
    // Colour data helpers — read/write per-element colour+opacity from ChatTabData
    // -------------------------------------------------------------------------
    private String[] getColorAndOpac(int idx) {
        switch(idx){
            case 0: return new String[]{data.colorSelection,      ""+data.opacSelection};
            case 1: return new String[]{data.colorTopBar,         ""+data.opacTopBar};
            case 2: return new String[]{data.colorBackground,     ""+data.opacBackground};
            case 3: return new String[]{data.colorText,           ""+data.opacText};
            case 4: return new String[]{data.colorTime,           ""+data.opacTime};
            case 5: return new String[]{data.colorInput,          ""+data.opacInput};
            case 6: return new String[]{data.colorFadeTopBar,     ""+data.opacFadeTopBar};
            case 7: return new String[]{data.colorFadeBackground, ""+data.opacFadeBackground};
        }
        return new String[]{"FFFFFF","255"};
    }

    private void applyColorAndOpac(int idx, int rgb, int opac) {
        String hex = String.format("%06X", rgb&0xFFFFFF);
        switch(idx){
            case 0: data.colorSelection=hex;      data.opacSelection=opac;      break;
            case 1: data.colorTopBar=hex;         data.opacTopBar=opac;         break;
            case 2: data.colorBackground=hex;     data.opacBackground=opac;     break;
            case 3: data.colorText=hex;           data.opacText=opac;           break;
            case 4: data.colorTime=hex;           data.opacTime=opac;           break;
            case 5: data.colorInput=hex;          data.opacInput=opac;          break;
            case 6: data.colorFadeTopBar=hex;     data.opacFadeTopBar=opac;     break;
            case 7: data.colorFadeBackground=hex; data.opacFadeBackground=opac; break;
        }
    }

    private void openPicker(int idx) {
        editingColorIndex = idx;
        String[] ca = getColorAndOpac(idx);
        try {
            int rgb = (int)Long.parseLong(ca[0].replace("#",""),16);
            float[] hsb = rgbToHsb(rgb);
            pickerH=hsb[0]; pickerS=hsb[1]; pickerB=hsb[2];
            pickerHexField.setText(ca[0].replace("#","").toUpperCase());
        } catch(Exception e){ pickerH=0; pickerS=1; pickerB=1; pickerHexField.setText("FFFFFF"); }
        pickerOpacity = Integer.parseInt(ca[1]);
        draggingHueBar=false; draggingSBSquare=false; draggingOpSlider=false;
    }

    private void syncPickerToHex() {
        pickerHexField.setText(String.format("%06X", hsbToRgb(pickerH,pickerS,pickerB)&0xFFFFFF));
    }
    private void applyPickerFromHex() {
        try {
            int rgb=(int)Long.parseLong(pickerHexField.getText(),16);
            float[] h=rgbToHsb(rgb); pickerH=h[0]; pickerS=h[1]; pickerB=h[2];
        } catch(Exception ignored){}
    }

    private void updateFilterPage() {
        if (selectedFilterTab >= data.tabs.size()) selectedFilterTab = 0;
        filterInput.setText(data.tabFilters.getOrDefault(selectedFilterTab,""));
        exclusionInput.setText(data.tabExclusions.getOrDefault(selectedFilterTab,""));
        prefixInput.setText(data.tabPrefixes.getOrDefault(selectedFilterTab,""));
        suffixInput.setText(data.tabSuffixes.getOrDefault(selectedFilterTab,""));
    }

    // -------------------------------------------------------------------------
    // Animation helpers  —  lerp and colour blend used for hover transitions
    // -------------------------------------------------------------------------
    private static float lerp(float a, float b, float t) { return a+(b-a)*t; }

    /** Linearly blends two ARGB colours by t (0 = a, 1 = b). */
    private static int blendColor(int a, int b, float t) {
        int ar=(a>>16)&0xFF, ag=(a>>8)&0xFF, ab=a&0xFF, aa=(a>>24)&0xFF;
        int br=(b>>16)&0xFF, bg=(b>>8)&0xFF, bb2=b&0xFF, ba=(b>>24)&0xFF;
        return (((int)lerp(aa,ba,t))<<24)|(((int)lerp(ar,br,t))<<16)
              |(((int)lerp(ag,bg,t))<<8)|(int)lerp(ab,bb2,t);
    }

    // -------------------------------------------------------------------------
    // Draw  —  main entry point called every frame by ChatTabHandler
    // -------------------------------------------------------------------------
    public void draw(int mx, int my) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = (sr.getScaledWidth()  - W) / 2;
        int y = (sr.getScaledHeight() - H) / 2;

        // Dimmed backdrop
        Gui.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), 0x88000000);

        // Panel shadow layers
        Gui.drawRect(x+3, y+3, x+W+3, y+H+3, 0x55000000);
        Gui.drawRect(x+2, y+2, x+W+2, y+H+2, 0x33000000);

        // Main panel background
        Gui.drawRect(x, y, x+W, y+H, C_BG);

        // ── Header ──
        Gui.drawRect(x, y, x+W, y+26, C_HEADER);
        Gui.drawRect(x, y+25, x+W, y+27, C_ACCENT); // accent underline
        mc.fontRendererObj.drawString("BetterChat  Settings", x+10, y+8, C_TEXT);
        // Close [x]
        boolean closeHov = mx>=x+W-18 && mx<=x+W-4 && my>=y+5 && my<=y+21;
        Gui.drawRect(x+W-18, y+5, x+W-4, y+21, closeHov ? 0xFF882222 : 0xFF441111);
        mc.fontRendererObj.drawString("x", x+W-13, y+8, 0xFFCCCCCC);

        // ── Sidebar ──
        Gui.drawRect(x, y+27, x+SW, y+H, C_SIDEBAR);
        Gui.drawRect(x+SW, y+27, x+SW+1, y+H, C_DIVIDER); // separator

        for (int i = 0; i < PAGE_NAMES.length; i++) {
            int ny = y+38+i*28;
            boolean hov = mx>=x+4 && mx<=x+SW-4 && my>=ny && my<=ny+20;
            navHover[i] = lerp(navHover[i], hov?1f:0f, 0.3f);
            boolean active = currentPage == i;
            if (active) Gui.drawRect(x+4, ny, x+6, ny+20, C_ACCENT);
            int bg = active ? C_NAV_ACTIVE : blendColor(C_SIDEBAR, C_NAV_ACTIVE, navHover[i]*0.6f);
            Gui.drawRect(x+6, ny, x+SW-4, ny+20, bg);
            int txtCol = active ? C_ACCENT : blendColor(C_TEXT_DIM, C_TEXT, navHover[i]);
            mc.fontRendererObj.drawString(PAGE_NAMES[i], x+12, ny+6, txtCol);
        }

        // ── Donate button (bottom of sidebar, replaces version stamp) ──
        int dbY = y + H - 22;
        boolean dHov = mx>=x+6 && mx<=x+SW-6 && my>=dbY && my<=dbY+14;
        donateHover = lerp(donateHover, dHov?1f:0f, 0.3f);
        int donBg  = blendColor(0xFF3A2800, 0xFF6A4A00, donateHover);
        int donBrd = blendColor(0xFFAA7700, 0xFFFFCC00, donateHover);
        int donTxt = blendColor(0xFFCCAA00, 0xFFFFEE44, donateHover);
        Gui.drawRect(x+6, dbY, x+SW-6, dbY+14, donBg);
        drawBorder(x+6, dbY, x+SW-6, dbY+14, donBrd);
        String donLbl = "\u2665 Donate";
        int donW = mc.fontRendererObj.getStringWidth(donLbl);
        mc.fontRendererObj.drawString(donLbl, x+6+(SW-12-donW)/2, dbY+3, donTxt);

        // ── Content ──
        int cx = x+SW+8;
        int cy = y+30;
        if      (currentPage == 0) drawAppearancePage(mc, cx, cy, mx, my);
        else if (currentPage == 1) drawFiltersPage(mc, cx, cy, mx, my);
        else if (currentPage == 2) drawSearchPage(mc, cx, cy, mx, my);
        else if (currentPage == 3) drawMutesPage(mc, cx, cy, mx, my);
        else                       drawHelpPage(mc, cx, cy, mx, my);

        // ── Color picker overlay ──
        if (editingColorIndex != -1) drawColorPicker(mc, sr, mx, my);

        // Font dropdown scroll via mouse wheel
        if (currentPage == 0 && Mouse.hasWheel()) {
            int wheel = Mouse.getDWheel();
            if (wheel != 0) {
                List<String> allFonts2 = com.betterchat.ChatRenderer.getSystemFonts();
                if (fontDropdownOpen && data.fontEnabled) {
                    int ms = Math.max(0, allFonts2.size() - FONT_DROPDOWN_VISIBLE);
                    fontDropdownScroll = Math.max(0, Math.min(ms, fontDropdownScroll + (wheel>0?-1:1)));
                }
                if (fontTabsDropdownOpen && data.fontTabsEnabled) {
                    int ms = Math.max(0, allFonts2.size() - FONT_DROPDOWN_VISIBLE);
                    fontTabsDropdownScroll = Math.max(0, Math.min(ms, fontTabsDropdownScroll + (wheel>0?-1:1)));
                }
                if (fontTimeDropdownOpen && data.fontTimestampsEnabled) {
                    int ms = Math.max(0, allFonts2.size() - FONT_DROPDOWN_VISIBLE);
                    fontTimeDropdownScroll = Math.max(0, Math.min(ms, fontTimeDropdownScroll + (wheel>0?-1:1)));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Appearance page
    // -------------------------------------------------------------------------
    private void drawAppearancePage(Minecraft mc, int cx, int cy, int mx, int my) {
        // The content area height available for scrolling
        int contentAreaTop    = cy;
        int contentAreaHeight = H - (cy - (/* panel y */ (new ScaledResolution(mc).getScaledHeight() - H) / 2)) - 4;
        int scrollBarX        = cx + CW + 2;
        int scrollBarW        = 5;

        // Measure total content height by doing a dry-run pass
        int totalContentH = measureAppearanceContent(mc);

        // Clamp scroll
        int maxScroll = Math.max(0, totalContentH - contentAreaHeight);
        settingsScrollY = Math.max(0, Math.min(maxScroll, settingsScrollY));

        // Handle mouse-wheel scroll over the content area
        if (Mouse.hasWheel()) {
            ScaledResolution sr2 = new ScaledResolution(mc);
            int panelX = (sr2.getScaledWidth() - W) / 2;
            int panelY = (sr2.getScaledHeight() - H) / 2;
            if (mx >= panelX + SW && mx <= panelX + W - scrollBarW - 2
                    && my >= panelY && my <= panelY + H) {
                int wheel = Mouse.getDWheel();
                if (wheel != 0) settingsScrollY = Math.max(0, Math.min(maxScroll,
                        settingsScrollY + (wheel > 0 ? -16 : 16)));
            }
        }

        // Scrollbar drag
        if (maxScroll > 0) {
            int barH    = Math.max(20, contentAreaHeight * contentAreaHeight / Math.max(1, totalContentH));
            int barY    = contentAreaTop + (int)((long)(contentAreaHeight - barH) * settingsScrollY / maxScroll);
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
            // Draw track + thumb
            Gui.drawRect(scrollBarX, contentAreaTop, scrollBarX + scrollBarW,
                    contentAreaTop + contentAreaHeight, 0x22FFFFFF);
            int barY2 = contentAreaTop + (maxScroll == 0 ? 0 :
                    (int)((long)(contentAreaHeight - barH) * settingsScrollY / maxScroll));
            Gui.drawRect(scrollBarX, barY2, scrollBarX + scrollBarW,
                    barY2 + barH, draggingSettingsBar || barHov ? C_ACCENT : C_ACCENT2);
        }

        // Enable GL scissor to clip content to the panel area
        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();
        int scissorX = (cx - 2) * scale;
        int scissorY = sr.getScaledHeight() * scale - (contentAreaTop + contentAreaHeight) * scale;
        int scissorW = (CW + 2) * scale;
        int scissorH = contentAreaHeight * scale;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        // Draw all content at (cy - settingsScrollY) offset
        int drawY = cy - settingsScrollY;
        drawAppearanceContent(mc, cx, drawY, mx, my, contentAreaTop, contentAreaTop + contentAreaHeight);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** Returns the total pixel height that drawAppearanceContent would produce. */
    private int measureAppearanceContent(Minecraft mc) {
        int h = 0;
        h += 13 + 8 * 20 + 5;           // Colors header + 8 rows + gap
        h += 13 + 5 * 16 + 5;           // Options header + 5 toggles + gap
        h += 13 + 16;                    // Chat Display header + combine toggle
        h += 16;                         // stripPlayerBrackets toggle
        // Master "Custom Fonts" toggle row
        h += 16;
        if (data.fontSizeEnabled) {
            h += fontSubCardHeight(data.fontEnabled,           fontDropdownOpen)
               + fontSubCardHeight(data.fontTabsEnabled,       fontTabsDropdownOpen)
               + fontSubCardHeight(data.fontTimestampsEnabled, fontTimeDropdownOpen)
               + 4 + 3; // gap between cards + 1px per card
        }
        h += 5 + 16 + 8;                 // gap + reset button + bottom padding
        return h;
    }

    private void drawAppearanceContent(Minecraft mc, int cx, int cy, int mx, int my,
                                       int clipTop, int clipBottom) {
        drawSectionHeader(mc, cx, cy, "Colors");
        cy += 13;
        for (int i = 0; i < 8; i++) {
            boolean hov = mx>=cx && mx<=cx+CW && my>=cy && my<=cy+18 && my>=clipTop && my<=clipBottom;
            colorHover[i] = lerp(colorHover[i], hov?1f:0f, 0.3f);
            if (cy+18 > clipTop && cy < clipBottom)
                drawColorRow(mc, i, cx, cy, mx, my, clipTop, clipBottom);
            cy += 20;
        }

        cy += 5;
        drawSectionHeader(mc, cx, cy, "Options");
        cy += 13;

        String[] labels  = {"Hide Default Chat","Save Chat History","Lock Chat Position","Show Timestamps","Show Notifications"};
        boolean[] values = {data.hideDefaultChat, data.saveChatLog, data.isLocked, data.showTimeStamps, data.showNotifications};
        for (int i = 0; i < 5; i++) {
            boolean hov = mx>=cx && mx<=cx+CW && my>=cy && my<=cy+14 && my>=clipTop && my<=clipBottom;
            cbHover[i] = lerp(cbHover[i], hov?1f:0f, 0.3f);
            if (cy+14 > clipTop && cy < clipBottom) {
                Gui.drawRect(cx, cy, cx+CW, cy+14, blendColor(C_CARD, C_CARD_H, cbHover[i]));
                drawTogglePill(cx+CW-22, cy+3, values[i]);
                mc.fontRendererObj.drawString(labels[i], cx+7, cy+4, C_TEXT);
            }
            cy += 16;
        }

        cy += 5;
        drawSectionHeader(mc, cx, cy, "Chat Display");
        cy += 13;

        // Combine Repeated Messages
        { boolean hov = mx>=cx && mx<=cx+CW && my>=cy && my<=cy+14 && my>=clipTop && my<=clipBottom;
          cbHover[5] = lerp(cbHover[5], hov?1f:0f, 0.3f);
          if (cy+14>clipTop && cy<clipBottom) {
              Gui.drawRect(cx, cy, cx+CW, cy+14, blendColor(C_CARD, C_CARD_H, cbHover[5]));
              drawTogglePill(cx+CW-22, cy+3, data.messageCombining);
              mc.fontRendererObj.drawString("Combine Repeated Messages", cx+7, cy+4, C_TEXT);
          } cy += 16; }

        // Strip Player Brackets (<Name> → Name:)
        { boolean hov = mx>=cx && mx<=cx+CW && my>=cy && my<=cy+14 && my>=clipTop && my<=clipBottom;
          cbHover[12] = lerp(cbHover[12], hov?1f:0f, 0.3f);
          if (cy+14>clipTop && cy<clipBottom) {
              Gui.drawRect(cx, cy, cx+CW, cy+14, blendColor(C_CARD, C_CARD_H, cbHover[12]));
              drawTogglePill(cx+CW-22, cy+3, data.stripPlayerBrackets);
              mc.fontRendererObj.drawString("Remove <> From Player Names", cx+7, cy+4, C_TEXT);
          } cy += 16; }

        // ── Master Custom Fonts toggle ──
        { boolean hov = mx>=cx && mx<=cx+CW && my>=cy && my<=cy+14 && my>=clipTop && my<=clipBottom;
          cbHover[6] = lerp(cbHover[6], hov?1f:0f, 0.3f);
          if (cy+14>clipTop && cy<clipBottom) {
              Gui.drawRect(cx, cy, cx+CW, cy+14, blendColor(C_CARD, C_CARD_H, cbHover[6]));
              drawTogglePill(cx+CW-22, cy+3, data.fontSizeEnabled);
              mc.fontRendererObj.drawString("Custom Fonts", cx+7, cy+4, C_TEXT);
          } cy += 16; }

        if (data.fontSizeEnabled) {
            // ── Text font sub-card ──
            cy = drawFontSubCard(mc, cx, cy, mx, my, clipTop, clipBottom,
                    "Text", data.fontEnabled, data.fontName, data.fontSize,
                    fontDropdownOpen, fontDropdownScroll, fontSearchField, fontSearchText, 0);

            // ── Tabs font sub-card ──
            cy = drawFontSubCard(mc, cx, cy, mx, my, clipTop, clipBottom,
                    "Tabs", data.fontTabsEnabled, data.fontNameTabs, data.fontSizeTabs,
                    fontTabsDropdownOpen, fontTabsDropdownScroll, fontTabsSearchField, fontTabsSearchText, 1);

            // ── Timestamps font sub-card ──
            cy = drawFontSubCard(mc, cx, cy, mx, my, clipTop, clipBottom,
                    "Timestamps", data.fontTimestampsEnabled, data.fontNameTimestamps, data.fontSizeTimestamps,
                    fontTimeDropdownOpen, fontTimeDropdownScroll, fontTimeSearchField, fontTimeSearchText, 2);

            cy += 4; // gap after cards
        }

        cy += 5;
        boolean rHov = mx>=cx && mx<=cx+CW && my>=cy && my<=cy+16 && my>=clipTop && my<=clipBottom;
        resetHover = lerp(resetHover, rHov?1f:0f, 0.3f);
        if (cy+16>clipTop && cy<clipBottom) {
            Gui.drawRect(cx, cy, cx+CW, cy+16, blendColor(0xFF1A0A0A, 0xFF3A1010, resetHover));
            drawBorder(cx, cy, cx+CW, cy+16, blendColor(0xFF552222, 0xFF994444, resetHover));
            int rtc = blendColor(0xFFAA4444, 0xFFFF7777, resetHover);
            int rw  = mc.fontRendererObj.getStringWidth("Reset to Defaults");
            mc.fontRendererObj.drawString("Reset to Defaults", cx+(CW-rw)/2, cy+4, rtc);
        }
    }

    private int fontSubCardHeight(boolean enabled, boolean dropdownOpen) {
        // Card header (14) + if enabled: size slider (14) + font picker row (14) + dropdown list
        int h = 14 + 2; // toggle row + gap
        if (enabled) {
            h += 14; // size slider
            h += 14 + (dropdownOpen ? (13 + 14 + FONT_DROPDOWN_VISIBLE * 12) : 0); // font picker
        }
        return h;
    }

    /**
     * Draws a font sub-card (indented card with enable toggle, and when enabled: size slider + font picker).
     * Returns updated cy after the card.
     * slot: 0=text, 1=tabs, 2=timestamps
     */
    private int drawFontSubCard(Minecraft mc, int cx, int cy, int mx, int my,
                                 int clipTop, int clipBottom,
                                 String label, boolean enabled, String currentFont,
                                 float fontSize, boolean dropOpen, int dropScroll,
                                 GuiTextField searchField, String searchText, int slot) {
        int indX = cx + 6;
        int indW = CW - 6;
        int cardH = fontSubCardHeight(enabled, dropOpen);

        // Card background with accent left border
        if (cy + cardH > clipTop && cy < clipBottom) {
            Gui.drawRect(indX, cy, indX + indW, cy + cardH, 0xFF141820);
            Gui.drawRect(indX, cy, indX + 2, cy + cardH, C_ACCENT2);
        }

        // Row 1: enable toggle + label
        int cbIdx = 7 + slot;
        boolean togHov = mx>=indX+2 && mx<=indX+indW && my>=cy && my<=cy+14 && my>=clipTop && my<=clipBottom;
        cbHover[cbIdx] = lerp(cbHover[cbIdx], togHov?1f:0f, 0.3f);
        if (cy+14>clipTop && cy<clipBottom) {
            Gui.drawRect(indX+2, cy, indX+indW, cy+14, blendColor(0xFF141820, 0xFF1C2430, cbHover[cbIdx]));
            drawTogglePill(indX+indW-22, cy+3, enabled);
            mc.fontRendererObj.drawString(label + " Font", indX+8, cy+4, C_TEXT);
        }
        cy += 14 + 2; // toggle + gap

        if (!enabled) return cy;

        // Row 2: size slider
        {
            int sx = indX + 28, sw2 = indW - 52;
            float pct = (fontSize - 0.5f) / 2.5f;
            int hx2 = sx + (int)(pct * sw2);
            if (cy+14>clipTop && cy<clipBottom) {
                mc.fontRendererObj.drawString("Size", indX+8, cy+3, C_TEXT_DIM);
                Gui.drawRect(sx, cy+4, sx+sw2, cy+7, C_DIVIDER);
                Gui.drawRect(sx, cy+4, hx2, cy+7, C_ACCENT2);
                Gui.drawRect(hx2-2, cy+2, hx2+2, cy+11, 0xFFEEEEEE);
                mc.fontRendererObj.drawString(String.format("%.1fx", fontSize), sx+sw2+4, cy+3, C_TEXT_DIM);
            }
            // Handle live drag
            if (Mouse.isButtonDown(0)) {
                if (draggingFontSlot == slot && draggingFontSize) {
                    float np = Math.max(0f, Math.min(1f, (float)(mx - sx) / sw2));
                    float ns = Math.round((0.5f + np * 2.5f) * 10) / 10.0f;
                    setFontSize(slot, ns);
                    data.filterVersion++;
                }
            } else if (draggingFontSlot == slot && draggingFontSize) {
                draggingFontSize = false; draggingFontSlot = -1; data.save();
            }
            cy += 14;
        }

        // Row 3: font picker dropdown
        if (cy+14>clipTop && cy<clipBottom) {
            drawFontDropdownGeneric(mc, indX+2, cy, mx, my, clipTop, clipBottom,
                    currentFont, dropOpen, dropScroll, searchField, searchText);
        }
        cy += 14 + (dropOpen ? 13 + 14 + FONT_DROPDOWN_VISIBLE * 12 : 0);

        return cy + 1; // 1px gap
    }

    private float getFontSize(int slot) {
        if (slot == 0) return data.fontSize;
        if (slot == 1) return data.fontSizeTabs;
        return data.fontSizeTimestamps;
    }

    private void setFontSize(int slot, float v) {
        if (slot == 0) data.fontSize = v;
        else if (slot == 1) data.fontSizeTabs = v;
        else data.fontSizeTimestamps = v;
    }

    /** Generic font dropdown renderer used inside drawFontSubCard. */
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
        boolean hov = mx>=cx && mx<=cx+CW && my>=cy && my<=cy+12 && my>=clipTop && my<=clipBottom;

        if (cy+12>clipTop && cy<clipBottom) {
            Gui.drawRect(cx, cy, cx+CW, cy+12, blendColor(C_CARD, C_CARD_H, hov?1f:0f));
            drawBorder(cx, cy, cx+CW, cy+12, dropOpen ? C_ACCENT : C_DIVIDER);
            String disp = current;
            while (disp.length() > 1 && mc.fontRendererObj.getStringWidth(disp+"...") > CW-20)
                disp = disp.substring(0, disp.length()-1);
            if (!disp.equals(current)) disp += "...";
            mc.fontRendererObj.drawString(disp, cx+4, cy+2, C_TEXT);
            mc.fontRendererObj.drawString(dropOpen ? "\u25B2" : "\u25BC", cx+CW-10, cy+2, C_TEXT_DIM);
        }

        if (!dropOpen) return;

        int searchY = cy + 13;
        if (searchY+13>clipTop && searchY<clipBottom) {
            Gui.drawRect(cx, searchY, cx+CW, searchY+13, C_CARD);
            drawBorder(cx, searchY, cx+CW, searchY+13, C_DIVIDER);
            searchField.xPosition = cx+4; searchField.yPosition = searchY+2;
            searchField.width = CW-8; searchField.setEnableBackgroundDrawing(false);
            searchField.drawTextBox();
            if (searchField.getText().isEmpty() && !searchField.isFocused())
                mc.fontRendererObj.drawString("Search...", cx+6, searchY+2, C_TEXT_DIM);
        }

        int listY = searchY + 14;
        for (int i = 0; i < FONT_DROPDOWN_VISIBLE && dropScroll+i < fonts.size(); i++) {
            String fname = fonts.get(dropScroll + i);
            boolean sel = fname.equals(currentFont);
            boolean fhov = mx>=cx && mx<=cx+CW && my>=listY && my<=listY+11 && my>=clipTop && my<=clipBottom;
            if (listY+11>clipTop && listY<clipBottom) {
                Gui.drawRect(cx, listY, cx+CW, listY+11,
                        sel ? C_NAV_ACTIVE : blendColor(C_CARD, C_CARD_H, fhov?1f:0f));
                if (sel) Gui.drawRect(cx, listY, cx+2, listY+11, C_ACCENT);
                String fn = fname;
                while (fn.length()>1 && mc.fontRendererObj.getStringWidth(fn)>CW-8)
                    fn = fn.substring(0, fn.length()-1);
                mc.fontRendererObj.drawString(fn, cx+4, listY+2, sel?C_ACCENT:C_TEXT);
            }
            listY += 12;
        }
        if (fonts.isEmpty() && listY>clipTop && listY<clipBottom)
            mc.fontRendererObj.drawString("No results", cx+4, listY+2, C_TEXT_DIM);
    }

    private void drawColorRow(Minecraft mc, int idx, int x, int y, int mx, int my,
                              int clipTop, int clipBottom) {
        if (y + 18 <= clipTop || y >= clipBottom) return;
        String[] ca = getColorAndOpac(idx);
        int rgb; try{ rgb=(int)Long.parseLong(ca[0].replace("#",""),16); }catch(Exception e){ rgb=0xFFFFFF; }
        int opac = Integer.parseInt(ca[1]);

        Gui.drawRect(x, y, x+CW, y+18, blendColor(C_CARD, C_CARD_H, colorHover[idx]));
        mc.fontRendererObj.drawString(COLOR_LABELS[idx], x+7, y+5, C_TEXT);

        // Swatch
        int sw = x+CW-44;
        // checker bg
        Gui.drawRect(sw,    y+3, sw+10, y+8,  0xFF888888);
        Gui.drawRect(sw+10, y+3, sw+20, y+8,  0xFF444444);
        Gui.drawRect(sw,    y+8, sw+10, y+13, 0xFF444444);
        Gui.drawRect(sw+10, y+8, sw+20, y+13, 0xFF888888);
        // color halves
        Gui.drawRect(sw,    y+3, sw+10, y+13, 0xFF000000|rgb);
        Gui.drawRect(sw+10, y+3, sw+20, y+13, (opac<<24)|rgb);
        // border — cyan on hover
        boolean swHov = mx>=sw-1 && mx<=sw+21 && my>=y+2 && my<=y+14;
        drawBorder(sw-1, y+2, sw+21, y+14, swHov ? C_ACCENT : C_DIVIDER);
        if (swHov) mc.fontRendererObj.drawString("edit", sw-22, y+5, C_ACCENT);
    }

    private void drawTogglePill(int x, int y, boolean on) {
        Gui.drawRect(x, y, x+20, y+8, on ? C_ACCENT2 : 0xFF2A2A3A);
        int kx = on ? x+13 : x+1;
        Gui.drawRect(kx, y+1, kx+6, y+7, 0xFFEEEEEE);
    }

    private void drawSectionHeader(Minecraft mc, int x, int y, String title) {
        mc.fontRendererObj.drawString(title.toUpperCase(), x+2, y, C_ACCENT);
        Gui.drawRect(x, y+10, x+CW, y+11, C_DIVIDER);
    }

    // -------------------------------------------------------------------------
    // Filters page
    // -------------------------------------------------------------------------
    private void drawFiltersPage(Minecraft mc, int cx, int cy, int mx, int my) {
        // Tab pills
        int tx = cx;
        for (int i = 0; i < data.tabs.size(); i++) {
            String lbl = data.tabs.get(i);
            int tw = mc.fontRendererObj.getStringWidth(lbl)+10;
            boolean sel = i==selectedFilterTab;
            boolean hov = mx>=tx && mx<=tx+tw && my>=cy && my<=cy+13;
            Gui.drawRect(tx, cy, tx+tw, cy+13, sel ? C_ACCENT2 : blendColor(C_CARD, C_CARD_H, hov?1f:0f));
            mc.fontRendererObj.drawString(lbl, tx+5, cy+2, sel ? 0xFFFFFFFF : C_TEXT_DIM);
            tx += tw+4;
        }
        cy += 17;

        // Prefix / Suffix
        mc.fontRendererObj.drawString("Prefix", cx,       cy+2, C_TEXT_DIM);
        prefixInput.xPosition = cx+38; prefixInput.yPosition = cy; prefixInput.drawTextBox();
        mc.fontRendererObj.drawString("Suffix", cx+CW/2,  cy+2, C_TEXT_DIM);
        suffixInput.xPosition = cx+CW/2+38; suffixInput.yPosition = cy; suffixInput.drawTextBox();
        cy += 16;

        mc.fontRendererObj.drawString("Include keywords:", cx, cy+2, C_TEXT_DIM);
        cy += 12;
        filterInput.xPosition = cx; filterInput.yPosition = cy; filterInput.drawTextBox();
        cy += 14;

        mc.fontRendererObj.drawString("Exclude keywords:", cx, cy+2, C_TEXT_DIM);
        cy += 12;
        exclusionInput.xPosition = cx; exclusionInput.yPosition = cy; exclusionInput.drawTextBox();
        cy += 16;

        drawSectionHeader(mc, cx, cy, "Message Types");
        cy += 13;

        String[] fl = {"All Messages","Commands","Server Messages","Player Messages","Command Responses"};
        boolean[] fv = {
            data.includeAllFilters.getOrDefault(selectedFilterTab,false),
            data.includeCommandsFilters.getOrDefault(selectedFilterTab,false),
            data.serverMessageFilters.getOrDefault(selectedFilterTab,false),
            data.includePlayersFilters.getOrDefault(selectedFilterTab,false),
            data.includeCommandResponseFilters.getOrDefault(selectedFilterTab,false)
        };
        for (int i = 0; i < fl.length; i++) {
            boolean hov = mx>=cx && mx<=cx+CW && my>=cy && my<=cy+14;
            Gui.drawRect(cx, cy, cx+CW, cy+14, blendColor(C_CARD, C_CARD_H, hov?1f:0f));
            drawTogglePill(cx+CW-22, cy+3, fv[i]);
            mc.fontRendererObj.drawString(fl[i], cx+7, cy+4, C_TEXT);
            cy += 16;
        }
    }

    // -------------------------------------------------------------------------
    // Search page
    // -------------------------------------------------------------------------
    private void drawSearchPage(Minecraft mc, int cx, int cy, int mx, int my) {
        drawSectionHeader(mc, cx, cy, "Chat Log Search");
        cy += 14;

        // Search input box
        int fieldW = CW - 4;
        Gui.drawRect(cx, cy, cx + fieldW, cy + 14, C_CARD);
        drawBorder(cx, cy, cx + fieldW, cy + 14,
                chatSearchField.isFocused() ? C_ACCENT : C_DIVIDER);
        chatSearchField.xPosition = cx + 4;
        chatSearchField.yPosition = cy + 2;
        chatSearchField.width     = fieldW - 8;
        chatSearchField.setEnableBackgroundDrawing(false);
        chatSearchField.drawTextBox();
        if (chatSearchField.getText().isEmpty() && !chatSearchField.isFocused())
            mc.fontRendererObj.drawString("Type to search chat history...", cx + 6, cy + 3, C_TEXT_DIM);
        cy += 16;

        // Rebuild results when dirty — guard against null fields on old messages
        if (searchDirty) {
            searchResults.clear();
            searchScrollX = 0;
            String q = chatSearchText.toLowerCase();
            if (!q.isEmpty()) {
                for (ChatTabData.ChatMessage msg : data.globalLog) {
                    if (msg.isDateSeparator) continue;
                    String plain = msg.plainText != null ? msg.plainText
                                 : (msg.text != null ? msg.text : "");
                    if (plain.toLowerCase().contains(q)) {
                        String date = msg.date != null ? msg.date : "??/??/??";
                        String time = msg.time != null ? msg.time : "??:??";
                        String ts   = "[" + date + " " + time + "] ";
                        String disp = msg.text != null ? data.applyBracketStrip(msg.text) : plain;
                        searchResults.add(ts + disp);
                    }
                }
                java.util.Collections.reverse(searchResults);
            }
            searchDirty = false;
        }

        // Result count label
        String countLbl = chatSearchText.isEmpty() ? "Enter a search term above."
                : searchResults.size() + " result" + (searchResults.size() == 1 ? "" : "s");
        mc.fontRendererObj.drawString(countLbl, cx + 2, cy + 2, C_TEXT_DIM);
        cy += 14;

        if (searchResults.isEmpty()) return;

        // Layout
        ScaledResolution sr  = new ScaledResolution(mc);
        int panelY    = (sr.getScaledHeight() - H) / 2;
        int rowH      = 11;
        int vBarW     = 4;
        int hBarH     = 4;
        int listW     = CW - vBarW - 2;          // width available for text
        int listAreaH = H - (cy - panelY) - hBarH - 10; // leave room for h-bar
        int maxVisible = Math.max(1, listAreaH / rowH);
        int maxScrollY = Math.max(0, searchResults.size() - maxVisible);
        searchScrollY  = Math.max(0, Math.min(maxScrollY, searchScrollY));

        // Measure widest line
        searchMaxLineW = 0;
        for (String r : searchResults) {
            int w = mc.fontRendererObj.getStringWidth(r);
            if (w > searchMaxLineW) searchMaxLineW = w;
        }
        int maxScrollX = Math.max(0, searchMaxLineW + 8 - listW);
        searchScrollX  = Math.max(0, Math.min(maxScrollX, searchScrollX));

        // ── Mouse wheel (vertical) ──
        if (Mouse.hasWheel()) {
            int wheel = Mouse.getDWheel();
            if (wheel != 0) searchScrollY = Math.max(0, Math.min(maxScrollY,
                    searchScrollY + (wheel > 0 ? -1 : 1)));
        }

        // ── Vertical scrollbar ──
        int vBarX = cx + listW + 2;
        if (maxScrollY > 0) {
            int vThH = Math.max(14, listAreaH * maxVisible / Math.max(1, searchResults.size()));
            int vThY = cy + (int)((long)(listAreaH - vThH) * searchScrollY / maxScrollY);
            boolean vHov = mx >= vBarX && mx <= vBarX + vBarW
                    && my >= cy && my <= cy + listAreaH;
            // Start drag
            if (Mouse.isButtonDown(0) && !draggingSearchHBar && (draggingSearchVBar || vHov)) {
                draggingSearchVBar = true;
                float frac = Math.max(0f, Math.min(1f,
                        (float)(my - cy - vThH / 2) / (listAreaH - vThH)));
                searchScrollY = (int)(frac * maxScrollY);
            }
            Gui.drawRect(vBarX, cy, vBarX + vBarW, cy + listAreaH, 0x22FFFFFF);
            Gui.drawRect(vBarX, vThY, vBarX + vBarW, vThY + vThH,
                    (draggingSearchVBar || vHov) ? C_ACCENT : C_ACCENT2);
        }
        if (!Mouse.isButtonDown(0)) draggingSearchVBar = false;

        // ── Horizontal scrollbar ──
        int hBarY = cy + listAreaH + 2;
        if (maxScrollX > 0) {
            int hThW = Math.max(20, listW * listW / Math.max(1, searchMaxLineW + 8));
            int hThX = cx + (int)((long)(listW - hThW) * searchScrollX / maxScrollX);
            boolean hHov = mx >= cx && mx <= cx + listW
                    && my >= hBarY && my <= hBarY + hBarH;
            if (Mouse.isButtonDown(0) && !draggingSearchVBar && (draggingSearchHBar || hHov)) {
                draggingSearchHBar = true;
                float frac = Math.max(0f, Math.min(1f,
                        (float)(mx - cx - hThW / 2) / (listW - hThW)));
                searchScrollX = (int)(frac * maxScrollX);
            }
            Gui.drawRect(cx, hBarY, cx + listW, hBarY + hBarH, 0x22FFFFFF);
            Gui.drawRect(hThX, hBarY, hThX + hThW, hBarY + hBarH,
                    (draggingSearchHBar || hHov) ? C_ACCENT : C_ACCENT2);
        }
        if (!Mouse.isButtonDown(0)) draggingSearchHBar = false;

        // ── Clip and draw rows ──
        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(cx * scale,
                sr.getScaledHeight() * scale - (cy + listAreaH) * scale,
                listW * scale, listAreaH * scale);

        int rowY = cy;
        for (int i = searchScrollY; i < searchResults.size() && rowY < cy + listAreaH; i++) {
            Gui.drawRect(cx, rowY, cx + listW, rowY + rowH,
                    (i % 2 == 0) ? 0x11FFFFFF : 0x00000000);
            mc.fontRendererObj.drawString(searchResults.get(i),
                    cx + 3 - searchScrollX, rowY + 1, C_TEXT);
            rowY += rowH;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // -------------------------------------------------------------------------
    // Mutes page
    // -------------------------------------------------------------------------
    private void drawMutesPage(Minecraft mc, int cx, int cy, int mx, int my) {
        drawSectionHeader(mc, cx, cy, "Muted Players");
        cy += 14;

        // ── Add player row ──
        int addBtnW = 36;
        int fieldW  = CW - addBtnW - 6;
        Gui.drawRect(cx, cy, cx + fieldW, cy + 14, C_CARD);
        drawBorder(cx, cy, cx + fieldW, cy + 14,
                muteAddField.isFocused() ? C_ACCENT : C_DIVIDER);
        muteAddField.xPosition = cx + 4;
        muteAddField.yPosition = cy + 2;
        muteAddField.width     = fieldW - 8;
        muteAddField.setEnableBackgroundDrawing(false);
        muteAddField.drawTextBox();
        if (muteAddField.getText().isEmpty() && !muteAddField.isFocused())
            mc.fontRendererObj.drawString("Player name...", cx + 6, cy + 3, C_TEXT_DIM);

        // "Mute" add button
        int btnX = cx + fieldW + 4;
        boolean addHov = mx >= btnX && mx <= btnX + addBtnW && my >= cy && my <= cy + 14;
        Gui.drawRect(btnX, cy, btnX + addBtnW, cy + 14,
                addHov ? 0xFF1A3A6A : 0xFF112244);
        drawBorder(btnX, cy, btnX + addBtnW, cy + 14,
                addHov ? C_ACCENT : C_ACCENT2);
        int lblW = mc.fontRendererObj.getStringWidth("Mute");
        mc.fontRendererObj.drawString("Mute", btnX + (addBtnW - lblW) / 2, cy + 3,
                addHov ? 0xFFFFFFFF : C_TEXT);
        cy += 18;

        // Clean up expired temp mutes before displaying
        java.util.Iterator<Map.Entry<String, Long>> it = data.mutedPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() != Long.MAX_VALUE && System.currentTimeMillis() >= e.getValue()) it.remove();
        }

        if (data.mutedPlayers.isEmpty()) {
            mc.fontRendererObj.drawString("No muted players.", cx + 4, cy + 4, C_TEXT_DIM);
            return;
        }

        // Convert map to sorted list for stable display
        List<Map.Entry<String, Long>> entries = new java.util.ArrayList<>(data.mutedPlayers.entrySet());
        java.util.Collections.sort(entries, new java.util.Comparator<Map.Entry<String, Long>>() {
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return a.getKey().compareToIgnoreCase(b.getKey());
            }
        });

        // Scrollable list
        ScaledResolution sr  = new ScaledResolution(mc);
        int panelY    = (sr.getScaledHeight() - H) / 2;
        int rowH      = 15;
        int vBarW     = 4;
        int listAreaH = H - (cy - panelY) - 8;
        int maxVis    = Math.max(1, listAreaH / rowH);
        int maxScrollY = Math.max(0, entries.size() - maxVis);
        muteScrollY    = Math.max(0, Math.min(maxScrollY, muteScrollY));

        // Scroll wheel
        if (Mouse.hasWheel()) {
            int wheel = Mouse.getDWheel();
            if (wheel != 0) muteScrollY = Math.max(0, Math.min(maxScrollY,
                    muteScrollY + (wheel > 0 ? -1 : 1)));
        }

        // Vertical scrollbar
        int vBarX = cx + CW - vBarW - 1;
        int listW  = CW - vBarW - 3;
        if (maxScrollY > 0) {
            int vThH = Math.max(14, listAreaH * maxVis / Math.max(1, entries.size()));
            int vThY = cy + (int)((long)(listAreaH - vThH) * muteScrollY / maxScrollY);
            boolean vHov = mx >= vBarX && mx <= vBarX + vBarW && my >= cy && my <= cy + listAreaH;
            if (Mouse.isButtonDown(0) && !draggingMuteVBar && (draggingMuteVBar || vHov)) draggingMuteVBar = true;
            if (draggingMuteVBar && Mouse.isButtonDown(0)) {
                float frac = Math.max(0f, Math.min(1f, (float)(my - cy - vThH / 2) / (listAreaH - vThH)));
                muteScrollY = (int)(frac * maxScrollY);
            }
            if (!Mouse.isButtonDown(0)) draggingMuteVBar = false;
            Gui.drawRect(vBarX, cy, vBarX + vBarW, cy + listAreaH, 0x22FFFFFF);
            Gui.drawRect(vBarX, vThY, vBarX + vBarW, vThY + vThH,
                    (draggingMuteVBar || vHov) ? C_ACCENT : C_ACCENT2);
        }

        // Clip and draw rows
        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(cx * scale,
                sr.getScaledHeight() * scale - (cy + listAreaH) * scale,
                listW * scale, listAreaH * scale);

        int rowY = cy;
        for (int i = muteScrollY; i < entries.size() && rowY < cy + listAreaH; i++) {
            Map.Entry<String, Long> e = entries.get(i);
            String name = e.getKey();
            boolean perma = (e.getValue() == Long.MAX_VALUE);
            String statusStr = perma ? "Permanent"
                    : "Expires in " + formatTimeLeft(e.getValue() - System.currentTimeMillis());

            boolean rowHov = mx >= cx && mx <= cx + listW && my >= rowY && my <= rowY + rowH;
            Gui.drawRect(cx, rowY, cx + listW, rowY + rowH,
                    rowHov ? C_CARD_H : ((i % 2 == 0) ? C_CARD : 0xFF101418));

            mc.fontRendererObj.drawString(name, cx + 5, rowY + 3, C_TEXT);
            mc.fontRendererObj.drawString(statusStr, cx + 5 + mc.fontRendererObj.getStringWidth(name) + 6,
                    rowY + 3, perma ? 0xFFFF6666 : 0xFFFFCC44);

            // Unmute [x] button on right
            int xBtnX = cx + listW - 14;
            boolean xHov = mx >= xBtnX && mx <= xBtnX + 12 && my >= rowY + 1 && my <= rowY + rowH - 1;
            Gui.drawRect(xBtnX, rowY + 2, xBtnX + 12, rowY + rowH - 2,
                    xHov ? 0xFF882222 : 0xFF441111);
            mc.fontRendererObj.drawString("x", xBtnX + 3, rowY + 3, xHov ? 0xFFFF9999 : 0xFFCC6666);

            rowY += rowH;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static String formatTimeLeft(long ms) {
        if (ms <= 0) return "now";
        long s = ms / 1000;
        if (s < 60)   return s + "s";
        long m = s / 60; s %= 60;
        if (m < 60)   return m + "m " + s + "s";
        long h = m / 60; m %= 60;
        return h + "h " + m + "m";
    }

    // -------------------------------------------------------------------------
    // Help page
    // -------------------------------------------------------------------------
    private void drawHelpPage(Minecraft mc, int cx, int cy, int mx, int my) {
        drawSectionHeader(mc, cx, cy, "Controls & Help");
        cy += 14;

        String[][] entries = {
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

        for (String[] e : entries) {
            if (cy + 12 > (new ScaledResolution(mc).getScaledHeight() - H) / 2 + H - 4) break;
            mc.fontRendererObj.drawString("\u2022 " + e[0] + ":", cx + 4, cy + 1, C_ACCENT);
            mc.fontRendererObj.drawString(e[1], cx + mc.fontRendererObj.getStringWidth("\u2022 " + e[0] + ":  ") + 4, cy + 1, C_TEXT);
            cy += 12;
        }
    }

    // -------------------------------------------------------------------------
    // Colour picker overlay
    // -------------------------------------------------------------------------
    private void drawColorPicker(Minecraft mc, ScaledResolution sr, int mx, int my) {
        int px = (sr.getScaledWidth()  - PW) / 2 + 20;
        int py = (sr.getScaledHeight() - PH) / 2;

        // Shadow
        Gui.drawRect(px+3, py+3, px+PW+3, py+PH+3, 0x66000000);
        // Panel
        Gui.drawRect(px, py, px+PW, py+PH, 0xFF13161C);
        Gui.drawRect(px, py, px+PW, py+18, 0xFF0D1015);
        Gui.drawRect(px, py+17, px+PW, py+19, C_ACCENT);
        mc.fontRendererObj.drawString("Edit: "+COLOR_LABELS[editingColorIndex], px+6, py+5, C_TEXT);

        int sbX = px+8, sbY = py+22;

        // SB square
        for (int sy = 0; sy < SB; sy++) {
            for (int sx = 0; sx < SB; sx++) {
                float s=sx/(float)(SB-1), b=1f-sy/(float)(SB-1);
                Gui.drawRect(sbX+sx, sbY+sy, sbX+sx+1, sbY+sy+1, 0xFF000000|hsbToRgb(pickerH,s,b));
            }
        }
        // SB cursor
        int csx = sbX+(int)(pickerS*(SB-1));
        int csy = sbY+(int)((1-pickerB)*(SB-1));
        Gui.drawRect(csx-3,csy-3,csx+3,csy+3,0xFFFFFFFF);
        Gui.drawRect(csx-2,csy-2,csx+2,csy+2,0xFF000000);

        // Hue bar
        int hbX = sbX+SB+6, hbY = sbY;
        for (int hy = 0; hy < HB_H; hy++) {
            Gui.drawRect(hbX, hbY+hy, hbX+HB_W, hbY+hy+1, 0xFF000000|hsbToRgb(hy/(float)(HB_H-1),1,1));
        }
        int htY = hbY+(int)(pickerH*(HB_H-1));
        Gui.drawRect(hbX-2,htY-1,hbX+HB_W+2,htY+2,0xFFFFFFFF);
        Gui.drawRect(hbX-1,htY,  hbX+HB_W+1,htY+1,0xFF000000);

        // Opacity bar
        int obX = hbX+HB_W+6, obY = sbY;
        int curRgb = hsbToRgb(pickerH,pickerS,pickerB);
        for (int oy = 0; oy < HB_H; oy++) {
            int a2=255-(int)((oy/(float)(HB_H-1))*255);
            Gui.drawRect(obX,obY+oy,obX+HB_W,obY+oy+1, ((oy/4)%2==0)?0xFF888888:0xFF444444);
            Gui.drawRect(obX,obY+oy,obX+HB_W,obY+oy+1, (a2<<24)|curRgb);
        }
        int otY = obY+(int)((1-pickerOpacity/255.0)*(HB_H-1));
        Gui.drawRect(obX-2,otY-1,obX+HB_W+2,otY+2,0xFFFFFFFF);
        Gui.drawRect(obX-1,otY,  obX+HB_W+1,otY+1,0xFF000000);

        // Labels above bars
        mc.fontRendererObj.drawString("H", hbX+1, hbY-8, C_TEXT_DIM);
        mc.fontRendererObj.drawString("A", obX+1, obY-8, C_TEXT_DIM);
        mc.fontRendererObj.drawString(""+pickerOpacity, obX, obY+HB_H+2, C_TEXT_DIM);

        // Preview
        int pvX=px+8, pvY=sbY+SB+10;
        mc.fontRendererObj.drawString("Preview", pvX, pvY-8, C_TEXT_DIM);
        Gui.drawRect(pvX,    pvY, pvX+20, pvY+6,  0xFF888888);
        Gui.drawRect(pvX+20, pvY, pvX+40, pvY+6,  0xFF444444);
        Gui.drawRect(pvX,    pvY+6, pvX+20, pvY+12, 0xFF444444);
        Gui.drawRect(pvX+20, pvY+6, pvX+40, pvY+12, 0xFF888888);
        Gui.drawRect(pvX, pvY, pvX+40, pvY+12, (pickerOpacity<<24)|curRgb);
        drawBorder(pvX-1,pvY-1,pvX+41,pvY+13,C_DIVIDER);

        // Hex input
        int hexY = pvY+16;
        mc.fontRendererObj.drawString("#", px+8, hexY+2, C_TEXT_DIM);
        pickerHexField.xPosition = px+18; pickerHexField.yPosition = hexY;
        pickerHexField.drawTextBox();

        // Done / Cancel
        int btnY = py+PH-18;
        boolean dHov = mx>=px+6   && mx<=px+92   && my>=btnY && my<=btnY+14;
        boolean cHov = mx>=px+100 && mx<=px+186  && my>=btnY && my<=btnY+14;
        Gui.drawRect(px+6,   btnY, px+92,  btnY+14, dHov?0xFF1A6644:0xFF114433);
        Gui.drawRect(px+100, btnY, px+186, btnY+14, cHov?0xFF882222:0xFF441111);
        drawBorder(px+6,   btnY, px+92,  btnY+14, dHov?0xFF22CC77:0xFF228855);
        drawBorder(px+100, btnY, px+186, btnY+14, cHov?0xFFCC4444:0xFF882222);
        mc.fontRendererObj.drawString("Done",   px+6+24,  btnY+3, dHov?0xFF44FFAA:0xFF22CC77);
        mc.fontRendererObj.drawString("Cancel", px+100+15,btnY+3, cHov?0xFFFF7777:0xFFCC4444);

        // Live drag
        if (Mouse.isButtonDown(0)) {
            if (draggingHueBar || (mx>=hbX-2 && mx<=hbX+HB_W+2 && my>=hbY && my<=hbY+HB_H)) {
                draggingHueBar = true;
                pickerH = Math.max(0,Math.min(1,(my-hbY)/(float)(HB_H-1)));
                syncPickerToHex();
            }
            if (!draggingHueBar && (draggingSBSquare || (mx>=sbX && mx<=sbX+SB && my>=sbY && my<=sbY+SB))) {
                draggingSBSquare = true;
                pickerS = Math.max(0,Math.min(1,(mx-sbX)/(float)(SB-1)));
                pickerB = Math.max(0,Math.min(1,1-(my-sbY)/(float)(SB-1)));
                syncPickerToHex();
            }
            if (!draggingHueBar && !draggingSBSquare &&
                (draggingOpSlider || (mx>=obX-2 && mx<=obX+HB_W+2 && my>=obY && my<=obY+HB_H))) {
                draggingOpSlider = true;
                pickerOpacity = 255-(int)(Math.max(0,Math.min(1,(my-obY)/(float)(HB_H-1)))*255);
            }
        } else {
            draggingHueBar=false; draggingSBSquare=false; draggingOpSlider=false;
        }
    }

    // -------------------------------------------------------------------------
    // Draw primitives
    // -------------------------------------------------------------------------
    private static void drawBorder(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1,   y1,   x2,   y1+1, color);
        Gui.drawRect(x1,   y2-1, x2,   y2,   color);
        Gui.drawRect(x1,   y1,   x1+1, y2,   color);
        Gui.drawRect(x2-1, y1,   x2,   y2,   color);
    }

    // -------------------------------------------------------------------------
    // Font sub-card click helper  (slot: 0=text, 1=tabs, 2=timestamps)
    // -------------------------------------------------------------------------
    private int handleFontSubCardClick(int mx, int my, int btn, int cx, int virtualCy, int slot) {
        int indX = cx + 6, indW = CW - 6;
        boolean enabled  = slot==0?data.fontEnabled    : slot==1?data.fontTabsEnabled    : data.fontTimestampsEnabled;
        boolean dropOpen = slot==0?fontDropdownOpen    : slot==1?fontTabsDropdownOpen    : fontTimeDropdownOpen;
        GuiTextField sf  = slot==0?fontSearchField     : slot==1?fontTabsSearchField     : fontTimeSearchField;
        String searchTxt = slot==0?fontSearchText      : slot==1?fontTabsSearchText      : fontTimeSearchText;

        // Row 1: enable toggle (14px) + gap (2px)
        if (btn==0 && mx>=indX+2 && mx<=indX+indW && my>=virtualCy && my<=virtualCy+14) {
            if (slot==0) {
                data.fontEnabled = !data.fontEnabled;
                if (!data.fontEnabled) {
                    data.fontName = ""; data.fontSize = 1.0f;
                    fontDropdownOpen = false;
                    fontSearchField.setText(""); fontSearchText = "";
                }
            } else if (slot==1) {
                data.fontTabsEnabled = !data.fontTabsEnabled;
                if (!data.fontTabsEnabled) {
                    data.fontNameTabs = ""; data.fontSizeTabs = 1.0f;
                    fontTabsDropdownOpen = false;
                    fontTabsSearchField.setText(""); fontTabsSearchText = "";
                }
            } else {
                data.fontTimestampsEnabled = !data.fontTimestampsEnabled;
                if (!data.fontTimestampsEnabled) {
                    data.fontNameTimestamps = ""; data.fontSizeTimestamps = 1.0f;
                    fontTimeDropdownOpen = false;
                    fontTimeSearchField.setText(""); fontTimeSearchText = "";
                }
            }
            AwtFontRenderer.clearCache(); data.filterVersion++; data.save();
            return virtualCy + 14 + 2;
        }
        virtualCy += 14 + 2;

        // Only process size/font rows if enabled
        if (!enabled) return virtualCy + 1;

        // Row 2: size slider (14px)
        int sx = indX + 28, sw2 = indW - 52;
        if (btn==0 && mx>=sx && mx<=sx+sw2 && my>=virtualCy && my<=virtualCy+14) {
            draggingFontSize=true; draggingFontSlot=slot;
            float np = Math.max(0f, Math.min(1f, (float)(mx-sx)/sw2));
            setFontSize(slot, Math.round((0.5f+np*2.5f)*10)/10.0f);
            data.filterVersion++; data.save();
        }
        if (!Mouse.isButtonDown(0) && draggingFontSlot==slot) { draggingFontSize=false; draggingFontSlot=-1; }
        virtualCy += 14;

        // Row 3: font picker dropdown header (12px clickable)
        if (btn==0 && mx>=indX+2 && mx<=indX+indW && my>=virtualCy && my<=virtualCy+12) {
            if(slot==0){ fontDropdownOpen=!fontDropdownOpen; if(fontDropdownOpen) fontSearchField.setFocused(true); }
            else if(slot==1){ fontTabsDropdownOpen=!fontTabsDropdownOpen; if(fontTabsDropdownOpen) fontTabsSearchField.setFocused(true); }
            else { fontTimeDropdownOpen=!fontTimeDropdownOpen; if(fontTimeDropdownOpen) fontTimeSearchField.setFocused(true); }
            return virtualCy + 14 + (dropOpen ? 13 + 14 + FONT_DROPDOWN_VISIBLE * 12 : 0) + 1;
        }

        if (dropOpen) {
            List<String> allFonts = com.betterchat.ChatRenderer.getSystemFonts();
            List<String> fonts = new java.util.ArrayList<>();
            for (String f : allFonts)
                if (searchTxt.isEmpty() || f.toLowerCase().contains(searchTxt.toLowerCase()))
                    fonts.add(f);
            int clampedScroll = slot==0?fontDropdownScroll : slot==1?fontTabsDropdownScroll : fontTimeDropdownScroll;
            clampedScroll = Math.min(clampedScroll, Math.max(0, fonts.size()-FONT_DROPDOWN_VISIBLE));

            int searchY = virtualCy + 13;
            if (mx>=indX+2 && mx<=indX+indW && my>=searchY && my<=searchY+13) {
                sf.mouseClicked(mx, my, btn); sf.setFocused(true);
                return virtualCy + 14 + 13 + 14 + FONT_DROPDOWN_VISIBLE * 12 + 1;
            }
            int listY = searchY + 14;
            for (int i=0; i<FONT_DROPDOWN_VISIBLE && clampedScroll+i<fonts.size(); i++) {
                if (btn==0 && mx>=indX+2 && mx<=indX+indW && my>=listY && my<=listY+11) {
                    String chosen = fonts.get(clampedScroll+i);
                    if(slot==0){ data.fontName=chosen; fontDropdownOpen=false; fontSearchField.setText(""); fontSearchText=""; }
                    else if(slot==1){ data.fontNameTabs=chosen; fontTabsDropdownOpen=false; fontTabsSearchField.setText(""); fontTabsSearchText=""; }
                    else { data.fontNameTimestamps=chosen; fontTimeDropdownOpen=false; fontTimeSearchField.setText(""); fontTimeSearchText=""; }
                    AwtFontRenderer.clearCache(); data.filterVersion++; data.save();
                    return virtualCy + 14 + 13 + 14 + FONT_DROPDOWN_VISIBLE * 12 + 1;
                }
                listY += 12;
            }
        }
        return virtualCy + 14 + (dropOpen ? 13 + 14 + FONT_DROPDOWN_VISIBLE * 12 : 0) + 1;
    }

    // -------------------------------------------------------------------------
    // Mouse click
    // -------------------------------------------------------------------------
    public void mouseClicked(int mx, int my, int btn) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = (sr.getScaledWidth()  - W) / 2;
        int y = (sr.getScaledHeight() - H) / 2;

        // Color picker intercept
        if (editingColorIndex != -1) {
            int px = (sr.getScaledWidth()  - PW) / 2 + 20;
            int py = (sr.getScaledHeight() - PH) / 2;
            pickerHexField.mouseClicked(mx, my, btn);
            int btnY = py+PH-18;
            if (btn==0 && mx>=px+6 && mx<=px+92 && my>=btnY && my<=btnY+14) {
                applyPickerFromHex();
                applyColorAndOpac(editingColorIndex, hsbToRgb(pickerH,pickerS,pickerB), pickerOpacity);
                editingColorIndex=-1; data.save(); return;
            }
            if (btn==0 && mx>=px+100 && mx<=px+186 && my>=btnY && my<=btnY+14) {
                editingColorIndex=-1; return;
            }
            return;
        }

        // Close button
        if (btn==0 && mx>=x+W-18 && mx<=x+W-4 && my>=y+5 && my<=y+21) {
            closeRequested = true; return;
        }

        // Sidebar nav (4 pages)
        for (int i=0;i<PAGE_NAMES.length;i++) {
            int ny = y+38+i*28;
            if (btn==0 && mx>=x+4 && mx<=x+SW-4 && my>=ny && my<=ny+20) {
                currentPage=i; return;
            }
        }

        // Donate button
        int dbY = y + H - 22;
        if (btn==0 && mx>=x+6 && mx<=x+SW-6 && my>=dbY && my<=dbY+14) {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://ko-fi.com/danielromanovski"));
            } catch (Exception ignored) {}
            return;
        }

        int cx = x+SW+8;
        int cy = y+30;

        if (currentPage == 0) {
            int virtualCy = y + 30 - settingsScrollY;

            virtualCy += 13; // Colors header
            for (int i = 0; i < 8; i++) {
                int sw2 = cx + CW - 44;
                if (btn==0 && mx>=sw2-1 && mx<=sw2+21 && my>=virtualCy+2 && my<=virtualCy+14) {
                    openPicker(i); return;
                }
                virtualCy += 20;
            }
            virtualCy += 5 + 13; // gap + Options header
            for (int i = 0; i < 5; i++) {
                if (btn==0 && mx>=cx && mx<=cx+CW && my>=virtualCy && my<=virtualCy+14) {
                    switch(i){
                        case 0: data.hideDefaultChat=!data.hideDefaultChat; break;
                        case 1: data.saveChatLog=!data.saveChatLog; break;
                        case 2:
                            data.isLocked=!data.isLocked;
                            if(data.isLocked){ data.lockedX=data.windowX; data.lockedY=data.windowY; data.lockedW=data.windowWidth; data.lockedH=data.windowHeight; data.lockedResW=sr.getScaledWidth(); data.lockedResH=sr.getScaledHeight(); }
                            break;
                        case 3: data.showTimeStamps=!data.showTimeStamps; data.filterVersion++; break;
                        case 4: data.showNotifications=!data.showNotifications; break;
                    }
                    data.save(); return;
                }
                virtualCy += 16;
            }
            virtualCy += 5 + 13; // gap + Chat Display header

            // Combine Repeated Messages
            if (btn==0 && mx>=cx && mx<=cx+CW && my>=virtualCy && my<=virtualCy+14) {
                data.messageCombining=!data.messageCombining; data.filterVersion++; data.save(); return;
            }
            virtualCy += 16;

            // Strip Player Brackets
            if (btn==0 && mx>=cx && mx<=cx+CW && my>=virtualCy && my<=virtualCy+14) {
                data.stripPlayerBrackets=!data.stripPlayerBrackets; data.filterVersion++; data.save(); return;
            }
            virtualCy += 16;

            // Master Custom Fonts toggle
            if (btn==0 && mx>=cx && mx<=cx+CW && my>=virtualCy && my<=virtualCy+14) {
                data.fontSizeEnabled = !data.fontSizeEnabled;
                // When turning the master switch OFF, fully reset all sub-categories to defaults
                if (!data.fontSizeEnabled) {
                    data.fontEnabled           = false;
                    data.fontName              = "";
                    data.fontSize              = 1.0f;
                    data.fontTabsEnabled       = false;
                    data.fontNameTabs          = "";
                    data.fontSizeTabs          = 1.0f;
                    data.fontTimestampsEnabled = false;
                    data.fontNameTimestamps    = "";
                    data.fontSizeTimestamps    = 1.0f;
                    fontDropdownOpen           = false;
                    fontTabsDropdownOpen       = false;
                    fontTimeDropdownOpen       = false;
                    fontSearchField.setText(""); fontSearchText = "";
                    fontTabsSearchField.setText(""); fontTabsSearchText = "";
                    fontTimeSearchField.setText(""); fontTimeSearchText = "";
                    AwtFontRenderer.clearCache();
                }
                data.filterVersion++; data.save(); return;
            }
            virtualCy += 16;

            if (data.fontSizeEnabled) {
                // 3 sub-cards
                virtualCy = handleFontSubCardClick(mx, my, btn, cx, virtualCy, 0);
                virtualCy = handleFontSubCardClick(mx, my, btn, cx, virtualCy, 1);
                virtualCy = handleFontSubCardClick(mx, my, btn, cx, virtualCy, 2);
                virtualCy += 4;
            }

            virtualCy += 5;
            if (btn==0 && mx>=cx && mx<=cx+CW && my>=virtualCy && my<=virtualCy+16) {
                data.resetToDefaults(); data.save(); return;
            }
        } else if (currentPage == 1) {
            int tx = cx;
            for (int i=0;i<data.tabs.size();i++) {
                int tw = mc.fontRendererObj.getStringWidth(data.tabs.get(i))+10;
                if (btn==0 && mx>=tx && mx<=tx+tw && my>=cy && my<=cy+13) {
                    selectedFilterTab=i; updateFilterPage(); data.save(); return;
                }
                tx += tw+4;
            }
            cy += 17;
            prefixInput.mouseClicked(mx,my,btn);
            suffixInput.mouseClicked(mx,my,btn);
            cy += 16+12;
            filterInput.mouseClicked(mx,my,btn);
            cy += 14+12;
            exclusionInput.mouseClicked(mx,my,btn);
            cy += 16+13;

            @SuppressWarnings("unchecked")
            java.util.Map<Integer,Boolean>[] maps = new java.util.Map[]{
                data.includeAllFilters, data.includeCommandsFilters,
                data.serverMessageFilters, data.includePlayersFilters,
                data.includeCommandResponseFilters
            };
            for (int i=0;i<5;i++) {
                if (btn==0 && mx>=cx && mx<=cx+CW && my>=cy && my<=cy+14) {
                    maps[i].put(selectedFilterTab, !maps[i].getOrDefault(selectedFilterTab,false));
                    data.filterVersion++; data.save(); return;
                }
                cy += 16;
            }
        } else if (currentPage == 2) {
            chatSearchField.mouseClicked(mx, my, btn);
        } else if (currentPage == 3) {
            // Mutes page
            int muteCy = cy + 14; // after section header

            // muteAddField click
            muteAddField.mouseClicked(mx, my, btn);

            // "Mute" add button
            int addBtnW = 36;
            int fieldW2 = CW - addBtnW - 6;
            int btnX2   = cx + fieldW2 + 4;
            if (btn == 0 && mx >= btnX2 && mx <= btnX2 + addBtnW
                    && my >= muteCy && my <= muteCy + 14) {
                String name = muteAddField.getText().trim();
                if (!name.isEmpty()) {
                    data.mutedPlayers.put(name, Long.MAX_VALUE);
                    data.filterVersion++;
                    muteAddField.setText("");
                    data.save();
                }
                return;
            }
            muteCy += 18;

            // Unmute [x] buttons in the list
            List<Map.Entry<String, Long>> entries =
                    new java.util.ArrayList<>(data.mutedPlayers.entrySet());
            java.util.Collections.sort(entries, new java.util.Comparator<Map.Entry<String, Long>>() {
                public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                    return a.getKey().compareToIgnoreCase(b.getKey());
                }
            });
            ScaledResolution srM = new ScaledResolution(Minecraft.getMinecraft());
            int panelYM  = (srM.getScaledHeight() - H) / 2;
            int rowHM    = 15;
            int vBarWM   = 4;
            int listW2   = CW - vBarWM - 3;
            int listAreaHM = H - (muteCy - panelYM) - 8;
            int maxVisM  = Math.max(1, listAreaHM / rowHM);
            int rowYM    = muteCy;
            for (int i = muteScrollY; i < entries.size() && rowYM < muteCy + listAreaHM; i++) {
                int xBtnX = cx + listW2 - 14;
                if (btn == 0 && mx >= xBtnX && mx <= xBtnX + 12
                        && my >= rowYM + 1 && my <= rowYM + rowHM - 1) {
                    data.mutedPlayers.remove(entries.get(i).getKey());
                    data.filterVersion++;
                    data.save(); return;
                }
                rowYM += rowHM;
            }
        }
        // Help page (currentPage==4) has no interactive elements
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
        // Font search bars (Appearance page, when dropdowns are open)
        if (currentPage == 0) {
            if (fontDropdownOpen && fontSearchField.isFocused()) {
                fontSearchField.textboxKeyTyped(c, code);
                fontSearchText = fontSearchField.getText(); fontDropdownScroll = 0; return;
            }
            if (fontTabsDropdownOpen && fontTabsSearchField.isFocused()) {
                fontTabsSearchField.textboxKeyTyped(c, code);
                fontTabsSearchText = fontTabsSearchField.getText(); fontTabsDropdownScroll = 0; return;
            }
            if (fontTimeDropdownOpen && fontTimeSearchField.isFocused()) {
                fontTimeSearchField.textboxKeyTyped(c, code);
                fontTimeSearchText = fontTimeSearchField.getText(); fontTimeDropdownScroll = 0; return;
            }
        }
        // Mutes page
        if (currentPage == 3 && muteAddField.isFocused()) {
            muteAddField.textboxKeyTyped(c, code);
            // Enter key adds the mute
            if (code == 28 /*KEY_RETURN*/) {
                String name = muteAddField.getText().trim();
                if (!name.isEmpty()) {
                    data.mutedPlayers.put(name, Long.MAX_VALUE);
                    data.filterVersion++;
                    muteAddField.setText("");
                    data.save();
                }
            }
            return;
        }
        // Search page
        if (currentPage == 2 && chatSearchField.isFocused()) {
            chatSearchField.textboxKeyTyped(c, code);
            String newText = chatSearchField.getText();
            if (!newText.equals(chatSearchText)) {
                chatSearchText = newText;
                searchScrollY  = 0;
                searchDirty    = true;
            }
            return;
        }
        if (currentPage == 1) {
            if (selectedFilterTab >= data.tabs.size()) selectedFilterTab=0;
            if (filterInput.isFocused())    filterInput.textboxKeyTyped(c, code);
            if (exclusionInput.isFocused()) exclusionInput.textboxKeyTyped(c, code);
            if (prefixInput.isFocused())    prefixInput.textboxKeyTyped(c, code);
            if (suffixInput.isFocused())    suffixInput.textboxKeyTyped(c, code);
            String of=data.tabFilters.getOrDefault(selectedFilterTab,"");
            String oe=data.tabExclusions.getOrDefault(selectedFilterTab,"");
            data.tabFilters.put(selectedFilterTab,    filterInput.getText());
            data.tabExclusions.put(selectedFilterTab, exclusionInput.getText());
            data.tabPrefixes.put(selectedFilterTab,   prefixInput.getText());
            data.tabSuffixes.put(selectedFilterTab,   suffixInput.getText());
            if (!of.equals(filterInput.getText())||!oe.equals(exclusionInput.getText()))
                data.filterVersion++;
        }
        data.save();
    }
}
