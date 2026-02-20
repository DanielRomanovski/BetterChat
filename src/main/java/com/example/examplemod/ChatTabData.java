package com.example.examplemod;

import net.minecraft.client.Minecraft;
import java.io.*;
import java.util.*;

public class ChatTabData {
    public final List<String> tabs = new ArrayList<>();
    public final Map<Integer, List<String>> chatHistories = new HashMap<>();
    public final Map<Integer, String> tabFilters = new HashMap<>();
    public final Map<Integer, Boolean> serverMessageFilters = new HashMap<>();
    public final Map<Integer, Boolean> includeAllFilters = new HashMap<>();
    public final Map<Integer, Boolean> includeCommandsFilters = new HashMap<>();
    public final Map<Integer, Boolean> includePlayersFilters = new HashMap<>();
    public final Map<Integer, String> tabPrefixes = new HashMap<>();
    public final Map<Integer, String> tabSuffixes = new HashMap<>();
    public final Map<Integer, Integer> scrollOffsets = new HashMap<>();
    public final Map<Integer, Boolean> tabNotifications = new HashMap<>();

    public long lastMessageTime = 0;
    public int windowX = 20, windowY = 20, windowWidth = 340, windowHeight = 172;
    public int lastResW = -1, lastResH = -1;
    public String colorSelection = "00FFFF", colorTopBar = "000000", colorBackground = "1A1E24";
    public boolean hideDefaultChat = true;
    public boolean saveChatLog = true;
    public boolean isLocked = false;

    private final File configFile;
    private final File logFile;

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
        } catch (Exception e) { return (alpha << 24) | 0xFFFFFF; }
    }

    public void save() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.println("POS:" + windowX + "," + windowY + "," + windowWidth + "," + windowHeight);
            writer.println("RES:" + lastResW + "," + lastResH);
            writer.println("STYLE:" + colorSelection + "," + colorTopBar + "," + colorBackground + "," + hideDefaultChat + "," + saveChatLog + "," + isLocked);
            for (int i = 0; i < tabs.size(); i++) {
                String filter = tabFilters.getOrDefault(i, "");
                boolean serverMsgs = serverMessageFilters.getOrDefault(i, false);
                boolean incAll = includeAllFilters.getOrDefault(i, false);
                boolean incCmd = includeCommandsFilters.getOrDefault(i, false);
                boolean incPlayers = includePlayersFilters.getOrDefault(i, false);
                String pre = tabPrefixes.getOrDefault(i, "");
                String suf = tabSuffixes.getOrDefault(i, "");
                writer.println("TAB_V4:" + tabs.get(i) + "|" + filter + "|" + serverMsgs + "|" + incAll + "|" + incCmd + "|" + pre + "|" + suf + "|" + incPlayers);
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
        tabs.clear();
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("POS:")) {
                        String[] p = line.substring(4).split(",");
                        windowX = Integer.parseInt(p[0]); windowY = Integer.parseInt(p[1]);
                        windowWidth = Integer.parseInt(p[2]); windowHeight = Integer.parseInt(p[3]);
                    } else if (line.startsWith("RES:")) {
                        String[] r = line.substring(4).split(",");
                        lastResW = Integer.parseInt(r[0]); lastResH = Integer.parseInt(r[1]);
                    } else if (line.startsWith("STYLE:")) {
                        String[] s = line.substring(6).split(",");
                        if (s.length >= 3) { colorSelection = s[0]; colorTopBar = s[1]; colorBackground = s[2]; }
                        if (s.length >= 4) hideDefaultChat = Boolean.parseBoolean(s[3]);
                        if (s.length >= 5) saveChatLog = Boolean.parseBoolean(s[4]);
                        if (s.length >= 6) isLocked = Boolean.parseBoolean(s[5]);
                    } else if (line.startsWith("TAB_V4:")) {
                        String[] parts = line.substring(7).split("\\|");
                        tabs.add(parts[0]);
                        int idx = tabs.size() - 1;
                        if (parts.length > 1) tabFilters.put(idx, parts[1]);
                        if (parts.length > 2) serverMessageFilters.put(idx, Boolean.parseBoolean(parts[2]));
                        if (parts.length > 3) includeAllFilters.put(idx, Boolean.parseBoolean(parts[3]));
                        if (parts.length > 4) includeCommandsFilters.put(idx, Boolean.parseBoolean(parts[4]));
                        if (parts.length > 5) tabPrefixes.put(idx, parts[5]);
                        if (parts.length > 6) tabSuffixes.put(idx, parts[6]);
                        if (parts.length > 7) includePlayersFilters.put(idx, Boolean.parseBoolean(parts[7]));
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        if (tabs.isEmpty()) tabs.add("Global");
        for (int i = 0; i < tabs.size(); i++) {
            if (!chatHistories.containsKey(i)) chatHistories.put(i, new ArrayList<String>());
            scrollOffsets.put(i, 0);
        }
    }

    public void addTab() {
        tabs.add("New Tab"); int idx = tabs.size() - 1;
        chatHistories.put(idx, new ArrayList<String>()); tabFilters.put(idx, "");
        serverMessageFilters.put(idx, false); includeAllFilters.put(idx, false);
        includeCommandsFilters.put(idx, false); includePlayersFilters.put(idx, false);
        scrollOffsets.put(idx, 0);
        save();
    }

    public void deleteTab(int idx) {
        if (tabs.size() > 1 && idx != 0) {
            tabs.remove(idx); chatHistories.remove(idx); tabFilters.remove(idx);
            serverMessageFilters.remove(idx); includeAllFilters.remove(idx);
            includeCommandsFilters.remove(idx); includePlayersFilters.remove(idx);
            tabPrefixes.remove(idx); tabSuffixes.remove(idx);
            scrollOffsets.remove(idx); tabNotifications.remove(idx);
            save();
        }
    }
}