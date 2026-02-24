package com.betterchat.settings;

import com.betterchat.ChatTabData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

import static com.betterchat.settings.ColorUtils.*;
import static com.betterchat.settings.SettingsConstants.*;

/**
 * Renders the Mutes page and handles its mouse/keyboard interaction.
 *
 * Displays the muted-player list from {@link ChatTabData#mutedPlayers} with
 * permanent vs. timed-expiry indicators and an "Unmute" button per row.
 */
public class MutesPage {

    private final ChatTabData data;

    public final GuiTextField muteAddField;

    private int     muteScrollY      = 0;
    private boolean draggingMuteVBar = false;

    public MutesPage(ChatTabData data) {
        this.data = data;
        Minecraft mc = Minecraft.getMinecraft();
        muteAddField = new GuiTextField(14, mc.fontRendererObj, 0, 0, 160, 10);
        muteAddField.setMaxStringLength(40);
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    public void draw(Minecraft mc, int cx, int cy, int mx, int my) {
        drawSectionHeader(mc, cx, cy, "Muted Players");
        cy += 14;

        // Add-player row
        int addBtnW = 36;
        int fieldW  = CW - addBtnW - 6;
        Gui.drawRect(cx, cy, cx + fieldW, cy + 14, C_CARD);
        drawBorder(cx, cy, cx + fieldW, cy + 14,
                muteAddField.isFocused() ? C_ACCENT : C_DIVIDER);
        muteAddField.xPosition = cx + 4; muteAddField.yPosition = cy + 2;
        muteAddField.width     = fieldW - 8;
        muteAddField.setEnableBackgroundDrawing(false);
        muteAddField.drawTextBox();
        if (muteAddField.getText().isEmpty() && !muteAddField.isFocused())
            mc.fontRendererObj.drawString("Player name...", cx + 6, cy + 3, C_TEXT_DIM);

        int btnX = cx + fieldW + 4;
        boolean addHov = mx >= btnX && mx <= btnX + addBtnW && my >= cy && my <= cy + 14;
        Gui.drawRect(btnX, cy, btnX + addBtnW, cy + 14, addHov ? 0xFF1A3A6A : 0xFF112244);
        drawBorder(btnX, cy, btnX + addBtnW, cy + 14, addHov ? C_ACCENT : C_ACCENT2);
        int lblW = mc.fontRendererObj.getStringWidth("Mute");
        mc.fontRendererObj.drawString("Mute", btnX + (addBtnW - lblW) / 2, cy + 3,
                addHov ? 0xFFFFFFFF : C_TEXT);
        cy += 18;

        // Remove expired temp-mutes
        java.util.Iterator<Map.Entry<String, Long>> it = data.mutedPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() != Long.MAX_VALUE && System.currentTimeMillis() >= e.getValue()) it.remove();
        }

        if (data.mutedPlayers.isEmpty()) {
            mc.fontRendererObj.drawString("No muted players.", cx + 4, cy + 4, C_TEXT_DIM);
            return;
        }

        List<Map.Entry<String, Long>> entries = sortedEntries();

        ScaledResolution sr    = new ScaledResolution(mc);
        int panelY    = (sr.getScaledHeight() - H) / 2;
        int rowH      = 15;
        int vBarW     = 4;
        int listAreaH = H - (cy - panelY) - 8;
        int maxVis    = Math.max(1, listAreaH / rowH);
        int maxScrollY = Math.max(0, entries.size() - maxVis);
        muteScrollY    = Math.max(0, Math.min(maxScrollY, muteScrollY));

        if (Mouse.hasWheel()) {
            int wheel = Mouse.getDWheel();
            if (wheel != 0)
                muteScrollY = Math.max(0, Math.min(maxScrollY,
                        muteScrollY + (wheel > 0 ? -1 : 1)));
        }

        // Vertical scrollbar
        int vBarX = cx + CW - vBarW - 1;
        int listW  = CW - vBarW - 3;
        if (maxScrollY > 0) {
            int vThH = Math.max(14, listAreaH * maxVis / Math.max(1, entries.size()));
            int vThY = cy + (int)((long)(listAreaH - vThH) * muteScrollY / maxScrollY);
            boolean vHov = mx >= vBarX && mx <= vBarX + vBarW && my >= cy && my <= cy + listAreaH;
            if (Mouse.isButtonDown(0) && (draggingMuteVBar || vHov)) {
                draggingMuteVBar = true;
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
            Map.Entry<String, Long> e  = entries.get(i);
            String name  = e.getKey();
            boolean perma = (e.getValue() == Long.MAX_VALUE);
            String status = perma ? "Permanent"
                    : "Expires in " + formatTimeLeft(e.getValue() - System.currentTimeMillis());

            boolean rowHov = mx >= cx && mx <= cx + listW && my >= rowY && my <= rowY + rowH;
            Gui.drawRect(cx, rowY, cx + listW, rowY + rowH,
                    rowHov ? C_CARD_H : ((i % 2 == 0) ? C_CARD : 0xFF101418));
            mc.fontRendererObj.drawString(name, cx + 5, rowY + 3, C_TEXT);
            mc.fontRendererObj.drawString(status,
                    cx + 5 + mc.fontRendererObj.getStringWidth(name) + 6,
                    rowY + 3, perma ? 0xFFFF6666 : 0xFFFFCC44);

            int xBtnX = cx + listW - 14;
            boolean xHov = mx >= xBtnX && mx <= xBtnX + 12 && my >= rowY + 1 && my <= rowY + rowH - 1;
            Gui.drawRect(xBtnX, rowY + 2, xBtnX + 12, rowY + rowH - 2,
                    xHov ? 0xFF882222 : 0xFF441111);
            mc.fontRendererObj.drawString("x", xBtnX + 3, rowY + 3,
                    xHov ? 0xFFFF9999 : 0xFFCC6666);

            rowY += rowH;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ── Mouse click ───────────────────────────────────────────────────────────

    public void mouseClicked(int mx, int my, int btn, int cx, int cy) {
        cy += 14; // after header

        muteAddField.mouseClicked(mx, my, btn);

        int addBtnW = 36;
        int fieldW  = CW - addBtnW - 6;
        int btnX    = cx + fieldW + 4;
        if (btn == 0 && mx >= btnX && mx <= btnX + addBtnW && my >= cy && my <= cy + 14) {
            String name = muteAddField.getText().trim();
            if (!name.isEmpty()) {
                data.mutedPlayers.put(name, Long.MAX_VALUE);
                data.filterVersion++;
                muteAddField.setText("");
                data.save();
            }
            return;
        }
        cy += 18;

        List<Map.Entry<String, Long>> entries = sortedEntries();
        ScaledResolution sr   = new ScaledResolution(Minecraft.getMinecraft());
        int panelY    = (sr.getScaledHeight() - H) / 2;
        int rowH      = 15;
        int vBarW     = 4;
        int listW     = CW - vBarW - 3;
        int listAreaH = H - (cy - panelY) - 8;
        int rowY      = cy;
        for (int i = muteScrollY; i < entries.size() && rowY < cy + listAreaH; i++) {
            int xBtnX = cx + listW - 14;
            if (btn == 0 && mx >= xBtnX && mx <= xBtnX + 12
                    && my >= rowY + 1 && my <= rowY + rowH - 1) {
                data.mutedPlayers.remove(entries.get(i).getKey());
                data.filterVersion++;
                data.save(); return;
            }
            rowY += rowH;
        }
    }

    // ── Key handling ──────────────────────────────────────────────────────────

    public void keyTyped(char c, int code) {
        if (!muteAddField.isFocused()) return;
        muteAddField.textboxKeyTyped(c, code);
        if (code == 28 /* ENTER */) {
            String name = muteAddField.getText().trim();
            if (!name.isEmpty()) {
                data.mutedPlayers.put(name, Long.MAX_VALUE);
                data.filterVersion++;
                muteAddField.setText("");
                data.save();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Map.Entry<String, Long>> sortedEntries() {
        List<Map.Entry<String, Long>> list = new java.util.ArrayList<>(data.mutedPlayers.entrySet());
        java.util.Collections.sort(list, new java.util.Comparator<Map.Entry<String, Long>>() {
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return a.getKey().compareToIgnoreCase(b.getKey());
            }
        });
        return list;
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
}

