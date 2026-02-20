package com.example.examplemod;

import net.minecraft.client.Minecraft;
import java.io.*;
import java.util.*;

public class ChatTabData {
    public final List<String> tabs = new ArrayList<>();
    public final Map<Integer, List<String>> chatHistories = new HashMap<>();
    private final File configFile;

    // Window Position and Size
    public int windowX = 20;
    public int windowY = 20;
    public int windowWidth = 340;
    public int windowHeight = 172;

    // Appearance Settings
    public String colorSelection = "00FFFF";
    public String colorTopBar = "000000";
    public String colorBackground = "1A1E24";
    public String fontName = "Minecraft";

    public ChatTabData() {
        // Saves to .minecraft/config/cleanchat.txt
        this.configFile = new File(Minecraft.getMinecraft().mcDataDir, "config/cleanchat.txt");
        load();
    }

    /**
     * Converts a Hex string to a Minecraft-usable integer color.
     * @param hex The 6-digit hex string (e.g., "FF0000")
     * @param alpha The transparency level (0-255)
     */
    public int getHex(String hex, int alpha) {
        try {
            // Remove '#' if the user accidentally included it
            String cleanHex = hex.replace("#", "");
            return (alpha << 24) | Integer.parseInt(cleanHex, 16);
        } catch (Exception e) {
            // Default to White if parsing fails
            return (alpha << 24) | 0xFFFFFF;
        }
    }

    public void addTab() {
        tabs.add("New Tab");
        chatHistories.put(tabs.size() - 1, new ArrayList<String>());
        save();
    }

    public void deleteTab(int index) {
        if (tabs.size() <= 1 || index == 0) return;
        tabs.remove(index);
        chatHistories.remove(index);
        save();
    }

    public void save() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            // Line 1: Window Bounds
            writer.println("POS:" + windowX + "," + windowY + "," + windowWidth + "," + windowHeight);

            // Line 2: Appearance Styles
            writer.println("STYLE:" + colorSelection + "," + colorTopBar + "," + colorBackground + "," + fontName);

            // Remaining Lines: Tab Names
            for (String tab : tabs) {
                writer.println(tab);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        tabs.clear();
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("POS:")) {
                        String[] parts = line.substring(4).split(",");
                        windowX = Integer.parseInt(parts[0]);
                        windowY = Integer.parseInt(parts[1]);
                        windowWidth = Integer.parseInt(parts[2]);
                        windowHeight = Integer.parseInt(parts[3]);
                    } else if (line.startsWith("STYLE:")) {
                        String[] styles = line.substring(6).split(",");
                        if (styles.length >= 4) {
                            colorSelection = styles[0];
                            colorTopBar = styles[1];
                            colorBackground = styles[2];
                            fontName = styles[3];
                        }
                    } else if (!line.trim().isEmpty()) {
                        tabs.add(line.trim());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Ensure at least one tab exists
        if (tabs.isEmpty()) tabs.add("Global");

        // Initialize histories for loaded tabs
        for (int i = 0; i < tabs.size(); i++) {
            chatHistories.put(i, new ArrayList<String>());
        }
    }
}