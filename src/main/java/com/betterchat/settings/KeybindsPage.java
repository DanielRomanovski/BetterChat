package com.betterchat.settings;

import com.betterchat.ChatTabData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.util.List;

import static com.betterchat.settings.ColorUtils.*;
import static com.betterchat.settings.SettingsConstants.*;

/**
 * Renders the Keybinds page (key bindings + auto-responses) and handles
 * all mouse clicks and keyboard input for that page.
 */
public class KeybindsPage {

    private final ChatTabData data;

    // Input fields
    public final GuiTextField kbMessageField;
    public final GuiTextField kbLabelField;
    public final GuiTextField arTriggerField;
    public final GuiTextField arResponseField;

    // Recording state
    private boolean recordingKeybind = false;
    private final List<Integer> pendingKeybindCodes = new java.util.ArrayList<>();

    // Scroll offsets
    private int kbScrollY = 0;
    private int arScrollY = 0;

    public KeybindsPage(ChatTabData data) {
        this.data = data;
        Minecraft mc = Minecraft.getMinecraft();
        kbMessageField  = new GuiTextField(20, mc.fontRendererObj, 0, 0, 160, 10);
        kbMessageField.setMaxStringLength(256);
        kbLabelField    = new GuiTextField(21, mc.fontRendererObj, 0, 0, 100, 10);
        kbLabelField.setMaxStringLength(40);
        arTriggerField  = new GuiTextField(22, mc.fontRendererObj, 0, 0, 120, 10);
        arTriggerField.setMaxStringLength(128);
        arResponseField = new GuiTextField(23, mc.fontRendererObj, 0, 0, 120, 10);
        arResponseField.setMaxStringLength(256);
    }

    /** Returns true while the mod should swallow all key presses for recording. */
    public boolean isRecordingKeybind() { return recordingKeybind; }

    // ── Drawing ───────────────────────────────────────────────────────────────

    public void draw(Minecraft mc, int cx, int cy, int mx, int my) {
        ScaledResolution sr    = new ScaledResolution(mc);
        int panelY = (sr.getScaledHeight() - H) / 2;

        // ── KEYBINDS ─────────────────────────────────────────────────────────
        drawSectionHeader(mc, cx, cy, "Keybinds");
        cy += 13;
        mc.fontRendererObj.drawString("Bind keyboard keys to send a message.", cx + 2, cy, C_TEXT_DIM);
        cy += 14;

        int halfFW = CW / 2 - 2;
        mc.fontRendererObj.drawString("Label", cx + 2, cy, C_TEXT_DIM);
        int mfX = cx + halfFW + 4;
        mc.fontRendererObj.drawString("Message / Command", mfX + 2, cy, C_TEXT_DIM);
        cy += 10;

        Gui.drawRect(cx, cy, cx + halfFW, cy + 14, C_CARD);
        drawBorder(cx, cy, cx + halfFW, cy + 14, kbLabelField.isFocused() ? C_ACCENT : C_DIVIDER);
        kbLabelField.xPosition = cx + 3; kbLabelField.yPosition = cy + 2;
        kbLabelField.width = halfFW - 6; kbLabelField.setEnableBackgroundDrawing(false);
        kbLabelField.drawTextBox();
        if (kbLabelField.getText().isEmpty() && !kbLabelField.isFocused())
            mc.fontRendererObj.drawString("Label...", cx + 4, cy + 3, C_TEXT_DIM);

        Gui.drawRect(mfX, cy, mfX + halfFW, cy + 14, C_CARD);
        drawBorder(mfX, cy, mfX + halfFW, cy + 14, kbMessageField.isFocused() ? C_ACCENT : C_DIVIDER);
        kbMessageField.xPosition = mfX + 3; kbMessageField.yPosition = cy + 2;
        kbMessageField.width = halfFW - 6; kbMessageField.setEnableBackgroundDrawing(false);
        kbMessageField.drawTextBox();
        if (kbMessageField.getText().isEmpty() && !kbMessageField.isFocused())
            mc.fontRendererObj.drawString("Message/cmd...", mfX + 4, cy + 3, C_TEXT_DIM);
        cy += 18;

        // Record-key button
        int recW = CW / 2 - 2;
        boolean recHov = !recordingKeybind && mx >= cx && mx <= cx + recW && my >= cy && my <= cy + 14;
        int recColor = recordingKeybind ? 0xFF5A1010 : (recHov ? 0xFF1A3A6A : 0xFF112244);
        Gui.drawRect(cx, cy, cx + recW, cy + 14, recColor);
        drawBorder(cx, cy, cx + recW, cy + 14, recordingKeybind ? 0xFFFF4444 : C_ACCENT2);
        String comboStr = pendingKeybindCodes.isEmpty() ? "" : buildComboString();
        String recLbl;
        if (recordingKeybind) {
            recLbl = pendingKeybindCodes.isEmpty() ? "Recording... (press keys)" : comboStr + " \u2014 click again to confirm";
        } else {
            recLbl = pendingKeybindCodes.isEmpty() ? "Record Key Combo" : comboStr;
        }
        while (mc.fontRendererObj.getStringWidth(recLbl) > recW - 8 && recLbl.length() > 1)
            recLbl = recLbl.substring(0, recLbl.length() - 1);
        mc.fontRendererObj.drawString(recLbl, cx + 5, cy + 3,
                recordingKeybind ? 0xFFFF8888 : (pendingKeybindCodes.isEmpty() ? C_TEXT : C_ACCENT));

        // Add-bind button
        int addKbX = cx + recW + 4;
        int addKbW = CW - recW - 4;
        boolean addKbHov = !recordingKeybind && mx >= addKbX && mx <= addKbX + addKbW && my >= cy && my <= cy + 14;
        boolean canAdd   = !pendingKeybindCodes.isEmpty() && !kbMessageField.getText().trim().isEmpty();
        Gui.drawRect(addKbX, cy, addKbX + addKbW, cy + 14,
                canAdd ? (addKbHov ? 0xFF1A3A6A : 0xFF112244) : 0xFF0A1020);
        drawBorder(addKbX, cy, addKbX + addKbW, cy + 14, canAdd ? C_ACCENT2 : C_DIVIDER);
        int addLblW = mc.fontRendererObj.getStringWidth("Add Bind");
        mc.fontRendererObj.drawString("Add Bind", addKbX + (addKbW - addLblW) / 2, cy + 3,
                canAdd ? C_TEXT : C_TEXT_DIM);
        cy += 18;

        // Keybind list
        int kbListAreaH  = Math.min(64, H - (cy - panelY) / 2 - 8);
        int kbRowH       = 13;
        int maxKbVis     = Math.max(1, kbListAreaH / kbRowH);
        int maxKbScroll  = Math.max(0, data.keybinds.size() - maxKbVis);
        kbScrollY        = Math.max(0, Math.min(maxKbScroll, kbScrollY));

        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(cx * scale, sr.getScaledHeight() * scale - (cy + kbListAreaH) * scale,
                CW * scale, kbListAreaH * scale);
        int ry = cy;
        for (int i = kbScrollY; i < data.keybinds.size() && ry < cy + kbListAreaH; i++) {
            ChatTabData.KeybindEntry kb = data.keybinds.get(i);
            boolean rHov = mx >= cx && mx <= cx + CW && my >= ry && my <= ry + kbRowH;
            Gui.drawRect(cx, ry, cx + CW, ry + kbRowH,
                    rHov ? C_CARD_H : (i % 2 == 0 ? C_CARD : 0xFF101418));
            String keyStr = "[" + kb.displayKey() + "]";
            mc.fontRendererObj.drawString(keyStr, cx + 4, ry + 2, C_ACCENT);
            int keyW = mc.fontRendererObj.getStringWidth(keyStr);
            String lbl = kb.label.isEmpty() ? kb.message : kb.label;
            while (mc.fontRendererObj.getStringWidth(lbl) > CW - keyW - 24 && lbl.length() > 1)
                lbl = lbl.substring(0, lbl.length() - 1);
            mc.fontRendererObj.drawString(lbl, cx + keyW + 8, ry + 2, C_TEXT);
            int delX = cx + CW - 12;
            boolean delHov = mx >= delX && mx <= delX + 10 && my >= ry && my <= ry + kbRowH;
            mc.fontRendererObj.drawString("x", delX + 1, ry + 2, delHov ? 0xFFFF6666 : 0xFF884444);
            ry += kbRowH;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        cy += kbListAreaH + 4;

        // ── AUTO-RESPONSES ────────────────────────────────────────────────────
        drawSectionHeader(mc, cx, cy, "Auto-Responses");
        cy += 13;
        mc.fontRendererObj.drawString("Auto-reply when a message contains trigger.", cx + 2, cy, C_TEXT_DIM);
        cy += 11;

        int arHalf = (CW - 4) / 2;
        Gui.drawRect(cx, cy, cx + arHalf, cy + 14, C_CARD);
        drawBorder(cx, cy, cx + arHalf, cy + 14, arTriggerField.isFocused() ? C_ACCENT : C_DIVIDER);
        arTriggerField.xPosition = cx + 3; arTriggerField.yPosition = cy + 2;
        arTriggerField.width = arHalf - 6; arTriggerField.setEnableBackgroundDrawing(false);
        arTriggerField.drawTextBox();
        if (arTriggerField.getText().isEmpty() && !arTriggerField.isFocused())
            mc.fontRendererObj.drawString("Trigger phrase...", cx + 4, cy + 3, C_TEXT_DIM);

        int arRX = cx + arHalf + 4;
        Gui.drawRect(arRX, cy, arRX + arHalf - 16, cy + 14, C_CARD);
        drawBorder(arRX, cy, arRX + arHalf - 16, cy + 14, arResponseField.isFocused() ? C_ACCENT : C_DIVIDER);
        arResponseField.xPosition = arRX + 3; arResponseField.yPosition = cy + 2;
        arResponseField.width = arHalf - 20; arResponseField.setEnableBackgroundDrawing(false);
        arResponseField.drawTextBox();
        if (arResponseField.getText().isEmpty() && !arResponseField.isFocused())
            mc.fontRendererObj.drawString("Response...", arRX + 4, cy + 3, C_TEXT_DIM);

        int arAddX = arRX + arHalf - 14;
        boolean arAddHov = mx >= arAddX && mx <= arAddX + 12 && my >= cy && my <= cy + 14;
        Gui.drawRect(arAddX, cy, arAddX + 12, cy + 14, arAddHov ? 0xFF1A3A6A : 0xFF112244);
        drawBorder(arAddX, cy, arAddX + 12, cy + 14, C_ACCENT2);
        mc.fontRendererObj.drawString("+", arAddX + 3, cy + 3, C_TEXT);
        cy += 18;

        // Auto-response list
        int arListAreaH = H - (cy - panelY) - 8;
        int arRowH      = 13;
        int maxArVis    = Math.max(1, arListAreaH / arRowH);
        int maxArScroll = Math.max(0, data.autoResponses.size() - maxArVis);
        arScrollY       = Math.max(0, Math.min(maxArScroll, arScrollY));

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(cx * scale, sr.getScaledHeight() * scale - (cy + arListAreaH) * scale,
                CW * scale, arListAreaH * scale);
        ry = cy;
        for (int i = arScrollY; i < data.autoResponses.size() && ry < cy + arListAreaH; i++) {
            ChatTabData.AutoResponseEntry ar = data.autoResponses.get(i);
            boolean rHov = mx >= cx && mx <= cx + CW && my >= ry && my <= ry + arRowH;
            Gui.drawRect(cx, ry, cx + CW, ry + arRowH,
                    rHov ? C_CARD_H : (i % 2 == 0 ? C_CARD : 0xFF101418));
            String trig = ar.trigger;
            if (mc.fontRendererObj.getStringWidth(trig) > CW / 2 - 12) {
                while (mc.fontRendererObj.getStringWidth(trig + "\u2026") > CW / 2 - 12 && trig.length() > 1)
                    trig = trig.substring(0, trig.length() - 1);
                trig += "\u2026";
            }
            String resp = ar.response;
            if (mc.fontRendererObj.getStringWidth(resp) > CW / 2 - 20) {
                while (mc.fontRendererObj.getStringWidth(resp + "\u2026") > CW / 2 - 20 && resp.length() > 1)
                    resp = resp.substring(0, resp.length() - 1);
                resp += "\u2026";
            }
            mc.fontRendererObj.drawString(trig, cx + 4, ry + 2, 0xFFFFCC44);
            mc.fontRendererObj.drawString("\u2192", cx + CW / 2 - 6, ry + 2, C_TEXT_DIM);
            mc.fontRendererObj.drawString(resp, cx + CW / 2 + 4, ry + 2, C_TEXT);
            int delX2 = cx + CW - 12;
            boolean delHov2 = mx >= delX2 && mx <= delX2 + 10 && my >= ry && my <= ry + arRowH;
            mc.fontRendererObj.drawString("x", delX2 + 1, ry + 2, delHov2 ? 0xFFFF6666 : 0xFF884444);
            ry += arRowH;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ── Mouse click ───────────────────────────────────────────────────────────

    public void mouseClicked(int mx, int my, int btn, int cx, int cy, ScaledResolution sr) {
        cy += 13 + 14 + 10; // header + description + label row

        kbLabelField.mouseClicked(mx, my, btn);
        kbMessageField.mouseClicked(mx, my, btn);
        cy += 18; // field row

        int recW = CW / 2 - 2;
        if (btn == 0 && mx >= cx && mx <= cx + recW && my >= cy && my <= cy + 14) {
            if (recordingKeybind) {
                recordingKeybind = false;
            } else {
                recordingKeybind = true;
                pendingKeybindCodes.clear();
            }
            return;
        }

        int addKbX = cx + recW + 4;
        int addKbW = CW - recW - 4;
        if (btn == 0 && mx >= addKbX && mx <= addKbX + addKbW && my >= cy && my <= cy + 14) {
            String msg = kbMessageField.getText().trim();
            if (!msg.isEmpty() && !pendingKeybindCodes.isEmpty()) {
                data.keybinds.add(new ChatTabData.KeybindEntry(
                        new java.util.ArrayList<>(pendingKeybindCodes),
                        msg, kbLabelField.getText().trim()));
                kbMessageField.setText(""); kbLabelField.setText("");
                pendingKeybindCodes.clear();
                data.save();
            }
            return;
        }
        cy += 18;

        // Delete keybind rows
        int kbListAreaH = Math.min(64, H / 2 - 8);
        int kbRowH = 13;
        int ry = cy;
        for (int i = kbScrollY; i < data.keybinds.size() && ry < cy + kbListAreaH; i++) {
            int delX = cx + CW - 12;
            if (btn == 0 && mx >= delX && mx <= delX + 10 && my >= ry && my <= ry + kbRowH) {
                data.keybinds.remove(i); data.save(); return;
            }
            ry += kbRowH;
        }
        cy += kbListAreaH + 4;

        // Auto-responses section
        cy += 13 + 11;
        arTriggerField.mouseClicked(mx, my, btn);
        arResponseField.mouseClicked(mx, my, btn);
        int arHalf = (CW - 4) / 2;
        int arAddX = cx + arHalf + 4 + arHalf - 14;
        if (btn == 0 && mx >= arAddX && mx <= arAddX + 12 && my >= cy && my <= cy + 14) {
            String trig = arTriggerField.getText().trim();
            String resp = arResponseField.getText().trim();
            if (!trig.isEmpty() && !resp.isEmpty()) {
                data.autoResponses.add(new ChatTabData.AutoResponseEntry(trig, resp));
                arTriggerField.setText(""); arResponseField.setText("");
                data.save();
            }
            return;
        }
        cy += 18;

        // Delete auto-response rows
        int arRowH      = 13;
        int panelY      = (sr.getScaledHeight() - H) / 2;
        int arListAreaH = H - (cy - panelY) - 8;
        ry = cy;
        for (int i = arScrollY; i < data.autoResponses.size() && ry < cy + arListAreaH; i++) {
            int delX2 = cx + CW - 12;
            if (btn == 0 && mx >= delX2 && mx <= delX2 + 10 && my >= ry && my <= ry + arRowH) {
                data.autoResponses.remove(i); data.save(); return;
            }
            ry += arRowH;
        }
    }

    // ── Key handling ──────────────────────────────────────────────────────────

    public void keyTyped(char c, int code) {
        if (recordingKeybind) {
            if (code == 1 /* ESC */) { recordingKeybind = false; pendingKeybindCodes.clear(); return; }
            if (code != 0 && !pendingKeybindCodes.contains(code)) pendingKeybindCodes.add(code);
            return;
        }
        if (kbLabelField.isFocused())    { kbLabelField.textboxKeyTyped(c, code);    return; }
        if (kbMessageField.isFocused())  { kbMessageField.textboxKeyTyped(c, code);  return; }
        if (arTriggerField.isFocused())  { arTriggerField.textboxKeyTyped(c, code);  return; }
        if (arResponseField.isFocused()) { arResponseField.textboxKeyTyped(c, code); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildComboString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pendingKeybindCodes.size(); i++) {
            if (i > 0) sb.append('+');
            String name = org.lwjgl.input.Keyboard.getKeyName(pendingKeybindCodes.get(i));
            sb.append(name != null ? name : "?");
        }
        return sb.toString();
    }
}

