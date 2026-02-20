package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

public class ChatSettingsGui {
    private final ChatTabData data;
    private GuiTextField selIn, topIn, bgIn, txtIn, timeIn, filterInput, exclusionInput, prefixInput, suffixInput;
    private String currentPage = "CUSTOM";
    private int selectedFilterTab = 0;

    public ChatSettingsGui(ChatTabData data) {
        this.data = data;
        Minecraft mc = Minecraft.getMinecraft();
        selIn = new GuiTextField(0, mc.fontRendererObj, 0, 0, 50, 10);
        topIn = new GuiTextField(1, mc.fontRendererObj, 0, 0, 50, 10);
        bgIn = new GuiTextField(2, mc.fontRendererObj, 0, 0, 50, 10);
        txtIn = new GuiTextField(3, mc.fontRendererObj, 0, 0, 50, 10);
        timeIn = new GuiTextField(4, mc.fontRendererObj, 0, 0, 50, 10);
        filterInput = new GuiTextField(5, mc.fontRendererObj, 0, 0, 110, 10);
        exclusionInput = new GuiTextField(8, mc.fontRendererObj, 0, 0, 110, 10);
        prefixInput = new GuiTextField(6, mc.fontRendererObj, 0, 0, 70, 10);
        suffixInput = new GuiTextField(7, mc.fontRendererObj, 0, 0, 70, 10);
        refreshInputs();
    }

    private void refreshInputs() {
        selIn.setText(data.colorSelection); topIn.setText(data.colorTopBar); bgIn.setText(data.colorBackground);
        txtIn.setText(data.colorText); timeIn.setText(data.colorTime);
        updateFilterPage();
    }

    private void updateFilterPage() {
        if (selectedFilterTab >= data.tabs.size()) selectedFilterTab = 0;
        filterInput.setText(data.tabFilters.getOrDefault(selectedFilterTab, ""));
        exclusionInput.setText(data.tabExclusions.getOrDefault(selectedFilterTab, ""));
        prefixInput.setText(data.tabPrefixes.getOrDefault(selectedFilterTab, ""));
        suffixInput.setText(data.tabSuffixes.getOrDefault(selectedFilterTab, ""));
    }

    public void draw(int mx, int my) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = (sr.getScaledWidth() - 280) / 2;
        int y = (sr.getScaledHeight() - 220) / 2;
        Gui.drawRect(x, y, x + 280, y + 220, 0xFF1A1E24);
        Gui.drawRect(x, y, x + 280, y + 25, 0xFF000000);
        drawNavButton("Colors & Style", x + 5, y + 5, currentPage.equals("CUSTOM"), mx, my);
        drawNavButton("Filters", x + 100, y + 5, currentPage.equals("FILTER"), mx, my);

        if (currentPage.equals("CUSTOM")) {
            int row = y + 35;
            drawColorRow("Selection", x + 10, row, selIn, data.opacSelection, val -> data.opacSelection = val, mx, my); row += 22;
            drawColorRow("Top Bar", x + 10, row, topIn, data.opacTopBar, val -> data.opacTopBar = val, mx, my); row += 22;
            drawColorRow("Background", x + 10, row, bgIn, data.opacBackground, val -> data.opacBackground = val, mx, my); row += 22;
            drawColorRow("Text", x + 10, row, txtIn, data.opacText, val -> data.opacText = val, mx, my); row += 22;
            drawColorRow("Timestamp", x + 10, row, timeIn, data.opacTime, val -> data.opacTime = val, mx, my); row += 25;

            drawCheckbox(x + 10, row, data.hideDefaultChat, "Hide Default Chat", mc); row += 12;
            drawCheckbox(x + 10, row, data.saveChatLog, "Save Chat History", mc); row += 12;
            drawCheckbox(x + 10, row, data.isLocked, "Lock Chat Position", mc); row += 12;
            drawCheckbox(x + 10, row, data.showTimeStamps, "Show Timestamps", mc); row += 12;
            drawCheckbox(x + 10, row, data.showNotifications, "Show Notifications", mc); row += 15;

            boolean resetHover = mx >= x + 180 && mx <= x + 270 && my >= y + 195 && my <= y + 210;
            Gui.drawRect(x + 180, y + 195, x + 270, y + 210, resetHover ? 0xFF993333 : 0xFF662222);
            mc.fontRendererObj.drawString("Reset Defaults", x + 187, y + 199, 0xFFFFFF);
        } else {
            if (selectedFilterTab >= data.tabs.size()) selectedFilterTab = 0;

            mc.fontRendererObj.drawString("Tab: " + data.tabs.get(selectedFilterTab), x + 20, y + 35, 0x00FFFF);
            mc.fontRendererObj.drawString("Prefix:", x + 20, y + 50, 0xAAAAAA);
            prefixInput.xPosition = x + 60; prefixInput.yPosition = y + 48; prefixInput.drawTextBox();
            mc.fontRendererObj.drawString("Suffix:", x + 140, y + 50, 0xAAAAAA);
            suffixInput.xPosition = x + 175; suffixInput.yPosition = y + 48; suffixInput.drawTextBox();

            mc.fontRendererObj.drawString("Inclusion (Keywords):", x + 20, y + 68, 0xAAAAAA);
            filterInput.xPosition = x + 20; filterInput.yPosition = y + 78; filterInput.drawTextBox();

            mc.fontRendererObj.drawString("Exclusion (Keywords):", x + 145, y + 68, 0xAAAAAA);
            exclusionInput.xPosition = x + 145; exclusionInput.yPosition = y + 78; exclusionInput.drawTextBox();

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

    private void drawColorRow(String label, int x, int y, GuiTextField field, int opac, java.util.function.Consumer<Integer> opacSetter, int mx, int my) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.fontRendererObj.drawString(label, x, y + 2, 0xCCCCCC);
        field.xPosition = x + 70; field.yPosition = y; field.drawTextBox();

        int sliderX = x + 130; int sliderW = 100;
        Gui.drawRect(sliderX, y + 4, sliderX + sliderW, y + 6, 0xFF333333);
        int thumbX = sliderX + (int)((opac / 255.0) * sliderW);
        Gui.drawRect(thumbX - 2, y + 2, thumbX + 2, y + 8, 0xFF00FFFF);
        mc.fontRendererObj.drawString(opac + "", sliderX + sliderW + 5, y + 2, 0xAAAAAA);

        if (Mouse.isButtonDown(0)) {
            if (mx >= sliderX && mx <= sliderX + sliderW && my >= y && my <= y + 10) {
                int newVal = (int)(((mx - sliderX) / (double)sliderW) * 255);
                opacSetter.accept(Math.max(0, Math.min(255, newVal)));
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

    public void mouseClicked(int mx, int my, int btn) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int x = (sr.getScaledWidth() - 280) / 2;
        int y = (sr.getScaledHeight() - 220) / 2;
        if (mx >= x + 5 && mx <= x + 90 && my >= y + 5 && my <= y + 20) currentPage = "CUSTOM";
        if (mx >= x + 100 && mx <= x + 180 && my >= y + 5 && my <= y + 20) currentPage = "FILTER";

        if (currentPage.equals("CUSTOM")) {
            selIn.mouseClicked(mx, my, btn); topIn.mouseClicked(mx, my, btn); bgIn.mouseClicked(mx, my, btn);
            txtIn.mouseClicked(mx, my, btn); timeIn.mouseClicked(mx, my, btn);

            int row = y + 145;
            if (mx >= x + 10 && mx <= x + 150 && my >= row && my <= row + 10) data.hideDefaultChat = !data.hideDefaultChat; row += 12;
            if (mx >= x + 10 && mx <= x + 150 && my >= row && my <= row + 10) data.saveChatLog = !data.saveChatLog; row += 12;
            if (mx >= x + 10 && mx <= x + 200 && my >= row && my <= row + 10) {
                data.isLocked = !data.isLocked;
                if (data.isLocked) {
                    data.lockedX = data.windowX; data.lockedY = data.windowY;
                    data.lockedW = data.windowWidth; data.lockedH = data.windowHeight;
                    data.lockedResW = sr.getScaledWidth(); data.lockedResH = sr.getScaledHeight();
                }
            } row += 12;
            if (mx >= x + 10 && mx <= x + 200 && my >= row && my <= row + 10) data.showTimeStamps = !data.showTimeStamps; row += 12;
            if (mx >= x + 10 && mx <= x + 200 && my >= row && my <= row + 10) data.showNotifications = !data.showNotifications;

            if (mx >= x + 180 && mx <= x + 270 && my >= y + 195 && my <= y + 210) {
                data.resetToDefaults();
                refreshInputs();
            }
        } else {
            if (selectedFilterTab >= data.tabs.size()) selectedFilterTab = 0;
            filterInput.mouseClicked(mx, my, btn); exclusionInput.mouseClicked(mx, my, btn);
            prefixInput.mouseClicked(mx, my, btn); suffixInput.mouseClicked(mx, my, btn);
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
            if (selIn.isFocused()) selIn.textboxKeyTyped(c, code);
            if (topIn.isFocused()) topIn.textboxKeyTyped(c, code);
            if (bgIn.isFocused()) bgIn.textboxKeyTyped(c, code);
            if (txtIn.isFocused()) txtIn.textboxKeyTyped(c, code);
            if (timeIn.isFocused()) timeIn.textboxKeyTyped(c, code);
            data.colorSelection = selIn.getText(); data.colorTopBar = topIn.getText(); data.colorBackground = bgIn.getText();
            data.colorText = txtIn.getText(); data.colorTime = timeIn.getText();
        } else {
            if (selectedFilterTab >= data.tabs.size()) selectedFilterTab = 0;
            if (filterInput.isFocused()) filterInput.textboxKeyTyped(c, code);
            if (exclusionInput.isFocused()) exclusionInput.textboxKeyTyped(c, code);
            if (prefixInput.isFocused()) prefixInput.textboxKeyTyped(c, code);
            if (suffixInput.isFocused()) suffixInput.textboxKeyTyped(c, code);
            data.tabFilters.put(selectedFilterTab, filterInput.getText());
            data.tabExclusions.put(selectedFilterTab, exclusionInput.getText());
            data.tabPrefixes.put(selectedFilterTab, prefixInput.getText());
            data.tabSuffixes.put(selectedFilterTab, suffixInput.getText());
        }
        data.save();
    }
}