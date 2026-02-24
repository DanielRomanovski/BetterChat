package com.betterchat.settings;

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
 * Renders the Search page and handles its mouse clicks.
 *
 * Searches {@link ChatTabData#globalLog} and displays matches in a scrollable,
 * horizontally-scrollable list with both a vertical and horizontal scrollbar.
 */
public class SearchPage {

    private final ChatTabData data;

    public final GuiTextField chatSearchField;

    private String       chatSearchText    = "";
    private int          searchScrollY     = 0;
    private int          searchScrollX     = 0;
    private int          searchMaxLineW    = 0;
    private boolean      draggingSearchVBar = false;
    private boolean      draggingSearchHBar = false;
    private List<String> searchResults     = new java.util.ArrayList<>();
    private boolean      searchDirty       = true;

    public SearchPage(ChatTabData data) {
        this.data = data;
        Minecraft mc = Minecraft.getMinecraft();
        chatSearchField = new GuiTextField(13, mc.fontRendererObj, 0, 0, 200, 10);
        chatSearchField.setMaxStringLength(128);
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    public void draw(Minecraft mc, int cx, int cy, int mx, int my) {
        drawSectionHeader(mc, cx, cy, "Chat Log Search");
        cy += 14;

        // Search input
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

        // Rebuild results when dirty
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

        String countLbl = chatSearchText.isEmpty() ? "Enter a search term above."
                : searchResults.size() + " result" + (searchResults.size() == 1 ? "" : "s");
        mc.fontRendererObj.drawString(countLbl, cx + 2, cy + 2, C_TEXT_DIM);
        cy += 14;

        if (searchResults.isEmpty()) return;

        ScaledResolution sr    = new ScaledResolution(mc);
        int panelY    = (sr.getScaledHeight() - H) / 2;
        int rowH      = 11;
        int vBarW     = 4;
        int hBarH     = 4;
        int listW     = CW - vBarW - 2;
        int listAreaH = H - (cy - panelY) - hBarH - 10;
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

        // Mouse-wheel (vertical)
        if (Mouse.hasWheel()) {
            int wheel = Mouse.getDWheel();
            if (wheel != 0)
                searchScrollY = Math.max(0, Math.min(maxScrollY,
                        searchScrollY + (wheel > 0 ? -1 : 1)));
        }

        // Vertical scrollbar
        int vBarX = cx + listW + 2;
        if (maxScrollY > 0) {
            int vThH = Math.max(14, listAreaH * maxVisible / Math.max(1, searchResults.size()));
            int vThY = cy + (int)((long)(listAreaH - vThH) * searchScrollY / maxScrollY);
            boolean vHov = mx >= vBarX && mx <= vBarX + vBarW
                    && my >= cy && my <= cy + listAreaH;
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

        // Horizontal scrollbar
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

        // Clip and draw rows
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

    // ── Mouse click ───────────────────────────────────────────────────────────

    public void mouseClicked(int mx, int my, int btn) {
        chatSearchField.mouseClicked(mx, my, btn);
    }

    // ── Key handling ──────────────────────────────────────────────────────────

    public void keyTyped(char c, int code) {
        if (!chatSearchField.isFocused()) return;
        chatSearchField.textboxKeyTyped(c, code);
        String newText = chatSearchField.getText();
        if (!newText.equals(chatSearchText)) {
            chatSearchText = newText;
            searchScrollY  = 0;
            searchDirty    = true;
        }
    }

    /** Forces a re-search on the next draw tick (call when new messages arrive). */
    public void markDirty() { searchDirty = true; }
}

