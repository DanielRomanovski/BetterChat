package com.example.examplemod;

import net.minecraft.client.Minecraft;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatTabData {
    public final List<String> tabs = new ArrayList<>();
    public Map<Integer, List<ChatMessage>> chatHistories = new HashMap<>();
    public final Map<Integer, String> tabFilters = new HashMap<>();
    public final Map<Integer, String> tabExclusions = new HashMap<>();
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

    public int lockedX, lockedY, lockedW, lockedH, lockedResW, lockedResH;

    public String colorSelection = "7171ad", colorTopBar = "15151a", colorBackground = "15151a", colorText = "FFFFFF", colorTime = "555555";
    public int opacSelection = 255, opacTopBar = 85, opacBackground = 238, opacText = 255, opacTime = 255;

    public boolean hideDefaultChat = true;
    public boolean saveChatLog = true;
    public boolean isLocked = false;
    public boolean showTimeStamps = true;
    public boolean showNotifications = true;

    private final File configFile;
    private final File logFile;

    public static class ChatMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        public String text;
        public String time;
        public String date;
        public boolean isDateSeparator;

        public ChatMessage(String text, boolean isSeparator) {
            this.text = text;
            this.isDateSeparator = isSeparator;
            this.time = new SimpleDateFormat("HH:mm").format(new Date());
            this.date = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        }
    }

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

    public void resetToDefaults() {
        colorSelection = "7171ad"; opacSelection = 255;
        colorTopBar = "15151a"; opacTopBar = 85;
        colorBackground = "15151a"; opacBackground = 238;
        colorText = "FFFFFF"; opacText = 255;
        colorTime = "555555"; opacTime = 255;
        showNotifications = true;
        save();
    }

    public void save() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.println("POS:" + windowX + "," + windowY + "," + windowWidth + "," + windowHeight);
            writer.println("RES:" + lastResW + "," + lastResH);
            writer.println("LOCK_SNAP:" + lockedX + "," + lockedY + "," + lockedW + "," + lockedH + "," + lockedResW + "," + lockedResH);
            writer.println("STYLE_V2:" + colorSelection + "," + colorTopBar + "," + colorBackground + "," + colorText + "," + colorTime);
            writer.println("OPAC:" + opacSelection + "," + opacTopBar + "," + opacBackground + "," + opacText + "," + opacTime);
            writer.println("FLAGS_V2:" + hideDefaultChat + "," + saveChatLog + "," + isLocked + "," + showTimeStamps + "," + showNotifications);
            for (int i = 0; i < tabs.size(); i++) {
                String filter = tabFilters.getOrDefault(i, "");
                String exclusion = tabExclusions.getOrDefault(i, "");
                boolean serverMsgs = serverMessageFilters.getOrDefault(i, false);
                boolean incAll = includeAllFilters.getOrDefault(i, false);
                boolean incCmd = includeCommandsFilters.getOrDefault(i, false);
                boolean incPlayers = includePlayersFilters.getOrDefault(i, false);
                String pre = tabPrefixes.getOrDefault(i, "");
                String suf = tabSuffixes.getOrDefault(i, "");
                writer.println("TAB_V5:" + tabs.get(i) + "|" + filter + "|" + exclusion + "|" + serverMsgs + "|" + incAll + "|" + incCmd + "|" + pre + "|" + suf + "|" + incPlayers);
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
                    } else if (line.startsWith("LOCK_SNAP:")) {
                        String[] l = line.substring(10).split(",");
                        lockedX = Integer.parseInt(l[0]); lockedY = Integer.parseInt(l[1]);
                        lockedW = Integer.parseInt(l[2]); lockedH = Integer.parseInt(l[3]);
                        lockedResW = Integer.parseInt(l[4]); lockedResH = Integer.parseInt(l[5]);
                    } else if (line.startsWith("RES:")) {
                        String[] r = line.substring(4).split(",");
                        lastResW = Integer.parseInt(r[0]); lastResH = Integer.parseInt(r[1]);
                    } else if (line.startsWith("STYLE_V2:")) {
                        String[] s = line.substring(9).split(",");
                        if (s.length >= 5) { colorSelection = s[0]; colorTopBar = s[1]; colorBackground = s[2]; colorText = s[3]; colorTime = s[4]; }
                    } else if (line.startsWith("OPAC:")) {
                        String[] o = line.substring(5).split(",");
                        if (o.length >= 5) { opacSelection = Integer.parseInt(o[0]); opacTopBar = Integer.parseInt(o[1]); opacBackground = Integer.parseInt(o[2]); opacText = Integer.parseInt(o[3]); opacTime = Integer.parseInt(o[4]); }
                    } else if (line.startsWith("FLAGS_V2:")) {
                        String[] f = line.substring(9).split(",");
                        hideDefaultChat = Boolean.parseBoolean(f[0]); saveChatLog = Boolean.parseBoolean(f[1]); isLocked = Boolean.parseBoolean(f[2]); showTimeStamps = Boolean.parseBoolean(f[3]);
                        if (f.length >= 5) showNotifications = Boolean.parseBoolean(f[4]);
                    } else if (line.startsWith("FLAGS:")) {
                        String[] f = line.substring(6).split(",");
                        hideDefaultChat = Boolean.parseBoolean(f[0]); saveChatLog = Boolean.parseBoolean(f[1]); isLocked = Boolean.parseBoolean(f[2]); showTimeStamps = Boolean.parseBoolean(f[3]);
                    } else if (line.startsWith("TAB_V5:")) {
                        String[] parts = line.substring(7).split("\\|");
                        tabs.add(parts[0]);
                        int idx = tabs.size() - 1;
                        if (parts.length > 1) tabFilters.put(idx, parts[1]);
                        if (parts.length > 2) tabExclusions.put(idx, parts[2]);
                        if (parts.length > 3) serverMessageFilters.put(idx, Boolean.parseBoolean(parts[3]));
                        if (parts.length > 4) includeAllFilters.put(idx, Boolean.parseBoolean(parts[4]));
                        if (parts.length > 5) includeCommandsFilters.put(idx, Boolean.parseBoolean(parts[5]));
                        if (parts.length > 6) tabPrefixes.put(idx, parts[6]);
                        if (parts.length > 7) tabSuffixes.put(idx, parts[7]);
                        if (parts.length > 8) includePlayersFilters.put(idx, Boolean.parseBoolean(parts[8]));
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        if (saveChatLog && logFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(logFile))) {
                Object obj = ois.readObject();
                if (obj instanceof Map) this.chatHistories = (Map<Integer, List<ChatMessage>>) obj;
            } catch (Exception e) { e.printStackTrace(); }
        }

        if (tabs.isEmpty()) tabs.add("Global");
        for (int i = 0; i < tabs.size(); i++) {
            if (!chatHistories.containsKey(i)) chatHistories.put(i, new ArrayList<ChatMessage>());
            scrollOffsets.put(i, 0);
        }
    }

    public void addTab() {
        tabs.add("New Tab"); int idx = tabs.size() - 1;
        chatHistories.put(idx, new ArrayList<ChatMessage>()); tabFilters.put(idx, "");
        tabExclusions.put(idx, "");
        serverMessageFilters.put(idx, false); includeAllFilters.put(idx, false);
        includeCommandsFilters.put(idx, false); includePlayersFilters.put(idx, false);
        scrollOffsets.put(idx, 0);
        save();
    }

    public void deleteTab(int idx) {
        if (tabs.size() > 1 && idx >= 0 && idx < tabs.size()) {
            tabs.remove(idx);
            rebuildMapsAfterDeletion(idx);
            save();
        }
    }

    private void rebuildMapsAfterDeletion(int removedIdx) {
        int size = tabs.size() + 1;
        for (int i = removedIdx; i < size - 1; i++) {
            chatHistories.put(i, chatHistories.get(i + 1));
            tabFilters.put(i, tabFilters.get(i + 1));
            tabExclusions.put(i, tabExclusions.get(i + 1));
            serverMessageFilters.put(i, serverMessageFilters.get(i + 1));
            includeAllFilters.put(i, includeAllFilters.get(i + 1));
            includeCommandsFilters.put(i, includeCommandsFilters.get(i + 1));
            includePlayersFilters.put(i, includePlayersFilters.get(i + 1));
            tabPrefixes.put(i, tabPrefixes.get(i + 1));
            tabSuffixes.put(i, tabSuffixes.get(i + 1));
            scrollOffsets.put(i, scrollOffsets.get(i + 1));
            tabNotifications.put(i, tabNotifications.get(i + 1));
        }
        int last = size - 1;
        chatHistories.remove(last); tabFilters.remove(last); tabExclusions.remove(last);
        serverMessageFilters.remove(last); includeAllFilters.remove(last);
        includeCommandsFilters.remove(last); includePlayersFilters.remove(last);
        tabPrefixes.remove(last); tabSuffixes.remove(last);
        scrollOffsets.remove(last); tabNotifications.remove(last);
    }

    public void swapTabs(int i, int j) {
        Collections.swap(tabs, i, j);
        swapMapEntry(chatHistories, i, j);
        swapMapEntry(tabFilters, i, j);
        swapMapEntry(tabExclusions, i, j);
        swapMapEntry(serverMessageFilters, i, j);
        swapMapEntry(includeAllFilters, i, j);
        swapMapEntry(includeCommandsFilters, i, j);
        swapMapEntry(includePlayersFilters, i, j);
        swapMapEntry(tabPrefixes, i, j);
        swapMapEntry(tabSuffixes, i, j);
        swapMapEntry(scrollOffsets, i, j);
        swapMapEntry(tabNotifications, i, j);
        save();
    }

    private <T> void swapMapEntry(Map<Integer, T> map, int i, int j) {
        T tempI = map.get(i);
        T tempJ = map.get(j);
        if (tempI != null) map.put(j, tempI); else map.remove(j);
        if (tempJ != null) map.put(i, tempJ); else map.remove(i);
    }
}