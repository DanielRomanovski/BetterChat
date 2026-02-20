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
    private long lastMessageTime = 0;
    private long lastClickTime = 0;
    private int lastClickedIndex = -1;
    private int lastTabEndX = 0;
    private boolean wasChatOpen = false;

    private void handleScreenResize(ScaledResolution sr) {
        if (data.lastResW == -1) {
            data.lastResW = sr.getScaledWidth();
            data.lastResH = sr.getScaledHeight();
            return;
        }

        if (data.lastResW != sr.getScaledWidth() || data.lastResH != sr.getScaledHeight()) {
            if (!data.isLocked) {
                float ratioX = (float) data.windowX / data.lastResW;
                float ratioY = (float) data.windowY / data.lastResH;
                data.windowX = (int) (ratioX * sr.getScaledWidth());
                data.windowY = (int) (ratioY * sr.getScaledHeight());
            }
            data.lastResW = sr.getScaledWidth();
            data.lastResH = sr.getScaledHeight();
            clampToScreen(sr);
            data.save();
        }
    }

    private void clampToScreen(ScaledResolution sr) {
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();
        int minWidth = Math.max(120, (lastTabEndX - data.windowX) + 25);

        // Force bounds
        if (data.windowX < 0) data.windowX = 0;
        if (data.windowY < 0) data.windowY = 0;

        // Ensure window doesn't grow right when pushed left
        if (data.windowX + data.windowWidth > screenW) {
            if (data.windowX == 0) data.windowWidth = screenW;
            else data.windowX = screenW - data.windowWidth;
        }

        if (data.windowWidth < minWidth) data.windowWidth = minWidth;
        if (data.windowHeight < 50) data.windowHeight = 50;
        if (data.windowHeight + data.windowY + 16 > screenH) data.windowY = screenH - data.windowHeight - 16;
    }

    @SubscribeEvent
    public void onRenderHUD(RenderGameOverlayEvent.Text event) {
        if (!data.hideDefaultChat) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiChat) return;
        long elapsed = System.currentTimeMillis() - data.lastMessageTime;
        if (elapsed > 8000) return;
        float alphaFactor = (elapsed > 7000) ? 1.0f - (float)(elapsed - 7000) / 1000f : 1.0f;
        int alpha = (int)(alphaFactor * 255);
        if (alpha < 5) return;
        renderContent(mc, data.getHex(data.colorSelection, alpha), alpha, true);
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

        boolean isLocal = plain.startsWith("<" + playerName + ">") || plain.startsWith(playerName + ":");
        boolean isOtherPlayer = (plain.startsWith("<") && plain.contains(">")) || (plain.contains(":") && !plain.startsWith("["));
        boolean isCommand = plain.startsWith("/");

        for (int i = 0; i < data.tabs.size(); i++) {
            boolean incAll = data.includeAllFilters.getOrDefault(i, false);
            if (isLocal || incAll || plain.contains(playerName)) {
                data.chatHistories.get(i).add(formatted);
                // Notification Logic: Don't notify for self
                if (i != selectedTabIndex && !isLocal) data.tabNotifications.put(i, true);
            } else {
                String f = data.tabFilters.getOrDefault(i, "");
                boolean keywordMatch = !f.isEmpty() && Arrays.stream(f.split(",")).anyMatch(k -> !k.trim().isEmpty() && plain.toLowerCase().contains(k.trim().toLowerCase()));
                boolean pass = keywordMatch;
                if (data.includeCommandsFilters.getOrDefault(i, false) && isCommand) pass = true;
                if (data.serverMessageFilters.getOrDefault(i, false) && !isOtherPlayer) pass = true;
                if (data.includePlayersFilters.getOrDefault(i, false) && isOtherPlayer) pass = true;

                if (pass) {
                    data.chatHistories.get(i).add(formatted);
                    if (i != selectedTabIndex) data.tabNotifications.put(i, true);
                }
            }
        }
        data.lastMessageTime = System.currentTimeMillis();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiChat)) { customChatField = null; wasChatOpen = false; return; }

        // Reset scroll strictly when chat is FIRST opened
        if (!wasChatOpen) {
            for (int i = 0; i < data.tabs.size(); i++) data.scrollOffsets.put(i, 0);
            wasChatOpen = true;
        }

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        handleScreenResize(sr);
        int mx = Mouse.getEventX() * event.gui.width / mc.displayWidth;
        int my = event.gui.height - Mouse.getEventY() * event.gui.height / mc.displayHeight - 1;
        GuiChat chatGui = (GuiChat) event.gui;
        GuiTextField vf = getVanillaInputField(chatGui);
        if (data.hideDefaultChat && vf != null) {
            vf.width = 0; vf.height = 0; vf.yPosition = -100;
            if (customChatField == null) {
                customChatField = new GuiTextField(999, mc.fontRendererObj, data.windowX + 4, data.windowY + data.windowHeight + 4, data.windowWidth - 8, 12);
                customChatField.setMaxStringLength(100); customChatField.setEnableBackgroundDrawing(false);
            }
            customChatField.setText(vf.getText());
            customChatField.setCursorPosition(vf.getCursorPosition());
        }
        if (isSettingsOpen) { settings.draw(mx, my); return; }

        if (Mouse.isButtonDown(0)) {
            if (isDragging) { data.windowX = mx - dragOffsetX; data.windowY = my - dragOffsetY; clampToScreen(sr); }
            else if (isResizing) {
                int minW = Math.max(120, (lastTabEndX - data.windowX) + 25);
                data.windowWidth = Math.max(minW, mx - data.windowX);
                data.windowHeight = Math.max(50, my - data.windowY);
                clampToScreen(sr);
            }
        } else { if (isDragging || isResizing) data.save(); isDragging = false; isResizing = false; }

        Gui.drawRect(data.windowX, data.windowY, data.windowX + data.windowWidth, data.windowY + data.windowHeight, data.getHex(data.colorBackground, 238));
        Gui.drawRect(data.windowX, data.windowY, data.windowX + data.windowWidth, data.windowY + 22, data.getHex(data.colorTopBar, 85));

        if (data.hideDefaultChat && customChatField != null) {
            Gui.drawRect(data.windowX, data.windowY + data.windowHeight, data.windowX + data.windowWidth, data.windowY + data.windowHeight + 16, 0xCC000000);
            customChatField.xPosition = data.windowX + 4; customChatField.yPosition = data.windowY + data.windowHeight + 4; customChatField.width = data.windowWidth - 8;
            customChatField.drawTextBox();
        }

        mc.fontRendererObj.drawString("\u2699", data.windowX + data.windowWidth - 15, data.windowY + 7, 0xFFFFFF);
        int curX = data.windowX + 5;
        for (int i = 0; i < data.tabs.size(); i++) {
            int tw = (i == editingTabIndex && renameField != null) ? mc.fontRendererObj.getStringWidth(renameField.getText()) : mc.fontRendererObj.getStringWidth(data.tabs.get(i));
            int tabW = tw + 18;
            if (i == selectedTabIndex) Gui.drawRect(curX, data.windowY + 20, curX + tabW, data.windowY + 22, data.getHex(data.colorSelection, 255));
            if (i == editingTabIndex && renameField != null) { renameField.xPosition = curX + 9; renameField.yPosition = data.windowY + 7; renameField.drawTextBox(); }
            else {
                mc.fontRendererObj.drawString(data.tabs.get(i), curX + 9, data.windowY + 7, (i == pendingDeleteIndex) ? 0xFFFF5555 : 0xFFFFFF);
                if (data.tabNotifications.getOrDefault(i, false)) {
                    Gui.drawRect(curX + tabW - 4, data.windowY + 4, curX + tabW - 1, data.windowY + 7, 0xFFFF0000);
                }
            }
            curX += tabW + 4;
        }

        if (curX + 25 < data.windowX + data.windowWidth - 15) {
            mc.fontRendererObj.drawString("+", curX + 5, data.windowY + 7, 0xFFFFFF);
            lastTabEndX = curX + 25;
        } else { lastTabEndX = curX; }

        if (!data.isLocked) Gui.drawRect(data.windowX + data.windowWidth - 5, data.windowY + data.windowHeight - 5, data.windowX + data.windowWidth, data.windowY + data.windowHeight, 0x55FFFFFF);
        renderContent(mc, data.getHex(data.colorSelection, 255), 255, false);
    }

    private void renderContent(Minecraft mc, int accentColor, int alpha, boolean isHUD) {
        List<String> history = data.chatHistories.get(selectedTabIndex);
        if (history == null) return;
        List<String> allLines = new ArrayList<>();
        int wrapWidth = data.windowWidth - 15;
        for (String msg : history) allLines.addAll(mc.fontRendererObj.listFormattedStringToWidth(msg, wrapWidth));
        int maxLines = (data.windowHeight - 30) / 10;
        int currentOffset = isHUD ? 0 : data.scrollOffsets.getOrDefault(selectedTabIndex, 0);

        if (!isHUD) {
            int wheel = Mouse.getDWheel();
            if (wheel != 0) {
                currentOffset = Math.max(0, Math.min(allLines.size() - maxLines, currentOffset + (wheel > 0 ? 1 : -1)));
                data.scrollOffsets.put(selectedTabIndex, currentOffset);
            }
        }

        int end = Math.max(0, allLines.size() - currentOffset);
        int start = Math.max(0, end - maxLines);
        int y = data.windowY + data.windowHeight - 12;
        int textColor = (alpha << 24) | 0xFFFFFF;
        for (int i = end - 1; i >= start; i--) { mc.fontRendererObj.drawStringWithShadow(allLines.get(i), data.windowX + 5, y, textColor); y -= 10; }
        if (!isHUD && allLines.size() > maxLines) renderScrollBar(allLines.size(), maxLines, currentOffset, accentColor);
    }

    private void renderScrollBar(int total, int visible, int offset, int color) {
        int barX = data.windowX + data.windowWidth - 4; int barAreaY = data.windowY + 25; int barAreaHeight = data.windowHeight - 30;
        double ratio = (double) visible / total; int thumbH = Math.max(10, (int) (barAreaHeight * ratio));
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

    @SubscribeEvent
    public void onMouseClick(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || !Mouse.getEventButtonState()) return;
        int mx = Mouse.getEventX() * event.gui.width / Minecraft.getMinecraft().displayWidth;
        int my = event.gui.height - Mouse.getEventY() * event.gui.height / Minecraft.getMinecraft().displayHeight - 1;
        int btn = Mouse.getEventButton();
        if (isSettingsOpen) { settings.mouseClicked(mx, my, btn); return; }
        if (btn == 0 && mx >= data.windowX + data.windowWidth - 20 && mx <= data.windowX + data.windowWidth && my >= data.windowY && my <= data.windowY + 22) { isSettingsOpen = true; return; }
        if (!data.isLocked && btn == 0 && mx >= data.windowX + data.windowWidth - 10 && mx <= data.windowX + data.windowWidth && my >= data.windowY + data.windowHeight - 10 && my <= data.windowY + data.windowHeight) { isResizing = true; return; }
        if (mx >= data.windowX && mx <= data.windowX + data.windowWidth && my >= data.windowY && my <= data.windowY + 22) {
            int cx = data.windowX + 5;
            for (int i = 0; i < data.tabs.size(); i++) {
                int tw = Minecraft.getMinecraft().fontRendererObj.getStringWidth(data.tabs.get(i)) + 18;
                if (mx >= cx && mx <= cx + tw) { handleTabClick(i, btn); return; }
                cx += tw + 4;
            }
            if (btn == 0 && mx >= cx && mx <= cx + 20) {
                if (cx + 50 < data.windowX + data.windowWidth - 20) data.addTab();
                return;
            }
            if (!data.isLocked && btn == 0) { isDragging = true; dragOffsetX = mx - data.windowX; dragOffsetY = my - data.windowY; }
        }
    }

    private void handleTabClick(int i, int btn) {
        if (btn == 0) {
            if (i == lastClickedIndex && (System.currentTimeMillis() - lastClickTime) < 350) {
                editingTabIndex = i; renameField = new GuiTextField(0, Minecraft.getMinecraft().fontRendererObj, 0, 0, 100, 12);
                renameField.setEnableBackgroundDrawing(false); renameField.setText(data.tabs.get(i)); renameField.setFocused(true);
            } else {
                selectedTabIndex = i;
                pendingDeleteIndex = -1;
                editingTabIndex = -1;
                data.tabNotifications.put(i, false);
            }
        } else if (btn == 1) { if (pendingDeleteIndex == i) { data.deleteTab(i); selectedTabIndex = 0; pendingDeleteIndex = -1; } else pendingDeleteIndex = i; }
        lastClickTime = System.currentTimeMillis(); lastClickedIndex = i;
    }

    @SubscribeEvent
    public void onKeyTyped(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!Keyboard.getEventKeyState()) return;
        int k = Keyboard.getEventKey(); char c = Keyboard.getEventCharacter();
        if (isSettingsOpen) { if (k == Keyboard.KEY_ESCAPE) isSettingsOpen = false; else settings.keyTyped(c, k); event.setCanceled(true); return; }
        if (editingTabIndex != -1 && renameField != null) {
            if (k == Keyboard.KEY_RETURN) { if (!renameField.getText().trim().isEmpty()) { data.tabs.set(editingTabIndex, renameField.getText().trim()); data.save(); } editingTabIndex = -1; }
            else if (k == Keyboard.KEY_ESCAPE) editingTabIndex = -1; else renameField.textboxKeyTyped(c, k);
            event.setCanceled(true); return;
        }
        if (data.hideDefaultChat && event.gui instanceof GuiChat) {
            if (k == Keyboard.KEY_RETURN) {
                GuiTextField vf = getVanillaInputField((GuiChat)event.gui);
                if (vf != null) {
                    String raw = vf.getText().trim();
                    if (!raw.isEmpty() && !raw.startsWith("/")) vf.setText(data.tabPrefixes.getOrDefault(selectedTabIndex, "") + raw + data.tabSuffixes.getOrDefault(selectedTabIndex, ""));
                }
            }
        }
    }
}