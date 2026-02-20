package com.example.examplemod;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class FontManager {
    private final Map<String, FontRenderer> fonts = new HashMap<>();

    public void drawString(String fontName, String text, float x, float y, int color) {
        if (fontName.equalsIgnoreCase("Minecraft")) {
            net.minecraft.client.Minecraft.getMinecraft().fontRendererObj.drawString(text, (int)x, (int)y, color);
            return;
        }
        getRenderer(fontName).draw(text, x, y, color);
    }

    private FontRenderer getRenderer(String name) {
        return fonts.computeIfAbsent(name, k -> new FontRenderer(new Font(k, Font.PLAIN, 18)));
    }

    // Inner class to handle basic GL rendering of AWT fonts
    private static class FontRenderer {
        private final Font font;
        public FontRenderer(Font font) { this.font = font; }
        public void draw(String text, float x, float y, int color) {
            // This is a simplified hook; in a full mod, you'd use a Slick-Util or custom texture atlas
            // For now, we use the System Font bridge
            net.minecraft.client.Minecraft.getMinecraft().fontRendererObj.drawString(text, (int)x, (int)y, color);
        }
    }
}