package com.betterchat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders text using a system AWT font.
 *
 * Each unique (text, targetHeight) pair is rendered at high resolution (BAKE_PT
 * point size) into a BufferedImage, uploaded as a DynamicTexture with GL_LINEAR
 * filtering, then drawn as a scaled-down quad via MC's Tessellator.
 * Scaling down from a high-res bake keeps text smooth and crisp at any size.
 */
public class AwtFontRenderer {

    /** Point size used for baking — high enough to be crisp when scaled down. */
    private static final int BAKE_PT = 64;

    // ── Static per-font-family cache ──────────────────────────────────────────
    private static final Map<String, AwtFontRenderer> FONT_CACHE = new LinkedHashMap<>();

    /** Returns (or creates) a renderer for the given font family. */
    public static AwtFontRenderer get(String fontFamily) {
        if (FONT_CACHE.containsKey(fontFamily)) return FONT_CACHE.get(fontFamily);
        try {
            AwtFontRenderer r = new AwtFontRenderer(fontFamily);
            FONT_CACHE.put(fontFamily, r);
            return r;
        } catch (Exception e) {
            FONT_CACHE.put(fontFamily, null);
            return null;
        }
    }

    public static void clearCache() { FONT_CACHE.clear(); }

    // ── Per-string texture LRU cache (max 512 entries) ────────────────────────
    private static final int MAX_CACHED = 512;

    private final Map<String, CachedString> stringCache =
            new LinkedHashMap<String, CachedString>(MAX_CACHED + 1, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry<String, CachedString> e) {
                    return size() > MAX_CACHED;
                }
            };

    private static class CachedString {
        final ResourceLocation tex;
        /** Pixel width/height of the baked texture (at BAKE_PT). */
        final int bakeW, bakeH;
        CachedString(ResourceLocation tex, int w, int h) {
            this.tex = tex; this.bakeW = w; this.bakeH = h;
        }
    }

    // ── Instance fields ───────────────────────────────────────────────────────
    private final Font        awtFont;
    private final FontMetrics metrics;
    private       int         texCounter = 0;
    private final String      fontKey;

    // ── Constructor ───────────────────────────────────────────────────────────
    private AwtFontRenderer(String fontFamily) {
        this.fontKey = fontFamily.toLowerCase().replaceAll("[^a-z0-9]", "_");
        awtFont  = new Font(fontFamily, Font.PLAIN, BAKE_PT);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gp = probe.createGraphics();
        gp.setFont(awtFont);
        metrics = gp.getFontMetrics();
        gp.dispose();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    /**
     * Draws plain text (no §-codes) at screen position (x, y).
     * The quad is scaled so the text is exactly {@code targetH} pixels tall,
     * downsampling from the high-res bake for crisp results.
     *
     * @param color    ARGB colour; alpha == 0 treated as fully opaque.
     * @param targetH  desired on-screen height in pixels (= lineH from the renderer).
     * @return on-screen pixel width of the drawn string.
     */
    public int drawString(String text, int x, int y, int color, int targetH) {
        if (text == null || text.isEmpty()) return 0;
        CachedString cs = getCached(text);
        if (cs == null) return 0;

        // Scale factor: shrink the high-res bake to fit targetH
        float scale = (float) targetH / cs.bakeH;
        int drawW = Math.max(1, Math.round(cs.bakeW * scale));
        int drawH = targetH;

        float ca = ((color >> 24) & 0xFF) / 255f;
        float cr = ((color >> 16) & 0xFF) / 255f;
        float cg = ((color >>  8) & 0xFF) / 255f;
        float cb = ( color        & 0xFF) / 255f;
        if (ca <= 0f) ca = 1f;

        GlStateManager.pushMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(cr, cg, cb, ca);
        Minecraft.getMinecraft().getTextureManager().bindTexture(cs.tex);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr  = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x,        y,       0).tex(0, 0).endVertex();
        wr.pos(x,        y+drawH, 0).tex(0, 1).endVertex();
        wr.pos(x+drawW,  y+drawH, 0).tex(1, 1).endVertex();
        wr.pos(x+drawW,  y,       0).tex(1, 0).endVertex();
        tess.draw();

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
        return drawW;
    }

    /** Returns the on-screen pixel width of text drawn at the given targetH. */
    public int stringWidth(String text, int targetH) {
        if (text == null || text.isEmpty()) return 0;
        float scale = (float) targetH / metrics.getHeight();
        return Math.max(1, Math.round(metrics.stringWidth(text) * scale));
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private CachedString getCached(String text) {
        CachedString cs = stringCache.get(text);
        if (cs != null) return cs;

        int tw = metrics.stringWidth(text);
        int th = metrics.getHeight();
        if (tw <= 0 || th <= 0) return null;

        // Render white text on transparent background at BAKE_PT resolution
        BufferedImage img = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setFont(awtFont);
        g2.setColor(new java.awt.Color(255, 255, 255, 255));
        g2.drawString(text, 0, metrics.getAscent());
        g2.dispose();

        // Upload via DynamicTexture — 1.8.9 uses DynamicTexture(int w, int h)
        DynamicTexture dt = new DynamicTexture(tw, th);
        int[] texData = dt.getTextureData();
        for (int py = 0; py < th; py++)
            for (int px = 0; px < tw; px++)
                texData[py * tw + px] = img.getRGB(px, py); // ARGB int
        dt.updateDynamicTexture();

        // DynamicTexture.updateDynamicTexture() leaves GL_NEAREST on the texture.
        // Override to GL_LINEAR so the downscaled quad is smooth, not pixelated.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        String locName = "betterchat/str_" + fontKey + "_" + (texCounter++);
        ResourceLocation loc = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation(locName, dt);

        cs = new CachedString(loc, tw, th);
        stringCache.put(text, cs);
        return cs;
    }
}

