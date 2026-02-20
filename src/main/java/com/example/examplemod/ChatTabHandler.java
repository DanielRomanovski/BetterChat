package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatTabHandler {
    private final ChatTabData data = new ChatTabData();
    private final ChatSettingsGui settings = new ChatSettingsGui(data);

    private int selectedTabIndex = 0;
    private int editingTabIndex = -1;
    private int pendingDeleteIndex = -1;
    private boolean isSettingsOpen = false;
    private boolean isDragging = false, isResizing = false;
    private int dragOffsetX, dragOffsetY;
    private GuiTextField renameField, customChatField;
    private long lastClickTime = 0;
    private int lastClickedIndex = -1;
    private boolean wasChatOpen = false;

    private int draggingTabIndex = -1;
    private int dragTabVisualX = 0;
    private int dragTabMouseOffset = 0;

    private void handleScreenResize(ScaledResolution sr) {
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        if (data.isLocked && sw == data.lockedResW && sh == data.lockedResH) {
            data.windowX = data.lockedX; data.windowY = data.lockedY;
            data.windowWidth = data.lockedW; data.windowHeight = data.lockedH;
        } else {
            clampToScreen(sr);
        }
        data.lastResW = sw; data.lastResH = sh;
    }

    private void clampToScreen(ScaledResolution sr) {
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();
        int requiredWidth = 10;
        for (String tab : data.tabs) {
            requiredWidth += Minecraft.getMinecraft().fontRendererObj.getStringWidth(tab) + 18 + 4;
        }
        requiredWidth += 45;
        if (data.windowWidth < requiredWidth) data.windowWidth = requiredWidth;
        if (data.windowHeight < 50) data.windowHeight = 50;
        if (data.windowX < 0) data.windowX = 0;
        if (data.windowY < 0) data.windowY = 0;
        if (data.windowX + data.windowWidth > screenW) data.windowX = screenW - data.windowWidth;
        if (data.windowHeight + data.windowY + 16 > screenH) data.windowY = screenH - data.windowHeight - 16;
    }

    @SubscribeEvent
    public void onRenderHUD(RenderGameOverlayEvent.Text event) {
        if (!data.hideDefaultChat) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiChat) return;
        long elapsed = System.currentTimeMillis() - data.lastMessageTime;
        if (elapsed > 8000) return;
        float fade = (elapsed > 7000) ? 1.0f - (float)(elapsed - 7000) / 1000f : 1.0f;
        renderContent(mc, (int)(fade * 255), true);
    }

    @SubscribeEvent
    public void onRenderChat(RenderGameOverlayEvent.Pre event) {
        if (data.hideDefaultChat && event.type == RenderGameOverlayEvent.ElementType.CHAT) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String formatted = event.message.getFormattedText();
        String plain = event.message.getUnformattedText();
        String playerName = Minecraft.getMinecraft().thePlayer.getName();
        String today = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        boolean isLocal = plain.startsWith("<" + playerName + ">") || plain.startsWith(playerName + ":");
        boolean isOtherPlayer = (plain.startsWith("<") && plain.contains(">")) || (plain.contains(":") && !plain.startsWith("["));
        boolean isCommand = plain.startsWith("/");

        for (int i = 0; i < data.tabs.size(); i++) {
            List<ChatTabData.ChatMessage> history = data.chatHistories.get(i);
            if (history == null) { history = new ArrayList<>(); data.chatHistories.put(i, history); }
            if (history.isEmpty() || !history.get(history.size() - 1).date.equals(today)) {
                history.add(new ChatTabData.ChatMessage(today, true));
            }
            String ex = data.tabExclusions.getOrDefault(i, "");
            if (!ex.isEmpty() && Arrays.stream(ex.split(",")).anyMatch(k -> !k.trim().isEmpty() && plain.toLowerCase().contains(k.trim().toLowerCase()))) {
                continue;
            }
            boolean pass = isLocal || data.includeAllFilters.getOrDefault(i, false) || plain.contains(playerName);
            if (!pass) {
                String f = data.tabFilters.getOrDefault(i, "");
                if (!f.isEmpty() && Arrays.stream(f.split(",")).anyMatch(k -> !k.trim().isEmpty() && plain.toLowerCase().contains(k.trim().toLowerCase()))) pass = true;
                if (data.includeCommandsFilters.getOrDefault(i, false) && isCommand) pass = true;
                if (data.serverMessageFilters.getOrDefault(i, false) && !isOtherPlayer) pass = true;
                if (data.includePlayersFilters.getOrDefault(i, false) && isOtherPlayer) pass = true;
            }
            if (pass) {
                history.add(new ChatTabData.ChatMessage(formatted, false));
                if (i != selectedTabIndex && !isLocal) data.tabNotifications.put(i, true);
            }
        }
        data.lastMessageTime = System.currentTimeMillis();
        data.save();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiChat)) {
            customChatField = null;
            wasChatOpen = false;
            return;
        }

        if (!wasChatOpen) {
            wasChatOpen = true;
            if (data.scrollOffsets.containsKey(selectedTabIndex)) {
                data.scrollOffsets.put(selectedTabIndex, 0);
            }
        }

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        handleScreenResize(sr);
        int mx = Mouse.getEventX() * event.gui.width / mc.displayWidth;
        int my = event.gui.height - Mouse.getEventY() * event.gui.height / mc.displayHeight - 1;

        if (isSettingsOpen) { settings.draw(mx, my); return; }

        if (Mouse.isButtonDown(0)) {
            if (isDragging) { data.windowX = mx - dragOffsetX; data.windowY = my - dragOffsetY; clampToScreen(sr); }
            else if (isResizing) { data.windowWidth = mx - data.windowX; data.windowHeight = my - data.windowY; clampToScreen(sr); }
            else if (draggingTabIndex != -1) { dragTabVisualX = mx - dragTabMouseOffset; }
        } else {
            if (isDragging || isResizing) data.save();
            if (draggingTabIndex != -1) { draggingTabIndex = -1; data.save(); }
            isDragging = false; isResizing = false;
        }

        Gui.drawRect(data.windowX, data.windowY, data.windowX + data.windowWidth, data.windowY + data.windowHeight, data.getHex(data.colorBackground, data.opacBackground));
        Gui.drawRect(data.windowX, data.windowY, data.windowX + data.windowWidth, data.windowY + 22, data.getHex(data.colorTopBar, data.opacTopBar));

        if (data.hideDefaultChat) {
            GuiChat chatGui = (GuiChat) event.gui;
            GuiTextField vf = getVanillaInputField(chatGui);
            if (vf != null) {
                vf.width = 0; vf.yPosition = -100;
                if (customChatField == null) {
                    customChatField = new GuiTextField(999, mc.fontRendererObj, data.windowX + 4, data.windowY + data.windowHeight + 4, data.windowWidth - 8, 12);
                    customChatField.setMaxStringLength(100); customChatField.setEnableBackgroundDrawing(false);
                }
                customChatField.setText(vf.getText());
                customChatField.setCursorPosition(vf.getCursorPosition());
                Gui.drawRect(data.windowX, data.windowY + data.windowHeight, data.windowX + data.windowWidth, data.windowY + data.windowHeight + 16, 0xCC000000);
                customChatField.xPosition = data.windowX + 4; customChatField.yPosition = data.windowY + data.windowHeight + 4; customChatField.width = data.windowWidth - 8;
                customChatField.drawTextBox();
            }
        }

        mc.fontRendererObj.drawString("\u2699", data.windowX + data.windowWidth - 15, data.windowY + 7, 0xFFFFFF);
        int curX = data.windowX + 5;
        int selectionHex = data.getHex(data.colorSelection, 255);

        for (int i = 0; i < data.tabs.size(); i++) {
            if (i == draggingTabIndex) { curX += mc.fontRendererObj.getStringWidth(data.tabs.get(i)) + 22; continue; }
            int tabW = mc.fontRendererObj.getStringWidth(data.tabs.get(i)) + 18;
            if (draggingTabIndex != -1) {
                if (dragTabVisualX < curX + (tabW/2) && draggingTabIndex > i) { data.swapTabs(draggingTabIndex, i); draggingTabIndex = i; }
                else if (dragTabVisualX + mc.fontRendererObj.getStringWidth(data.tabs.get(draggingTabIndex)) + 18 > curX + (tabW/2) && draggingTabIndex < i) { data.swapTabs(draggingTabIndex, i); draggingTabIndex = i; }
            }
            drawSingleTab(i, curX, selectionHex, mc);
            curX += tabW + 4;
        }

        if (draggingTabIndex != -1 && draggingTabIndex < data.tabs.size()) {
            drawSingleTab(draggingTabIndex, dragTabVisualX, selectionHex, mc);
        }

        mc.fontRendererObj.drawString("[+]", curX, data.windowY + 7, selectionHex);
        if (!data.isLocked) Gui.drawRect(data.windowX + data.windowWidth - 5, data.windowY + data.windowHeight - 5, data.windowX + data.windowWidth, data.windowY + data.windowHeight, 0x55FFFFFF);
        renderContent(mc, 255, false);
    }

    private void drawSingleTab(int i, int x, int selectionHex, Minecraft mc) {
        if (i >= data.tabs.size()) return;
        int tw = (i == editingTabIndex && renameField != null) ? mc.fontRendererObj.getStringWidth(renameField.getText()) : mc.fontRendererObj.getStringWidth(data.tabs.get(i));
        int tabW = tw + 18;
        if (i == selectedTabIndex) Gui.drawRect(x, data.windowY + 20, x + tabW, data.windowY + 22, data.getHex(data.colorSelection, data.opacSelection));
        if (data.showNotifications && data.tabNotifications.getOrDefault(i, false)) Gui.drawRect(x + 2, data.windowY + 4, x + 6, data.windowY + 8, selectionHex);
        if (i == editingTabIndex && renameField != null) { renameField.xPosition = x + 9; renameField.yPosition = data.windowY + 7; renameField.drawTextBox(); }
        else mc.fontRendererObj.drawString(data.tabs.get(i), x + 9, data.windowY + 7, (i == pendingDeleteIndex) ? 0xFFFF5555 : 0xFFFFFF);
    }

    private void renderContent(Minecraft mc, int globalAlpha, boolean isHUD) {
        if (selectedTabIndex >= data.tabs.size()) selectedTabIndex = 0;
        List<ChatTabData.ChatMessage> history = data.chatHistories.get(selectedTabIndex);
        if (history == null || history.isEmpty()) return;
        List<RenderableLine> allLines = new ArrayList<>();
        int wrapWidth = data.windowWidth - 15;
        for (ChatTabData.ChatMessage msg : history) {
            if (msg.isDateSeparator) allLines.add(new RenderableLine(msg.text, true, msg.time, msg.date));
            else {
                List<String> wrapped = mc.fontRendererObj.listFormattedStringToWidth(msg.text, wrapWidth);
                for (int j = 0; j < wrapped.size(); j++) allLines.add(new RenderableLine(wrapped.get(j), false, (j == 0) ? msg.time : "", msg.date));
            }
        }
        int maxLines = (data.windowHeight - 30) / 10;
        int totalLines = allLines.size();
        int currentOffset = data.scrollOffsets.getOrDefault(selectedTabIndex, 0);
        if (!isHUD && Mouse.hasWheel()) {
            int wheel = Mouse.getDWheel();
            if (wheel != 0) {
                currentOffset += (wheel > 0 ? 1 : -1);
                currentOffset = Math.max(0, Math.min(totalLines - maxLines, currentOffset));
                data.scrollOffsets.put(selectedTabIndex, currentOffset);
            }
        }
        int end = Math.max(0, totalLines - currentOffset);
        int start = Math.max(0, end - maxLines);
        int y = data.windowY + data.windowHeight - 12;
        for (int i = end - 1; i >= start; i--) {
            if (i < 0 || i >= allLines.size()) continue;
            RenderableLine line = allLines.get(i);
            if (line.isSeparator) {
                int tw = mc.fontRendererObj.getStringWidth(line.text);
                Gui.drawRect(data.windowX + 5, y + 4, data.windowX + (data.windowWidth / 2) - (tw / 2) - 5, y + 5, 0x22FFFFFF);
                Gui.drawRect(data.windowX + (data.windowWidth / 2) + (tw / 2) + 5, y + 4, data.windowX + data.windowWidth - 5, y + 5, 0x22FFFFFF);
                mc.fontRendererObj.drawString(line.text, data.windowX + (data.windowWidth / 2) - (tw / 2), y, 0xAAAAAA | (globalAlpha << 24));
            } else {
                mc.fontRendererObj.drawStringWithShadow(line.text, data.windowX + 5, y, data.getHex(data.colorText, (int)((data.opacText/255.0)*globalAlpha)));
                if (data.showTimeStamps && !line.time.isEmpty() && !isHUD) {
                    int timeW = mc.fontRendererObj.getStringWidth(line.time);
                    mc.fontRendererObj.drawString(line.time, data.windowX + data.windowWidth - timeW - 8, y, data.getHex(data.colorTime, (int)((data.opacTime/255.0)*globalAlpha)));
                }
            }
            y -= 10;
        }
        if (!isHUD && totalLines > maxLines) renderScrollBar(totalLines, maxLines, currentOffset, data.getHex(data.colorSelection, 255));
    }

    private void renderScrollBar(int total, int visible, int offset, int color) {
        int barX = data.windowX + data.windowWidth - 4;
        int barAreaY = data.windowY + 25;
        int barAreaHeight = data.windowHeight - 35;
        int thumbH = Math.max(10, (int) (barAreaHeight * ((double)visible / total)));
        int thumbY = barAreaY + (barAreaHeight - thumbH) - (int) ((barAreaHeight - thumbH) * ((double)offset / (total - visible)));
        Gui.drawRect(barX, thumbY, barX + 2, thumbY + thumbH, color);
    }

    private GuiTextField getVanillaInputField(GuiChat gui) {
        try {
            for (String name : new String[]{"inputField", "field_146415_a"}) {
                try { Field f = GuiChat.class.getDeclaredField(name); f.setAccessible(true); return (GuiTextField) f.get(gui); } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception e) {}
        return null;
    }

    private static class RenderableLine {
        String text, time, date; boolean isSeparator;
        RenderableLine(String t, boolean s, String tm, String dt) { text = t; isSeparator = s; time = tm; date = dt; }
    }

    @SubscribeEvent
    public void onMouseClick(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || !Mouse.getEventButtonState()) return;
        int mx = Mouse.getEventX() * event.gui.width / Minecraft.getMinecraft().displayWidth;
        int my = event.gui.height - Mouse.getEventY() * event.gui.height / Minecraft.getMinecraft().displayHeight - 1;
        int btn = Mouse.getEventButton();
        if (isSettingsOpen) { settings.mouseClicked(mx, my, btn); return; }
        if (btn == 0 && mx >= data.windowX + data.windowWidth - 20 && mx <= data.windowX + data.windowWidth && my >= data.windowY && my <= data.windowY + 22) { isSettingsOpen = true; return; }
        if (!data.isLocked && btn == 0 && mx >= data.windowX + data.windowWidth - 10 && mx <= data.windowX + data.windowWidth && my >= data.windowY + data.windowHeight - 10 && my <= data.windowY + data.windowHeight) { isResizing = true; return; }
        if (my >= data.windowY && my <= data.windowY + 22) {
            int cx = data.windowX + 5;
            int settingsX = data.windowX + data.windowWidth - 20;
            for (int i = 0; i < data.tabs.size(); i++) {
                int tw = Minecraft.getMinecraft().fontRendererObj.getStringWidth(data.tabs.get(i)) + 18;
                if (mx >= cx && mx <= cx + tw) { handleTabClick(i, btn, mx, cx); return; }
                cx += tw + 4;
            }
            if (btn == 0 && mx >= cx && mx <= cx + 20) { if (cx + 120 < settingsX) data.addTab(); return; }
            if (!data.isLocked && btn == 0 && mx >= data.windowX && mx <= data.windowX + data.windowWidth) { isDragging = true; dragOffsetX = mx - data.windowX; dragOffsetY = my - data.windowY; }
        }
    }

    private void handleTabClick(int i, int btn, int mx, int tabX) {
        if (btn == 0) {
            if (i == lastClickedIndex && (System.currentTimeMillis() - lastClickTime) < 350) {
                editingTabIndex = i; renameField = new GuiTextField(0, Minecraft.getMinecraft().fontRendererObj, 0, 0, 100, 12);
                renameField.setEnableBackgroundDrawing(false); renameField.setText(data.tabs.get(i)); renameField.setFocused(true);
            } else {
                selectedTabIndex = i; editingTabIndex = -1; data.tabNotifications.put(i, false);
                draggingTabIndex = i; dragTabMouseOffset = mx - tabX; dragTabVisualX = tabX;
            }
        } else if (btn == 1) {
            if (pendingDeleteIndex == i) {
                data.deleteTab(i);
                selectedTabIndex = 0;
                pendingDeleteIndex = -1;
                editingTabIndex = -1;
            } else pendingDeleteIndex = i;
        }
        lastClickTime = System.currentTimeMillis(); lastClickedIndex = i;
    }

    @SubscribeEvent
    public void onKeyTyped(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!Keyboard.getEventKeyState()) return;
        int k = Keyboard.getEventKey(); char c = Keyboard.getEventCharacter();
        if (isSettingsOpen) { if (k == Keyboard.KEY_ESCAPE) isSettingsOpen = false; else settings.keyTyped(c, k); event.setCanceled(true); return; }
        if (editingTabIndex != -1 && renameField != null && editingTabIndex < data.tabs.size()) {
            if (k == Keyboard.KEY_RETURN) { if (!renameField.getText().trim().isEmpty()) { data.tabs.set(editingTabIndex, renameField.getText().trim()); data.save(); } editingTabIndex = -1; }
            else if (k == Keyboard.KEY_ESCAPE) editingTabIndex = -1; else renameField.textboxKeyTyped(c, k);
            event.setCanceled(true); return;
        }
    }
}