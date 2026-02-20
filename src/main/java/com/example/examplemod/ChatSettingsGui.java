package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;

public class ChatSettingsGui {
    private final ChatTabData data;
    private GuiTextField selInput, topInput, bgInput, filterInput;
    private String currentPage = "CUSTOM";
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
        updateFilterPage();
    }

    private void updateFilterPage() { filterInput.setText(data.tabFilters.getOrDefault(selectedFilterTab, "")); }

    public void draw(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = (sr.getScaledWidth() - 250) / 2;
        int y = (sr.getScaledHeight() - 200) / 2;
        Gui.drawRect(x, y, x + 250, y + 200, 0xFF1A1E24);
        Gui.drawRect(x, y, x + 250, y + 25, 0xFF000000);
        drawNavButton("Customization", x + 5, y + 5, currentPage.equals("CUSTOM"), mouseX, mouseY);
        drawNavButton("Filters", x + 100, y + 5, currentPage.equals("FILTER"), mouseX, mouseY);
        if (currentPage.equals("CUSTOM")) {
            renderSetting(x + 20, y + 50, "Selection (#):", selInput);
            renderSetting(x + 20, y + 80, "Top Bar (#):", topInput);
            renderSetting(x + 20, y + 110, "Background (#):", bgInput);
            drawCheckbox(x + 20, y + 140, data.hideDefaultChat, "Remove Default Chat", mc);
            drawCheckbox(x + 20, y + 160, data.saveChatLog, "Save All Chat Log", mc);
        } else {
            mc.fontRendererObj.drawString("Tab: " + data.tabs.get(selectedFilterTab), x + 20, y + 40, 0x00FFFF);
            mc.fontRendererObj.drawString("Keywords:", x + 20, y + 60, 0xAAAAAA);
            filterInput.xPosition = x + 20; filterInput.yPosition = y + 72; filterInput.drawTextBox();
            boolean isChecked = data.serverMessageFilters.getOrDefault(selectedFilterTab, false);
            drawCheckbox(x + 20, y + 100, isChecked, "Include Server Messages", mc);
            int tx = x + 20;
            for (int i = 0; i < data.tabs.size(); i++) {
                int color = (i == selectedFilterTab) ? 0x00FFFF : 0x555555;
                mc.fontRendererObj.drawString("[" + data.tabs.get(i) + "]", tx, y + 160, color);
                tx += mc.fontRendererObj.getStringWidth("[" + data.tabs.get(i) + "]") + 5;
            }
        }
    }

    private void drawCheckbox(int x, int y, boolean active, String label, Minecraft mc) {
        Gui.drawRect(x, y, x + 10, y + 10, 0xFF333333);
        if (active) mc.fontRendererObj.drawString("v", x + 2, y + 1, 0x00FFFF);
        mc.fontRendererObj.drawString(label, x + 15, y + 1, 0xFFFFFF);
    }

    private void drawNavButton(String text, int x, int y, boolean active, int mx, int my) {
        int w = Minecraft.getMinecraft().fontRendererObj.getStringWidth(text) + 10;
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 15;
        Gui.drawRect(x, y, x + w, y + 15, active ? 0xFF333333 : (hover ? 0xFF222222 : 0x00000000));
        Minecraft.getMinecraft().fontRendererObj.drawString(text, x + 5, y + 4, active ? 0x00FFFF : 0xFFFFFF);
    }

    private void renderSetting(int x, int y, String label, GuiTextField field) {
        Minecraft.getMinecraft().fontRendererObj.drawString(label, x, y, 0xCCCCCC);
        field.xPosition = x + 100; field.yPosition = y - 2; field.drawTextBox();
    }

    public void mouseClicked(int mouseX, int mouseY, int btn) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int x = (sr.getScaledWidth() - 250) / 2;
        int y = (sr.getScaledHeight() - 200) / 2;
        if (mouseX >= x + 5 && mouseX <= x + 90 && mouseY >= y + 5 && mouseY <= y + 20) currentPage = "CUSTOM";
        if (mouseX >= x + 100 && mouseX <= x + 180 && mouseY >= y + 5 && mouseY <= y + 20) currentPage = "FILTER";
        if (currentPage.equals("CUSTOM")) {
            selInput.mouseClicked(mouseX, mouseY, btn); topInput.mouseClicked(mouseX, mouseY, btn); bgInput.mouseClicked(mouseX, mouseY, btn);
            if (mouseX >= x + 20 && mouseX <= x + 150 && mouseY >= y + 140 && mouseY <= y + 150) data.hideDefaultChat = !data.hideDefaultChat;
            if (mouseX >= x + 20 && mouseX <= x + 150 && mouseY >= y + 160 && mouseY <= y + 170) data.saveChatLog = !data.saveChatLog;
        } else {
            filterInput.mouseClicked(mouseX, mouseY, btn);
            if (mouseX >= x + 20 && mouseX <= x + 150 && mouseY >= y + 100 && mouseY <= y + 110) {
                boolean current = data.serverMessageFilters.getOrDefault(selectedFilterTab, false);
                data.serverMessageFilters.put(selectedFilterTab, !current);
            }
            int tx = x + 20;
            for (int i = 0; i < data.tabs.size(); i++) {
                int tw = Minecraft.getMinecraft().fontRendererObj.getStringWidth("[" + data.tabs.get(i) + "]");
                if (mouseX >= tx && mouseX <= tx + tw && mouseY >= y + 160 && mouseY <= y + 170) { selectedFilterTab = i; updateFilterPage(); }
                tx += tw + 5;
            }
        }
        data.save();
    }

    public void keyTyped(char c, int code) {
        if (currentPage.equals("CUSTOM")) {
            selInput.textboxKeyTyped(c, code); topInput.textboxKeyTyped(c, code); bgInput.textboxKeyTyped(c, code);
            data.colorSelection = selInput.getText(); data.colorTopBar = topInput.getText(); data.colorBackground = bgInput.getText();
        } else { filterInput.textboxKeyTyped(c, code); data.tabFilters.put(selectedFilterTab, filterInput.getText()); }
        data.save();
    }
}