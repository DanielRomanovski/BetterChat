package com.example.examplemod;

import net.minecraft.client.Minecraft;
import java.io.*;
import java.util.*;

public class ChatTabData {
    public final List<String> tabs = new ArrayList<>();
    public final Map<Integer, List<String>> chatHistories = new HashMap<>();
    private final File configFile;

    public int windowX = 20, windowY = 20, windowWidth = 340, windowHeight = 172;

    // Styling Defaults (Hex Strings)
    public String colorSelection = "00FFFF";
    public String colorTopBar = "000000";
    public String colorBackground = "1A1E24";

    public ChatTabData() {
        this.configFile = new File(Minecraft.getMinecraft().mcDataDir, "config/cleanchat.txt");
        load();
    }

    public int getHex(String hex, int alpha) {
        try {
            return (alpha << 24) | Integer.parseInt(hex, 16);
        } catch (Exception e) { return 0xFFFFFF; }
    }

    public void save() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.println("POS:" + windowX + "," + windowY + "," + windowWidth + "," + windowHeight);
            writer.println("STYLE:" + colorSelection + "," + colorTopBar + "," + colorBackground);
            for (String tab : tabs) writer.println(tab);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void load() {
        tabs.clear();
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("POS:")) {
                        String[] p = line.substring(4).split(",");
                        windowX = Integer.parseInt(p[0]); windowY = Integer.parseInt(p[1]);
                        windowWidth = Integer.parseInt(p[2]); windowHeight = Integer.parseInt(p[3]);
                    } else if (line.startsWith("STYLE:")) {
                        String[] s = line.substring(6).split(",");
                        if (s.length >= 3) {
                            colorSelection = s[0]; colorTopBar = s[1]; colorBackground = s[2];
                        }
                    } else if (!line.trim().isEmpty()) {
                        tabs.add(line.trim());
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        if (tabs.isEmpty()) tabs.add("Global");
        for (int i = 0; i < tabs.size(); i++) chatHistories.put(i, new ArrayList<String>());
    }

    public void addTab() { tabs.add("New Tab"); chatHistories.put(tabs.size()-1, new ArrayList<String>()); save(); }
    public void deleteTab(int idx) { if (tabs.size() > 1 && idx != 0) { tabs.remove(idx); chatHistories.remove(idx); save(); } }
}