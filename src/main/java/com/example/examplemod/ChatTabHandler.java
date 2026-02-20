package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.util.*;

public class ChatTabHandler {

    private final ChatTabData data = new ChatTabData();
    private final ChatSettingsGui settings = new ChatSettingsGui(data);

    private int selectedTabIndex = 0;
    private int editingTabIndex = -1;
    private int pendingDeleteIndex = -1;
    private boolean isSettingsOpen = false;

    private boolean isDragging = false;
    private boolean isResizing = false;
    private int dragOffsetX, dragOffsetY;

    private GuiTextField renameField;
    private long lastClickTime = 0;
    private int lastClickedIndex = -1;

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String plain = event.message.getUnformattedText();
        String formatted = event.message.getFormattedText();
        Minecraft mc = Minecraft.getMinecraft();
        boolean isFromMe = plain.startsWith("<" + mc.thePlayer.getName() + ">") || plain.startsWith(mc.thePlayer.getName() + ":");

        if (isFromMe) {
            if (data.chatHistories.containsKey(selectedTabIndex)) data.chatHistories.get(selectedTabIndex).add(formatted);
            if (selectedTabIndex != 0) data.chatHistories.get(0).add(formatted);
        } else {
            boolean filtered = false;
            for (int i = 1; i < data.tabs.size(); i++) {
                if (plain.toLowerCase().contains(data.tabs.get(i).toLowerCase())) {
                    data.chatHistories.get(i).add(formatted);
                    filtered = true;
                    break;
                }
            }
            if (!filtered) data.chatHistories.get(0).add(formatted);
        }
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiChat)) return;
        Minecraft mc = Minecraft.getMinecraft();
        int mouseX = Mouse.getEventX() * event.gui.width / mc.displayWidth;
        int mouseY = event.gui.height - Mouse.getEventY() * event.gui.height / mc.displayHeight - 1;

        if (isSettingsOpen) {
            settings.draw(mouseX, mouseY);
            return;
        }

        int bgColor = data.getHex(data.colorBackground, 238);
        int topBarColor = data.getHex(data.colorTopBar, 85);
        int accentColor = data.getHex(data.colorSelection, 255);

        int minWidth = 50;
        for (String s : data.tabs) minWidth += mc.fontRendererObj.getStringWidth(s) + 22;

        if (Mouse.isButtonDown(0)) {
            if (isDragging) {
                data.windowX = mouseX - dragOffsetX;
                data.windowY = mouseY - dragOffsetY;
            } else if (isResizing) {
                data.windowWidth = Math.max(minWidth + 35, mouseX - data.windowX);
                data.windowHeight = Math.max(80, mouseY - data.windowY);
            }
        } else {
            if (isDragging || isResizing) data.save();
            isDragging = false;
            isResizing = false;
        }

        Gui.drawRect(data.windowX, data.windowY, data.windowX + data.windowWidth, data.windowY + data.windowHeight, bgColor);
        Gui.drawRect(data.windowX, data.windowY, data.windowX + data.windowWidth, data.windowY + 22, topBarColor);

        mc.fontRendererObj.drawString("\u2699", data.windowX + data.windowWidth - 15, data.windowY + 7, 0xFFFFFF);

        int currentX = data.windowX + 5;
        for (int i = 0; i < data.tabs.size(); i++) {
            int textWidth = (i == editingTabIndex && renameField != null) ? mc.fontRendererObj.getStringWidth(renameField.getText()) : mc.fontRendererObj.getStringWidth(data.tabs.get(i));
            int tabWidth = textWidth + 18;
            if (i == selectedTabIndex) Gui.drawRect(currentX, data.windowY + 20, currentX + tabWidth, data.windowY + 22, accentColor);
            if (i == editingTabIndex && renameField != null) {
                renameField.xPosition = currentX + 9;
                renameField.yPosition = data.windowY + 7;
                renameField.drawTextBox();
            } else {
                int textColor = (i == pendingDeleteIndex) ? 0xFFFF5555 : 0xFFFFFF;
                mc.fontRendererObj.drawString(data.tabs.get(i), currentX + 9, data.windowY + 7, textColor);
            }
            currentX += tabWidth + 4;
        }

        int plusX = currentX;
        Gui.drawRect(plusX, data.windowY + 4, plusX + 15, data.windowY + 18, 0x55FFFFFF);
        mc.fontRendererObj.drawString("+", plusX + 5, data.windowY + 7, 0xFFFFFF);
        Gui.drawRect(data.windowX + data.windowWidth - 5, data.windowY + data.windowHeight - 5, data.windowX + data.windowWidth, data.windowY + data.windowHeight, accentColor);

        List<String> history = data.chatHistories.get(selectedTabIndex);
        if (history != null) {
            int messageY = data.windowY + data.windowHeight - 12;
            int wrapWidth = data.windowWidth - 10;
            List<String> allLines = new ArrayList<>();
            for (String rawMsg : history) {
                allLines.addAll(mc.fontRendererObj.listFormattedStringToWidth(rawMsg, wrapWidth));
            }
            int maxLines = (data.windowHeight - 30) / 10;
            int start = Math.max(0, allLines.size() - maxLines);
            for (int i = allLines.size() - 1; i >= start; i--) {
                mc.fontRendererObj.drawStringWithShadow(allLines.get(i), data.windowX + 5, messageY, 0xFFFFFF);
                messageY -= 10;
            }
        }
    }

    @SubscribeEvent
    public void onMouseClick(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || !Mouse.getEventButtonState()) return;
        Minecraft mc = Minecraft.getMinecraft();
        int mouseX = Mouse.getEventX() * event.gui.width / mc.displayWidth;
        int mouseY = event.gui.height - Mouse.getEventY() * event.gui.height / mc.displayHeight - 1;
        int button = Mouse.getEventButton();

        if (isSettingsOpen) {
            settings.mouseClicked(mouseX, mouseY, button);
            return;
        }

        if (button == 0 && mouseX >= data.windowX + data.windowWidth - 20 && mouseX <= data.windowX + data.windowWidth && mouseY >= data.windowY && mouseY <= data.windowY + 22) {
            isSettingsOpen = true;
            mc.thePlayer.playSound("gui.button.press", 1.0F, 1.0F);
            return;
        }

        if (button == 0 && mouseX >= data.windowX + data.windowWidth - 10 && mouseY >= data.windowY + data.windowHeight - 10) {
            isResizing = true;
            return;
        }

        if (mouseX >= data.windowX && mouseX <= data.windowX + data.windowWidth && mouseY >= data.windowY && mouseY <= data.windowY + 22) {
            int currentX = data.windowX + 5;
            for (int i = 0; i < data.tabs.size(); i++) {
                int tabWidth = mc.fontRendererObj.getStringWidth(data.tabs.get(i)) + 18;
                if (mouseX >= currentX && mouseX <= currentX + tabWidth) {
                    handleTabClick(i, button, mc);
                    return;
                }
                currentX += tabWidth + 4;
            }
            if (button == 0 && mouseX >= currentX && mouseX <= currentX + 15) {
                data.addTab();
                mc.thePlayer.playSound("gui.button.press", 1.0F, 1.0F);
                return;
            }
            if (button == 0) {
                isDragging = true;
                dragOffsetX = mouseX - data.windowX;
                dragOffsetY = mouseY - data.windowY;
            }
        }
    }

    private void handleTabClick(int i, int button, Minecraft mc) {
        if (button == 0) {
            if (i == lastClickedIndex && (System.currentTimeMillis() - lastClickTime) < 350) {
                editingTabIndex = i;
                renameField = new GuiTextField(0, mc.fontRendererObj, 0, 0, 100, 12);
                renameField.setEnableBackgroundDrawing(false);
                renameField.setText(data.tabs.get(i));
                renameField.setFocused(true);
            } else {
                selectedTabIndex = i;
                pendingDeleteIndex = -1;
                editingTabIndex = -1;
            }
        } else if (button == 1) {
            if (pendingDeleteIndex == i) {
                data.deleteTab(i);
                selectedTabIndex = 0;
                pendingDeleteIndex = -1;
            } else {
                pendingDeleteIndex = i;
            }
        }
        lastClickTime = System.currentTimeMillis();
        lastClickedIndex = i;
    }

    @SubscribeEvent
    public void onKeyTyped(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (isSettingsOpen) {
            if (Keyboard.getEventKey() == Keyboard.KEY_ESCAPE) isSettingsOpen = false;
            else settings.keyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey());
            event.setCanceled(true);
            return;
        }
        if (editingTabIndex != -1 && renameField != null) {
            if (Keyboard.getEventKey() == Keyboard.KEY_RETURN) {
                if (!renameField.getText().trim().isEmpty()) {
                    data.tabs.set(editingTabIndex, renameField.getText().trim());
                    data.save();
                }
                editingTabIndex = -1;
            } else if (Keyboard.getEventKey() == Keyboard.KEY_ESCAPE) editingTabIndex = -1;
            else renameField.textboxKeyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey());
            event.setCanceled(true);
        }
    }
}