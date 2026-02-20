package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import java.util.stream.Collectors;
import java.util.*;

public class ChatSettingsGui {
    private final ChatTabData data;
    private GuiTextField selInput, topInput, bgInput, fontSearch;
    private List<String> popularFonts = Arrays.asList(
            "Roboto", "Open Sans", "Lato", "Montserrat", "Oswald", "Source Sans Pro", "Slabo 27px",
            "Raleway", "PT Sans", "Merriweather", "Noto Sans", "Ubuntu", "Lora", "Playfair Display",
            "Bebas Neue", "Poppins", "Inter", "Muli", "Quicksand", "Titillium Web", "Fira Sans"
    ); // Add up to 50 here

    private List<String> filteredFonts;

    public ChatSettingsGui(ChatTabData data) {
        this.data = data;
        Minecraft mc = Minecraft.getMinecraft();
        selInput = new GuiTextField(0, mc.fontRendererObj, 0, 0, 60, 12);
        topInput = new GuiTextField(1, mc.fontRendererObj, 0, 0, 60, 12);
        bgInput = new GuiTextField(2, mc.fontRendererObj, 0, 0, 60, 12);
        fontSearch = new GuiTextField(3, mc.fontRendererObj, 0, 0, 180, 12);

        selInput.setText(data.colorSelection);
        topInput.setText(data.colorTopBar);
        bgInput.setText(data.colorBackground);
        fontSearch.setText("");
        updateFilter();
    }

    private void updateFilter() {
        filteredFonts = popularFonts.stream()
                .filter(f -> f.toLowerCase().contains(fontSearch.getText().toLowerCase()))
                .limit(5)
                .collect(Collectors.toList());
    }

    public void draw(int mouseX, int mouseY) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int x = (sr.getScaledWidth() - 250) / 2;
        int y = (sr.getScaledHeight() - 220) / 2;
        Minecraft mc = Minecraft.getMinecraft();

        Gui.drawRect(x, y, x + 250, y + 220, 0xFF1A1E24);
        mc.fontRendererObj.drawString("Appearance & Fonts", x + 10, y + 6, 0xFFFFFF);

        renderSetting(x + 10, y + 30, "Selection:", selInput);
        renderSetting(x + 10, y + 55, "Top Bar:", topInput);
        renderSetting(x + 10, y + 80, "Background:", bgInput);

        // Font Search Section
        mc.fontRendererObj.drawString("Search Fonts (Google Fonts):", x + 10, y + 110, 0xAAAAAA);
        fontSearch.xPosition = x + 10;
        fontSearch.yPosition = y + 122;
        fontSearch.drawTextBox();

        // Font Results
        int fy = y + 140;
        for (String font : filteredFonts) {
            boolean hovering = mouseX >= x + 10 && mouseX <= x + 200 && mouseY >= fy && mouseY <= fy + 12;
            int color = font.equalsIgnoreCase(data.fontName) ? 0x00FFFF : (hovering ? 0xFFFFFF : 0x888888);
            mc.fontRendererObj.drawString(font, x + 15, fy, color);
            fy += 12;
        }
    }

    private void renderSetting(int x, int y, String label, GuiTextField field) {
        Minecraft.getMinecraft().fontRendererObj.drawString(label, x, y, 0xCCCCCC);
        field.xPosition = x + 100;
        field.yPosition = y - 2;
        field.drawTextBox();
    }

    public void mouseClicked(int mouseX, int mouseY, int btn) {
        selInput.mouseClicked(mouseX, mouseY, btn);
        topInput.mouseClicked(mouseX, mouseY, btn);
        bgInput.mouseClicked(mouseX, mouseY, btn);
        fontSearch.mouseClicked(mouseX, mouseY, btn);

        // Font Selection Logic
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int x = (sr.getScaledWidth() - 250) / 2;
        int fy = ((sr.getScaledHeight() - 220) / 2) + 140;
        for (String font : filteredFonts) {
            if (mouseX >= x + 10 && mouseX <= x + 200 && mouseY >= fy && mouseY <= fy + 12) {
                data.fontName = font;
                data.save();
                Minecraft.getMinecraft().thePlayer.playSound("gui.button.press", 1.0F, 1.0F);
            }
            fy += 12;
        }
    }

    public void keyTyped(char c, int code) {
        if (fontSearch.isFocused()) {
            fontSearch.textboxKeyTyped(c, code);
            updateFilter();
        } else {
            selInput.textboxKeyTyped(c, code);
            topInput.textboxKeyTyped(c, code);
            bgInput.textboxKeyTyped(c, code);
            data.colorSelection = selInput.getText();
            data.colorTopBar = topInput.getText();
            data.colorBackground = bgInput.getText();
            data.save();
        }
    }
}