package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.lang.reflect.Field;
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

    @SubscribeEvent
    public void onRenderChat(RenderGameOverlayEvent.Pre event) {
        if (data.hideDefaultChat && event.type == RenderGameOverlayEvent.ElementType.CHAT) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String plain = event.message.getUnformattedText();
        String formatted = event.message.getFormattedText();
        String playerName = Minecraft.getMinecraft().thePlayer.getName();

        // Check if the message is from YOU
        // Standard chat looks like <Name> message or Name: message
        boolean isLocalPlayerMessage = plain.startsWith("<" + playerName + ">") || plain.startsWith(playerName + ":");

        // Exclude other player messages from custom filters
        // A message is a "player message" if it starts with < but isn't from you
        boolean isOtherPlayerMessage = plain.startsWith("<") && !isLocalPlayerMessage;

        if (isLocalPlayerMessage) {
            // Your messages go into the current selected tab only
            data.chatHistories.get(selectedTabIndex).add(formatted);
        } else if (!isOtherPlayerMessage) {
            // Only non-player messages (system/server) go through the filter
            boolean sorted = false;
            for (int i = 0; i < data.tabs.size(); i++) {
                boolean matches = false;
                String f = data.tabFilters.getOrDefault(i, "");
                if (!f.isEmpty()) {
                    for (String k : f.split(",")) {
                        if (!k.trim().isEmpty() && plain.toLowerCase().contains(k.trim().toLowerCase())) { matches = true; break; }
                    }
                }
                if (matches || data.serverMessageFilters.getOrDefault(i, false)) {
                    data.chatHistories.get(i).add(formatted); sorted = true;
                }
            }
            if (!sorted) data.chatHistories.get(0).add(formatted);
        }

        if (data.saveChatLog) data.save();
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiChat)) { customChatField = null; return; }
        GuiChat guiChat = (GuiChat) event.gui;
        Minecraft mc = Minecraft.getMinecraft();
        int mx = Mouse.getEventX() * event.gui.width / mc.displayWidth;
        int my = event.gui.height - Mouse.getEventY() * event.gui.height / mc.displayHeight - 1;

        if (data.hideDefaultChat) {
            GuiTextField vf = getVanillaInputField(guiChat);
            if (vf != null) { vf.width = 0; vf.height = 0; vf.setFocused(false); vf.setEnableBackgroundDrawing(false); vf.setText(""); }
        }

        if (isSettingsOpen) { settings.draw(mx, my); return; }

        if (data.hideDefaultChat && customChatField == null) {
            customChatField = new GuiTextField(999, mc.fontRendererObj, data.windowX + 4, data.windowY + data.windowHeight + 4, data.windowWidth - 8, 12);
            customChatField.setMaxStringLength(100); customChatField.setFocused(true); customChatField.setCanLoseFocus(false); customChatField.setEnableBackgroundDrawing(false);
        }

        int bgColor = data.getHex(data.colorBackground, 238);
        int topBarColor = data.getHex(data.colorTopBar, 85);
        int accentColor = data.getHex(data.colorSelection, 255);

        if (Mouse.isButtonDown(0)) {
            if (isDragging) { data.windowX = mx - dragOffsetX; data.windowY = my - dragOffsetY; }
            else if (isResizing) { data.windowWidth = Math.max(150, mx - data.windowX); data.windowHeight = Math.max(80, my - data.windowY); }
        } else { if (isDragging || isResizing) data.save(); isDragging = false; isResizing = false; }

        Gui.drawRect(data.windowX, data.windowY, data.windowX + data.windowWidth, data.windowY + data.windowHeight, bgColor);
        Gui.drawRect(data.windowX, data.windowY, data.windowX + data.windowWidth, data.windowY + 22, topBarColor);

        if (data.hideDefaultChat && customChatField != null) {
            Gui.drawRect(data.windowX, data.windowY + data.windowHeight, data.windowX + data.windowWidth, data.windowY + data.windowHeight + 16, 0xCC000000);
            customChatField.xPosition = data.windowX + 4; customChatField.yPosition = data.windowY + data.windowHeight + 4; customChatField.drawTextBox();
        }

        mc.fontRendererObj.drawString("\u2699", data.windowX + data.windowWidth - 15, data.windowY + 7, 0xFFFFFF);
        int curX = data.windowX + 5;
        for (int i = 0; i < data.tabs.size(); i++) {
            int tw = (i == editingTabIndex && renameField != null) ? mc.fontRendererObj.getStringWidth(renameField.getText()) : mc.fontRendererObj.getStringWidth(data.tabs.get(i));
            int tabW = tw + 18;
            if (i == selectedTabIndex) Gui.drawRect(curX, data.windowY + 20, curX + tabW, data.windowY + 22, accentColor);
            if (i == editingTabIndex && renameField != null) { renameField.xPosition = curX + 9; renameField.yPosition = data.windowY + 7; renameField.drawTextBox(); }
            else mc.fontRendererObj.drawString(data.tabs.get(i), curX + 9, data.windowY + 7, (i == pendingDeleteIndex) ? 0xFFFF5555 : 0xFFFFFF);
            curX += tabW + 4;
        }

        Gui.drawRect(curX, data.windowY + 4, curX + 15, data.windowY + 18, 0x55FFFFFF);
        mc.fontRendererObj.drawString("+", curX + 5, data.windowY + 7, 0xFFFFFF);
        Gui.drawRect(data.windowX + data.windowWidth - 5, data.windowY + data.windowHeight - 5, data.windowX + data.windowWidth, data.windowY + data.windowHeight, accentColor);

        renderContent(mc, accentColor);
    }

    private void renderContent(Minecraft mc, int accentColor) {
        List<String> history = data.chatHistories.get(selectedTabIndex);
        if (history == null) return;
        List<String> allLines = new ArrayList<>();
        int wrapWidth = data.windowWidth - 15;
        for (String msg : history) allLines.addAll(mc.fontRendererObj.listFormattedStringToWidth(msg, wrapWidth));

        int maxLines = (data.windowHeight - 30) / 10;
        int wheel = Mouse.getDWheel();
        int currentOffset = data.scrollOffsets.getOrDefault(selectedTabIndex, 0);
        if (wheel != 0) {
            currentOffset += (wheel > 0 ? 1 : -1);
            int maxOffset = Math.max(0, allLines.size() - maxLines);
            if (currentOffset < 0) currentOffset = 0;
            if (currentOffset > maxOffset) currentOffset = maxOffset;
            data.scrollOffsets.put(selectedTabIndex, currentOffset);
        }

        int end = Math.max(0, allLines.size() - currentOffset);
        int start = Math.max(0, end - maxLines);
        int y = data.windowY + data.windowHeight - 12;
        for (int i = end - 1; i >= start; i--) {
            mc.fontRendererObj.drawStringWithShadow(allLines.get(i), data.windowX + 5, y, 0xFFFFFF);
            y -= 10;
        }
        if (allLines.size() > maxLines) renderScrollBar(allLines.size(), maxLines, currentOffset, accentColor);
    }

    private void renderScrollBar(int total, int visible, int offset, int color) {
        int barX = data.windowX + data.windowWidth - 4;
        int barAreaY = data.windowY + 25;
        int barAreaHeight = data.windowHeight - 30;
        Gui.drawRect(barX, barAreaY, barX + 2, barAreaY + barAreaHeight, 0x44FFFFFF);
        double ratio = (double) visible / total;
        int thumbHeight = Math.max(10, (int) (barAreaHeight * ratio));
        double scrollPercent = (double) offset / (total - visible);
        int thumbY = barAreaY + (barAreaHeight - thumbHeight) - (int) ((barAreaHeight - thumbHeight) * scrollPercent);
        Gui.drawRect(barX, thumbY, barX + 2, thumbY + thumbHeight, color);
    }

    private GuiTextField getVanillaInputField(GuiChat gui) {
        try {
            for (String name : new String[]{"inputField", "field_146415_a"}) {
                try {
                    Field f = GuiChat.class.getDeclaredField(name); f.setAccessible(true);
                    return (GuiTextField) f.get(gui);
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    @SubscribeEvent
    public void onMouseClick(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || !Mouse.getEventButtonState()) return;
        int mx = Mouse.getEventX() * event.gui.width / Minecraft.getMinecraft().displayWidth;
        int my = event.gui.height - Mouse.getEventY() * event.gui.height / Minecraft.getMinecraft().displayHeight - 1;
        int btn = Mouse.getEventButton();
        if (isSettingsOpen) { settings.mouseClicked(mx, my, btn); return; }
        if (btn == 0 && mx >= data.windowX + data.windowWidth - 20 && my <= data.windowY + 22) { isSettingsOpen = true; return; }
        if (btn == 0 && mx >= data.windowX + data.windowWidth - 10 && my >= data.windowY + data.windowHeight - 10) { isResizing = true; return; }
        if (mx >= data.windowX && mx <= data.windowX + data.windowWidth && my >= data.windowY && my <= data.windowY + 22) {
            int cx = data.windowX + 5;
            for (int i = 0; i < data.tabs.size(); i++) {
                int tw = Minecraft.getMinecraft().fontRendererObj.getStringWidth(data.tabs.get(i)) + 18;
                if (mx >= cx && mx <= cx + tw) { handleTabClick(i, btn); return; }
                cx += tw + 4;
            }
            if (btn == 0 && mx >= cx && mx <= cx + 15) { data.addTab(); return; }
            if (btn == 0) { isDragging = true; dragOffsetX = mx - data.windowX; dragOffsetY = my - data.windowY; }
        }
    }

    private void handleTabClick(int i, int btn) {
        if (btn == 0) {
            if (i == lastClickedIndex && (System.currentTimeMillis() - lastClickTime) < 350) {
                editingTabIndex = i; renameField = new GuiTextField(0, Minecraft.getMinecraft().fontRendererObj, 0, 0, 100, 12);
                renameField.setEnableBackgroundDrawing(false); renameField.setText(data.tabs.get(i)); renameField.setFocused(true);
            } else { selectedTabIndex = i; pendingDeleteIndex = -1; editingTabIndex = -1; }
        } else if (btn == 1) {
            if (pendingDeleteIndex == i) { data.deleteTab(i); selectedTabIndex = 0; pendingDeleteIndex = -1; }
            else pendingDeleteIndex = i;
        }
        lastClickTime = System.currentTimeMillis(); lastClickedIndex = i;
    }

    @SubscribeEvent
    public void onKeyTyped(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        int k = Keyboard.getEventKey(); char c = Keyboard.getEventCharacter();
        if (isSettingsOpen) { if (k == Keyboard.KEY_ESCAPE) isSettingsOpen = false; else settings.keyTyped(c, k); event.setCanceled(true); return; }
        if (editingTabIndex != -1 && renameField != null) {
            if (k == Keyboard.KEY_RETURN) { if (!renameField.getText().trim().isEmpty()) { data.tabs.set(editingTabIndex, renameField.getText().trim()); data.save(); } editingTabIndex = -1; }
            else if (k == Keyboard.KEY_ESCAPE) editingTabIndex = -1;
            else renameField.textboxKeyTyped(c, k);
            event.setCanceled(true); return;
        }
        if (data.hideDefaultChat && customChatField != null) {
            if (k == Keyboard.KEY_RETURN) {
                String m = customChatField.getText().trim();
                if (!m.isEmpty()) { Minecraft.getMinecraft().thePlayer.sendChatMessage(m); customChatField.setText(""); }
                Minecraft.getMinecraft().displayGuiScreen(null);
            } else if (k == Keyboard.KEY_ESCAPE) Minecraft.getMinecraft().displayGuiScreen(null);
            else customChatField.textboxKeyTyped(c, k);
            event.setCanceled(true);
        }
    }
}