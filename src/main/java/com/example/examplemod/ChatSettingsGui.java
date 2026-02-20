package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;

public class ChatSettingsGui {
    private final ChatTabData data;
    private GuiTextField selInput, topInput, bgInput, filterInput;
    private String currentPage = "CUSTOM"; // "CUSTOM" or "FILTER"
    private int selectedFilterTab = 0;

    public ChatSettingsGui(ChatTabData data) {
        this.data = data;
        Minecraft mc = Minecraft.getMinecraft();
        selInput = new GuiTextField(0, mc.fontRendererObj, 0, 0, 60, 12);
        topInput = new GuiTextField(1, mc.fontRendererObj, 0, 0, 60, 12);
        bgInput = new GuiTextField(2, mc.fontRendererObj, 0, 0, 60, 12);
        filterInput = new GuiTextField(3, mc.fontRendererObj, 0, 0, 150, 12);

        selInput.setText(data.colorSelection);
        topInput.setText(data.colorTopBar);
        bgInput.setText(data.colorBackground);
        updateFilterTextField();
    }

    private void updateFilterTextField() {
        filterInput.setText(data.tabFilters.getOrDefault(selectedFilterTab, ""));
    }

    public void draw(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = (sr.getScaledWidth() - 250) / 2;
        int y = (sr.getScaledHeight() - 200) / 2;

        Gui.drawRect(x, y, x + 250, y + 200, 0xFF1A1E24);
        Gui.drawRect(x, y, x + 250, y + 25, 0xFF000000);

        // Tabs for the Settings Menu
        drawNavButton("Customization", x + 5, y + 5, currentPage.equals("CUSTOM"), mouseX, mouseY);
        drawNavButton("Filters", x + 100, y + 5, currentPage.equals("FILTER"), mouseX, mouseY);

        if (currentPage.equals("CUSTOM")) {
            renderSetting(x + 20, y + 50, "Selection (#):", selInput);
            renderSetting(x + 20, y + 80, "Top Bar (#):", topInput);
            renderSetting(x + 20, y + 110, "Background (#):", bgInput);
        } else {
            mc.fontRendererObj.drawString("Select Tab:", x + 20, y + 40, 0xAAAAAA);
            int tx = x + 20;
            for (int i = 0; i < data.tabs.size(); i++) {
                int color = (i == selectedFilterTab) ? 0x00FFFF : 0xFFFFFF;
                mc.fontRendererObj.drawString(data.tabs.get(i), tx, y + 55, color);
                tx += mc.fontRendererObj.getStringWidth(data.tabs.get(i)) + 10;
            }

            mc.fontRendererObj.drawString("Keywords (comma separated):", x + 20, y + 85, 0xAAAAAA);
            filterInput.xPosition = x + 20;
            filterInput.yPosition = y + 100;
            filterInput.drawTextBox();
            mc.fontRendererObj.drawString("Example: hello,smith,info", x + 20, y + 115, 0x555555);
        }

        mc.fontRendererObj.drawString("CLOSE [ESC]", x + 180, y + 185, 0x555555);
    }

    private void drawNavButton(String text, int x, int y, boolean active, int mx, int my) {
        int w = Minecraft.getMinecraft().fontRendererObj.getStringWidth(text) + 10;
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 15;
        Gui.drawRect(x, y, x + w, y + 15, active ? 0xFF333333 : (hover ? 0xFF222222 : 0x00000000));
        Minecraft.getMinecraft().fontRendererObj.drawString(text, x + 5, y + 4, active ? 0x00FFFF : 0xFFFFFF);
    }

    private void renderSetting(int x, int y, String label, GuiTextField field) {
        Minecraft.getMinecraft().fontRendererObj.drawString(label, x, y, 0xCCCCCC);
        field.xPosition = x + 100;
        field.yPosition = y - 2;
        field.drawTextBox();
    }

    public void mouseClicked(int mouseX, int mouseY, int btn) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int x = (sr.getScaledWidth() - 250) / 2;
        int y = (sr.getScaledHeight() - 200) / 2;

        if (mouseX >= x + 5 && mouseX <= x + 90 && mouseY >= y + 5 && mouseY <= y + 20) currentPage = "CUSTOM";
        if (mouseX >= x + 100 && mouseX <= x + 180 && mouseY >= y + 5 && mouseY <= y + 20) currentPage = "FILTER";

        if (currentPage.equals("CUSTOM")) {
            selInput.mouseClicked(mouseX, mouseY, btn);
            topInput.mouseClicked(mouseX, mouseY, btn);
            bgInput.mouseClicked(mouseX, mouseY, btn);
        } else {
            filterInput.mouseClicked(mouseX, mouseY, btn);
            int tx = x + 20;
            for (int i = 0; i < data.tabs.size(); i++) {
                int tw = Minecraft.getMinecraft().fontRendererObj.getStringWidth(data.tabs.get(i));
                if (mouseX >= tx && mouseX <= tx + tw && mouseY >= y + 55 && mouseY <= y + 65) {
                    selectedFilterTab = i;
                    updateFilterTextField();
                }
                tx += tw + 10;
            }
        }
    }

    public void keyTyped(char c, int code) {
        if (currentPage.equals("CUSTOM")) {
            selInput.textboxKeyTyped(c, code);
            topInput.textboxKeyTyped(c, code);
            bgInput.textboxKeyTyped(c, code);
            data.colorSelection = selInput.getText();
            data.colorTopBar = topInput.getText();
            data.colorBackground = bgInput.getText();
        } else {
            filterInput.textboxKeyTyped(c, code);
            data.tabFilters.put(selectedFilterTab, filterInput.getText());
        }
        data.save();
    }
}