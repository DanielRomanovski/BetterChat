package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;

public class ChatSettingsGui {
    private final ChatTabData data;
    private GuiTextField selInput, topInput, bgInput, filterInput, prefixInput, suffixInput;
    private String currentPage = "CUSTOM";
    private int selectedFilterTab = 0;

    public ChatSettingsGui(ChatTabData data) {
        this.data = data;
        Minecraft mc = Minecraft.getMinecraft();
        selInput = new GuiTextField(0, mc.fontRendererObj, 0, 0, 60, 12);
        topInput = new GuiTextField(1, mc.fontRendererObj, 0, 0, 60, 12);
        bgInput = new GuiTextField(2, mc.fontRendererObj, 0, 0, 60, 12);
        filterInput = new GuiTextField(3, mc.fontRendererObj, 0, 0, 150, 12);
        prefixInput = new GuiTextField(4, mc.fontRendererObj, 0, 0, 70, 12);
        suffixInput = new GuiTextField(5, mc.fontRendererObj, 0, 0, 70, 12);
        selInput.setText(data.colorSelection);
        topInput.setText(data.colorTopBar);
        bgInput.setText(data.colorBackground);
        updateFilterPage();
    }

    private void updateFilterPage() {
        filterInput.setText(data.tabFilters.getOrDefault(selectedFilterTab, ""));
        prefixInput.setText(data.tabPrefixes.getOrDefault(selectedFilterTab, ""));
        suffixInput.setText(data.tabSuffixes.getOrDefault(selectedFilterTab, ""));
    }

    public void draw(int mx, int my) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = (sr.getScaledWidth() - 250) / 2;
        int y = (sr.getScaledHeight() - 200) / 2;
        Gui.drawRect(x, y, x + 250, y + 200, 0xFF1A1E24);
        Gui.drawRect(x, y, x + 250, y + 25, 0xFF000000);
        drawNavButton("Customization", x + 5, y + 5, currentPage.equals("CUSTOM"), mx, my);
        drawNavButton("Filters", x + 100, y + 5, currentPage.equals("FILTER"), mx, my);

        if (currentPage.equals("CUSTOM")) {
            renderSetting(x + 20, y + 50, "Selection (#):", selInput);
            renderSetting(x + 20, y + 80, "Top Bar (#):", topInput);
            renderSetting(x + 20, y + 110, "Background (#):", bgInput);
            drawCheckbox(x + 20, y + 140, data.hideDefaultChat, "Remove Default Chat", mc);
            drawCheckbox(x + 20, y + 160, data.saveChatLog, "Save All Chat Log", mc);
            drawCheckbox(x + 20, y + 180, data.isLocked, "Lock Chat At Current Position", mc);
        } else {
            mc.fontRendererObj.drawString("Tab: " + data.tabs.get(selectedFilterTab), x + 20, y + 35, 0x00FFFF);
            mc.fontRendererObj.drawString("Prefix:", x + 20, y + 50, 0xAAAAAA);
            prefixInput.xPosition = x + 60; prefixInput.yPosition = y + 48; prefixInput.drawTextBox();
            mc.fontRendererObj.drawString("Suffix:", x + 140, y + 50, 0xAAAAAA);
            suffixInput.xPosition = x + 175; suffixInput.yPosition = y + 48; suffixInput.drawTextBox();
            mc.fontRendererObj.drawString("Keywords:", x + 20, y + 68, 0xAAAAAA);
            filterInput.xPosition = x + 20; filterInput.yPosition = y + 78; filterInput.drawTextBox();
            drawCheckbox(x + 20, y + 95, data.includeAllFilters.getOrDefault(selectedFilterTab, false), "Include ALL Messages", mc);
            drawCheckbox(x + 20, y + 110, data.includeCommandsFilters.getOrDefault(selectedFilterTab, false), "Include Commands", mc);
            drawCheckbox(x + 20, y + 125, data.serverMessageFilters.getOrDefault(selectedFilterTab, false), "Include Server Messages", mc);
            drawCheckbox(x + 20, y + 140, data.includePlayersFilters.getOrDefault(selectedFilterTab, false), "Include Player Messages", mc);
            int tx = x + 20;
            for (int i = 0; i < data.tabs.size(); i++) {
                int color = (i == selectedFilterTab) ? 0x00FFFF : 0x555555;
                mc.fontRendererObj.drawString("[" + data.tabs.get(i) + "]", tx, y + 160, color);
                tx += mc.fontRendererObj.getStringWidth("[" + data.tabs.get(i) + "]") + 5;
            }
        }
    }

    private void drawCheckbox(int x, int y, boolean active, String label, Minecraft mc) {
        Gui.drawRect(x, y, x + 8, y + 8, 0xFF333333);
        if (active) mc.fontRendererObj.drawString("v", x + 1, y - 1, 0x00FFFF);
        mc.fontRendererObj.drawString(label, x + 12, y, 0xFFFFFF);
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

    public void mouseClicked(int mx, int my, int btn) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int x = (sr.getScaledWidth() - 250) / 2;
        int y = (sr.getScaledHeight() - 200) / 2;
        if (mx >= x + 5 && mx <= x + 90 && my >= y + 5 && my <= y + 20) currentPage = "CUSTOM";
        if (mx >= x + 100 && mx <= x + 180 && my >= y + 5 && my <= y + 20) currentPage = "FILTER";
        if (currentPage.equals("CUSTOM")) {
            selInput.mouseClicked(mx, my, btn); topInput.mouseClicked(mx, my, btn); bgInput.mouseClicked(mx, my, btn);
            if (mx >= x + 20 && mx <= x + 150 && my >= y + 140 && my <= y + 150) data.hideDefaultChat = !data.hideDefaultChat;
            if (mx >= x + 20 && mx <= x + 150 && my >= y + 160 && my <= y + 170) data.saveChatLog = !data.saveChatLog;
            if (mx >= x + 20 && mx <= x + 200 && my >= y + 180 && my <= y + 190) data.isLocked = !data.isLocked;
        } else {
            filterInput.mouseClicked(mx, my, btn); prefixInput.mouseClicked(mx, my, btn); suffixInput.mouseClicked(mx, my, btn);
            if (mx >= x + 20 && mx <= x + 150 && my >= y + 95 && my <= y + 105) data.includeAllFilters.put(selectedFilterTab, !data.includeAllFilters.getOrDefault(selectedFilterTab, false));
            if (mx >= x + 20 && mx <= x + 150 && my >= y + 110 && my <= y + 120) data.includeCommandsFilters.put(selectedFilterTab, !data.includeCommandsFilters.getOrDefault(selectedFilterTab, false));
            if (mx >= x + 20 && mx <= x + 150 && my >= y + 125 && my <= y + 135) data.serverMessageFilters.put(selectedFilterTab, !data.serverMessageFilters.getOrDefault(selectedFilterTab, false));
            if (mx >= x + 20 && mx <= x + 150 && my >= y + 140 && my <= y + 150) data.includePlayersFilters.put(selectedFilterTab, !data.includePlayersFilters.getOrDefault(selectedFilterTab, false));
            int tx = x + 20;
            for (int i = 0; i < data.tabs.size(); i++) {
                int tw = Minecraft.getMinecraft().fontRendererObj.getStringWidth("[" + data.tabs.get(i) + "]");
                if (mx >= tx && mx <= tx + tw && my >= y + 160 && my <= y + 170) { selectedFilterTab = i; updateFilterPage(); }
                tx += tw + 5;
            }
        }
        data.save();
    }

    public void keyTyped(char c, int code) {
        if (currentPage.equals("CUSTOM")) {
            if (selInput.isFocused()) selInput.textboxKeyTyped(c, code);
            if (topInput.isFocused()) topInput.textboxKeyTyped(c, code);
            if (bgInput.isFocused()) bgInput.textboxKeyTyped(c, code);
            data.colorSelection = selInput.getText(); data.colorTopBar = topInput.getText(); data.colorBackground = bgInput.getText();
        } else {
            if (filterInput.isFocused()) filterInput.textboxKeyTyped(c, code);
            if (prefixInput.isFocused()) prefixInput.textboxKeyTyped(c, code);
            if (suffixInput.isFocused()) suffixInput.textboxKeyTyped(c, code);
            data.tabFilters.put(selectedFilterTab, filterInput.getText());
            data.tabPrefixes.put(selectedFilterTab, prefixInput.getText());
            data.tabSuffixes.put(selectedFilterTab, suffixInput.getText());
        }
        data.save();
    }
}