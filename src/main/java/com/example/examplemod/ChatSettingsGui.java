package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;

public class ChatSettingsGui {
    private final ChatTabData data;
    private GuiTextField selInput, topInput, bgInput;

    public ChatSettingsGui(ChatTabData data) {
        this.data = data;
        Minecraft mc = Minecraft.getMinecraft();
        selInput = new GuiTextField(0, mc.fontRendererObj, 0, 0, 60, 12);
        topInput = new GuiTextField(1, mc.fontRendererObj, 0, 0, 60, 12);
        bgInput = new GuiTextField(2, mc.fontRendererObj, 0, 0, 60, 12);

        selInput.setText(data.colorSelection);
        topInput.setText(data.colorTopBar);
        bgInput.setText(data.colorBackground);
    }

    public void draw(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = (sr.getScaledWidth() - 220) / 2;
        int y = (sr.getScaledHeight() - 180) / 2;

        Gui.drawRect(x, y, x + 220, y + 180, 0xFF1A1E24); // Solid BG
        Gui.drawRect(x, y, x + 220, y + 20, 0xFF000000); // Header
        mc.fontRendererObj.drawString("Appearance Settings", x + 10, y + 6, 0xFFFFFF);

        // Inputs
        renderSetting(x + 10, y + 40, "Selection Color (#):", selInput);
        renderSetting(x + 10, y + 70, "Top Bar Color (#):", topInput);
        renderSetting(x + 10, y + 100, "Background Color (#):", bgInput);

        // Close Button
        mc.fontRendererObj.drawString("CLOSE [ESC]", x + 160, y + 165, 0xAAAAAA);
    }

    private void renderSetting(int x, int y, String label, GuiTextField field) {
        Minecraft.getMinecraft().fontRendererObj.drawString(label, x, y, 0xCCCCCC);
        field.xPosition = x + 120;
        field.yPosition = y - 2;
        field.drawTextBox();
    }

    public void mouseClicked(int mouseX, int mouseY, int btn) {
        selInput.mouseClicked(mouseX, mouseY, btn);
        topInput.mouseClicked(mouseX, mouseY, btn);
        bgInput.mouseClicked(mouseX, mouseY, btn);
    }

    public void keyTyped(char c, int code) {
        selInput.textboxKeyTyped(c, code);
        topInput.textboxKeyTyped(c, code);
        bgInput.textboxKeyTyped(c, code);

        // Live update the data
        data.colorSelection = selInput.getText();
        data.colorTopBar = topInput.getText();
        data.colorBackground = bgInput.getText();
        data.save();
    }
}