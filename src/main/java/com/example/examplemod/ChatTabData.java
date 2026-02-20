package com.example.examplemod;

import net.minecraft.client.Minecraft;
import java.io.*;
import java.util.*;

public class ChatTabData {
    public final List<String> tabs = new ArrayList<>();
    public final Map<Integer, List<String>> chatHistories = new HashMap<>();
    public final Map<Integer, String> tabFilters = new HashMap<>();
    public final Map<Integer, Boolean> serverMessageFilters = new HashMap<>();
    public final Map<Integer, Integer> scrollOffsets = new HashMap<>();

    private final File configFile;
    private final File logFile;

    public int windowX = 20, windowY = 20, windowWidth = 340, windowHeight = 172;
    public String colorSelection = "00FFFF", colorTopBar = "000000", colorBackground = "1A1E24";
    public boolean hideDefaultChat = true;
    public boolean saveChatLog = true;

    public ChatTabData() {
        File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!configDir.exists()) configDir.mkdirs();
        this.configFile = new File(configDir, "cleanchat.txt");
        this.logFile = new File(configDir, "cleanchat_logs.dat");
        load();
    }

    public int getHex(String hex, int alpha) {
        try {
            return (alpha << 24) | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Exception e) { return 0xFFFFFF; }
    }

    public void save() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.println("POS:" + windowX + "," + windowY + "," + windowWidth + "," + windowHeight);
            writer.println("STYLE:" + colorSelection + "," + colorTopBar + "," + colorBackground + "," + hideDefaultChat + "," + saveChatLog);
            for (int i = 0; i < tabs.size(); i++) {
                String filter = tabFilters.getOrDefault(i, "");
                boolean serverMsgs = serverMessageFilters.getOrDefault(i, false);
                writer.println("TAB:" + tabs.get(i) + "|" + filter + "|" + serverMsgs);
            }
            if (saveChatLog) saveHistory();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void saveHistory() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(logFile))) {
            oos.writeObject(chatHistories);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @SuppressWarnings("unchecked")
    public void load() {
        tabs.clear(); tabFilters.clear(); serverMessageFilters.clear();
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
                        if (s.length >= 3) { colorSelection = s[0]; colorTopBar = s[1]; colorBackground = s[2]; }
                        if (s.length >= 4) hideDefaultChat = Boolean.parseBoolean(s[3]);
                        if (s.length >= 5) saveChatLog = Boolean.parseBoolean(s[4]);
                    } else if (line.startsWith("TAB:")) {
                        String[] parts = line.substring(4).split("\\|");
                        tabs.add(parts[0]);
                        int idx = tabs.size() - 1;
                        if (parts.length > 1) tabFilters.put(idx, parts[1]);
                        if (parts.length > 2) serverMessageFilters.put(idx, Boolean.parseBoolean(parts[2]));
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        if (tabs.isEmpty()) tabs.add("Global");
        for (int i = 0; i < tabs.size(); i++) {
            if (!chatHistories.containsKey(i)) chatHistories.put(i, new ArrayList<String>());
            scrollOffsets.put(i, 0);
        }
        if (saveChatLog && logFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(logFile))) {
                Map<Integer, List<String>> loaded = (Map<Integer, List<String>>) ois.readObject();
                chatHistories.putAll(loaded);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public void addTab() {
        tabs.add("New Tab"); int idx = tabs.size() - 1;
        chatHistories.put(idx, new ArrayList<String>()); tabFilters.put(idx, "");
        serverMessageFilters.put(idx, false); scrollOffsets.put(idx, 0);
        save();
    }

    public void deleteTab(int idx) {
        if (tabs.size() > 1 && idx != 0) {
            tabs.remove(idx); chatHistories.remove(idx); tabFilters.remove(idx);
            serverMessageFilters.remove(idx); scrollOffsets.remove(idx);
            save();
        }
    }
}