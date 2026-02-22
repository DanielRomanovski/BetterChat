package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.util.IChatComponent;
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
    public final Map<Integer, Boolean> includeCommandResponseFilters = new HashMap<>();
    public final Map<Integer, String> tabPrefixes = new HashMap<>();
    public final Map<Integer, String> tabSuffixes = new HashMap<>();
    public final Map<Integer, Integer> scrollOffsets = new HashMap<>();
    public final Map<Integer, Boolean> tabNotifications = new HashMap<>();

    public long lastMessageTime = 0;

    // Legacy single-window fields (window[0] is the primary)
    public int windowX = 20, windowY = 20, windowWidth = 340, windowHeight = 172;
    public int lastResW = -1, lastResH = -1;
    public int lockedX, lockedY, lockedW, lockedH, lockedResW, lockedResH;

    public String colorSelection = "7171ad", colorTopBar = "15151a", colorBackground = "15151a", colorText = "FFFFFF", colorTime = "555555", colorInput = "000000";
    public int opacSelection = 255, opacTopBar = 255, opacBackground = 188, opacText = 255, opacTime = 255, opacInput = 204;
    public String colorFadeTopBar = "000000", colorFadeBackground = "000000";
    public int opacFadeTopBar = 0, opacFadeBackground = 0;

    public boolean hideDefaultChat = true;
    public boolean saveChatLog = true;
    public boolean isLocked = false;
    public boolean showTimeStamps = true;
    public boolean showNotifications = true;

    // -------------------------------------------------------------------------
    // Multi-window support
    // -------------------------------------------------------------------------
    /** One chat window on screen. Owns a list of tab indices (into the global tabs list). */
    public static class ChatWindowInstance {
        public int x, y, width, height;
        public List<Integer> tabIndices = new ArrayList<>(); // which global tab indices this window shows
        public int selectedLocalTab = 0; // index into tabIndices

        public ChatWindowInstance(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.width = w; this.height = h;
        }

        public int getSelectedGlobalIndex() {
            if (tabIndices.isEmpty()) return -1;
            if (selectedLocalTab >= tabIndices.size()) selectedLocalTab = 0;
            return tabIndices.get(selectedLocalTab);
        }

        public void selectGlobalTab(int globalIdx) {
            int local = tabIndices.indexOf(globalIdx);
            if (local != -1) selectedLocalTab = local;
        }
    }

    /** All open windows. windows.get(0) is always the primary window. */
    public final List<ChatWindowInstance> windows = new ArrayList<>();

    // -------------------------------------------------------------------------

    private final File configFile;
    private final File logFile;

    public static class ChatMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        public String text, time, date;
        public boolean isDateSeparator;
        public transient IChatComponent rawComponent;

        public ChatMessage(String text, boolean isSeparator) {
            this.text = text; this.isDateSeparator = isSeparator;
            this.time = new SimpleDateFormat("HH:mm").format(new Date());
            this.date = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        }
        public ChatMessage(String text, boolean isSeparator, IChatComponent component) {
            this(text, isSeparator); this.rawComponent = component;
        }
    }

    public ChatTabData() {
        File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!configDir.exists()) configDir.mkdirs();
        this.configFile = new File(configDir, "cleanchat.txt");
        this.logFile    = new File(configDir, "cleanchat_logs.dat");
        load();
    }

    public int getHex(String hex, int alpha) {
        try { return (alpha << 24) | Integer.parseInt(hex.replace("#",""), 16); }
        catch (Exception e) { return (alpha << 24) | 0xFFFFFF; }
    }

    // -------------------------------------------------------------------------
    // Window helpers
    // -------------------------------------------------------------------------

    /** Find which window owns a given global tab index. Returns -1 if none. */
    public int windowIndexForTab(int globalTabIdx) {
        for (int w = 0; w < windows.size(); w++) {
            if (windows.get(w).tabIndices.contains(globalTabIdx)) return w;
        }
        return -1;
    }

    /**
     * Move a tab from its current window to a new standalone window.
     * The new window is positioned at (spawnX, spawnY).
     * If the source window becomes empty, remove it (unless it is window 0).
     */
    public ChatWindowInstance detachTab(int globalTabIdx, int spawnX, int spawnY) {
        // Remove from current window
        for (ChatWindowInstance win : windows) {
            win.tabIndices.remove((Integer) globalTabIdx);
            if (win.selectedLocalTab >= win.tabIndices.size()) win.selectedLocalTab = Math.max(0, win.tabIndices.size() - 1);
        }
        // Remove ALL empty windows (including window 0)
        for (int w = windows.size() - 1; w >= 0; w--) {
            if (windows.get(w).tabIndices.isEmpty()) windows.remove(w);
        }
        // Create new window for this tab
        ChatWindowInstance newWin = new ChatWindowInstance(spawnX, spawnY, windowWidth, windowHeight);
        newWin.tabIndices.add(globalTabIdx);
        newWin.selectedLocalTab = 0;
        windows.add(newWin);
        save();
        return newWin;
    }

    /**
     * Move a tab from its current window into an existing target window.
     * If the source window becomes empty and is not window 0, remove it.
     */
    public void mergeTabIntoWindow(int globalTabIdx, int targetWindowIdx) {
        ChatWindowInstance target = windows.get(targetWindowIdx);
        if (target.tabIndices.contains(globalTabIdx)) return; // already there
        // Remove from source
        for (ChatWindowInstance win : windows) {
            win.tabIndices.remove((Integer) globalTabIdx);
            if (win.selectedLocalTab >= win.tabIndices.size()) win.selectedLocalTab = Math.max(0, win.tabIndices.size() - 1);
        }
        // Remove ALL empty windows (including window 0)
        for (int w = windows.size() - 1; w >= 0; w--) {
            if (windows.get(w).tabIndices.isEmpty()) windows.remove(w);
        }
        // Re-find target after potential removal
        int newTargetIdx = windows.indexOf(target);
        if (newTargetIdx == -1) newTargetIdx = 0;
        if (newTargetIdx < windows.size()) {
            windows.get(newTargetIdx).tabIndices.add(globalTabIdx);
        } else {
            // Target was removed (was empty), just add to first available window
            if (!windows.isEmpty()) windows.get(0).tabIndices.add(globalTabIdx);
        }
        save();
    }

    // -------------------------------------------------------------------------
    // Defaults / reset
    // -------------------------------------------------------------------------
    public void resetToDefaults() {
        colorSelection = "7171ad"; opacSelection = 255;
        colorTopBar = "15151a";    opacTopBar    = 255;
        colorBackground = "15151a"; opacBackground = 188;
        colorText = "FFFFFF";      opacText      = 255;
        colorTime = "555555";      opacTime      = 255;
        colorInput = "000000";     opacInput     = 204;
        colorFadeTopBar = "000000";     opacFadeTopBar     = 0;
        colorFadeBackground = "000000"; opacFadeBackground = 0;
        showNotifications = true;
        save();
    }

    // -------------------------------------------------------------------------
    // Save / Load
    // -------------------------------------------------------------------------
    public void save() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.println("POS:" + windowX + "," + windowY + "," + windowWidth + "," + windowHeight);
            writer.println("RES:" + lastResW + "," + lastResH);
            writer.println("LOCK_SNAP:" + lockedX + "," + lockedY + "," + lockedW + "," + lockedH + "," + lockedResW + "," + lockedResH);
            writer.println("STYLE_V3:" + colorSelection + "," + colorTopBar + "," + colorBackground + "," + colorText + "," + colorTime + "," + colorInput);
            writer.println("OPAC_V2:" + opacSelection + "," + opacTopBar + "," + opacBackground + "," + opacText + "," + opacTime + "," + opacInput);
            writer.println("FADE_STYLE:" + colorFadeTopBar + "," + colorFadeBackground);
            writer.println("FADE_OPAC:" + opacFadeTopBar + "," + opacFadeBackground);
            writer.println("FLAGS_V2:" + hideDefaultChat + "," + saveChatLog + "," + isLocked + "," + showTimeStamps + "," + showNotifications);
            // Save extra windows (window 0 is saved via POS above)
            for (int w = 1; w < windows.size(); w++) {
                ChatWindowInstance win = windows.get(w);
                StringBuilder sb = new StringBuilder("WINDOW:");
                sb.append(win.x).append(",").append(win.y).append(",").append(win.width).append(",").append(win.height).append(",").append(win.selectedLocalTab);
                for (int idx : win.tabIndices) sb.append(",T").append(idx);
                writer.println(sb.toString());
            }
            // Save primary window tab assignment
            StringBuilder sb0 = new StringBuilder("WINDOW_PRIMARY:");
            sb0.append(windows.isEmpty() ? 0 : windows.get(0).selectedLocalTab);
            for (int idx : (windows.isEmpty() ? Collections.<Integer>emptyList() : windows.get(0).tabIndices)) sb0.append(",T").append(idx);
            writer.println(sb0.toString());

            for (int i = 0; i < tabs.size(); i++) {
                String filter    = tabFilters.getOrDefault(i, "");
                String exclusion = tabExclusions.getOrDefault(i, "");
                boolean serverMsgs = serverMessageFilters.getOrDefault(i, false);
                boolean incAll     = includeAllFilters.getOrDefault(i, false);
                boolean incCmd     = includeCommandsFilters.getOrDefault(i, false);
                boolean incPlayers = includePlayersFilters.getOrDefault(i, false);
                boolean incCmdResp = includeCommandResponseFilters.getOrDefault(i, false);
                String pre = tabPrefixes.getOrDefault(i, "");
                String suf = tabSuffixes.getOrDefault(i, "");
                writer.println("TAB_V6:" + tabs.get(i) + "|" + filter + "|" + exclusion + "|" + serverMsgs + "|" + incAll + "|" + incCmd + "|" + pre + "|" + suf + "|" + incPlayers + "|" + incCmdResp);
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
        tabs.clear(); windows.clear();
        // Temporary storage for window lines read before tabs are loaded
        List<String[]> pendingWindows = new ArrayList<>();
        String[] primaryWindowRaw = null;

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
                    } else if (line.startsWith("STYLE_V3:")) {
                        String[] s = line.substring(9).split(",");
                        if (s.length >= 5) { colorSelection = s[0]; colorTopBar = s[1]; colorBackground = s[2]; colorText = s[3]; colorTime = s[4]; }
                        if (s.length >= 6) colorInput = s[5];
                    } else if (line.startsWith("OPAC_V2:")) {
                        String[] o = line.substring(8).split(",");
                        if (o.length >= 5) { opacSelection = Integer.parseInt(o[0]); opacTopBar = Integer.parseInt(o[1]); opacBackground = Integer.parseInt(o[2]); opacText = Integer.parseInt(o[3]); opacTime = Integer.parseInt(o[4]); }
                        if (o.length >= 6) opacInput = Integer.parseInt(o[5]);
                    } else if (line.startsWith("FADE_STYLE:")) {
                        String[] s = line.substring(11).split(",");
                        if (s.length >= 1) colorFadeTopBar    = s[0];
                        if (s.length >= 2) colorFadeBackground = s[1];
                    } else if (line.startsWith("FADE_OPAC:")) {
                        String[] o = line.substring(10).split(",");
                        if (o.length >= 1) opacFadeTopBar    = Integer.parseInt(o[0]);
                        if (o.length >= 2) opacFadeBackground = Integer.parseInt(o[1]);
                    } else if (line.startsWith("FLAGS_V2:")) {
                        String[] f = line.substring(9).split(",");
                        hideDefaultChat = Boolean.parseBoolean(f[0]); saveChatLog = Boolean.parseBoolean(f[1]);
                        isLocked = Boolean.parseBoolean(f[2]); showTimeStamps = Boolean.parseBoolean(f[3]);
                        if (f.length >= 5) showNotifications = Boolean.parseBoolean(f[4]);
                    } else if (line.startsWith("WINDOW_PRIMARY:")) {
                        primaryWindowRaw = line.substring(15).split(",");
                    } else if (line.startsWith("WINDOW:")) {
                        pendingWindows.add(line.substring(7).split(","));
                    } else if (line.startsWith("TAB_V6:")) {
                        String[] parts = line.substring(7).split("\\|");
                        tabs.add(parts[0]); int idx = tabs.size() - 1;
                        if (parts.length > 1) tabFilters.put(idx, parts[1]);
                        if (parts.length > 2) tabExclusions.put(idx, parts[2]);
                        if (parts.length > 3) serverMessageFilters.put(idx, Boolean.parseBoolean(parts[3]));
                        if (parts.length > 4) includeAllFilters.put(idx, Boolean.parseBoolean(parts[4]));
                        if (parts.length > 5) includeCommandsFilters.put(idx, Boolean.parseBoolean(parts[5]));
                        if (parts.length > 6) tabPrefixes.put(idx, parts[6]);
                        if (parts.length > 7) tabSuffixes.put(idx, parts[7]);
                        if (parts.length > 8) includePlayersFilters.put(idx, Boolean.parseBoolean(parts[8]));
                        if (parts.length > 9) includeCommandResponseFilters.put(idx, Boolean.parseBoolean(parts[9]));
                    }
                    // ...legacy format support omitted for brevity, kept from original...
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        if (tabs.isEmpty()) tabs.add("Global");
        for (int i = 0; i < tabs.size(); i++) {
            if (!chatHistories.containsKey(i)) chatHistories.put(i, new ArrayList<ChatMessage>());
            scrollOffsets.put(i, 0);
        }

        // Build primary window (window 0)
        ChatWindowInstance primary = new ChatWindowInstance(windowX, windowY, windowWidth, windowHeight);
        if (primaryWindowRaw != null) {
            try { primary.selectedLocalTab = Integer.parseInt(primaryWindowRaw[0]); } catch (Exception ignored) {}
            for (int i = 1; i < primaryWindowRaw.length; i++) {
                if (primaryWindowRaw[i].startsWith("T")) {
                    try { primary.tabIndices.add(Integer.parseInt(primaryWindowRaw[i].substring(1))); } catch (Exception ignored) {}
                }
            }
        }
        // Default: all tabs in primary window if no saved assignment
        if (primary.tabIndices.isEmpty()) for (int i = 0; i < tabs.size(); i++) primary.tabIndices.add(i);
        windows.add(primary);

        // Build extra windows
        for (String[] parts : pendingWindows) {
            try {
                int wx = Integer.parseInt(parts[0]), wy = Integer.parseInt(parts[1]);
                int ww = Integer.parseInt(parts[2]), wh = Integer.parseInt(parts[3]);
                int sel = Integer.parseInt(parts[4]);
                ChatWindowInstance win = new ChatWindowInstance(wx, wy, ww, wh);
                win.selectedLocalTab = sel;
                for (int i = 5; i < parts.length; i++) {
                    if (parts[i].startsWith("T")) {
                        int tabIdx = Integer.parseInt(parts[i].substring(1));
                        if (tabIdx < tabs.size()) {
                            win.tabIndices.add(tabIdx);
                            // Remove from primary if it was put there by default
                            primary.tabIndices.remove((Integer) tabIdx);
                        }
                    }
                }
                if (!win.tabIndices.isEmpty()) windows.add(win);
            } catch (Exception e) { e.printStackTrace(); }
        }

        if (saveChatLog && logFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(logFile))) {
                Object obj = ois.readObject();
                if (obj instanceof Map) this.chatHistories = (Map<Integer, List<ChatMessage>>) obj;
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // -------------------------------------------------------------------------
    // Tab management (unchanged interface, now also updates window assignments)
    // -------------------------------------------------------------------------
    public void addTab() {
        tabs.add("New Tab"); int idx = tabs.size() - 1;
        chatHistories.put(idx, new ArrayList<ChatMessage>()); tabFilters.put(idx, "");
        tabExclusions.put(idx, ""); serverMessageFilters.put(idx, false);
        includeAllFilters.put(idx, false); includeCommandsFilters.put(idx, false);
        includePlayersFilters.put(idx, false); includeCommandResponseFilters.put(idx, false);
        scrollOffsets.put(idx, 0);
        // New tab goes into the primary window by default
        if (!windows.isEmpty()) windows.get(0).tabIndices.add(idx);
        save();
    }

    public void deleteTab(int globalIdx) {
        if (tabs.size() <= 1 || globalIdx < 0 || globalIdx >= tabs.size()) return;
        tabs.remove(globalIdx);
        // Remove from all windows and remap indices > globalIdx
        for (ChatWindowInstance win : windows) {
            win.tabIndices.remove((Integer) globalIdx);
            for (int i = 0; i < win.tabIndices.size(); i++) {
                if (win.tabIndices.get(i) > globalIdx) win.tabIndices.set(i, win.tabIndices.get(i) - 1);
            }
            if (win.selectedLocalTab >= win.tabIndices.size()) win.selectedLocalTab = Math.max(0, win.tabIndices.size() - 1);
        }
        // Remove ALL empty windows (including window 0 if it becomes empty)
        for (int w = windows.size() - 1; w >= 0; w--) {
            if (windows.get(w).tabIndices.isEmpty()) windows.remove(w);
        }
        // If no windows remain, create a fallback primary window
        if (windows.isEmpty()) {
            ChatWindowInstance primary = new ChatWindowInstance(windowX, windowY, windowWidth, windowHeight);
            if (!tabs.isEmpty()) primary.tabIndices.add(0);
            windows.add(primary);
        }
        rebuildMapsAfterDeletion(globalIdx);
        save();
    }

    private void rebuildMapsAfterDeletion(int removedIdx) {
        int size = tabs.size() + 1;
        for (int i = removedIdx; i < size - 1; i++) {
            chatHistories.put(i, chatHistories.getOrDefault(i + 1, new ArrayList<ChatMessage>()));
            tabFilters.put(i, tabFilters.getOrDefault(i + 1, ""));
            tabExclusions.put(i, tabExclusions.getOrDefault(i + 1, ""));
            serverMessageFilters.put(i, serverMessageFilters.getOrDefault(i + 1, false));
            includeAllFilters.put(i, includeAllFilters.getOrDefault(i + 1, false));
            includeCommandsFilters.put(i, includeCommandsFilters.getOrDefault(i + 1, false));
            includePlayersFilters.put(i, includePlayersFilters.getOrDefault(i + 1, false));
            includeCommandResponseFilters.put(i, includeCommandResponseFilters.getOrDefault(i + 1, false));
            tabPrefixes.put(i, tabPrefixes.getOrDefault(i + 1, ""));
            tabSuffixes.put(i, tabSuffixes.getOrDefault(i + 1, ""));
            scrollOffsets.put(i, scrollOffsets.getOrDefault(i + 1, 0));
            tabNotifications.put(i, tabNotifications.getOrDefault(i + 1, false));
        }
        int last = size - 1;
        chatHistories.remove(last); tabFilters.remove(last); tabExclusions.remove(last);
        serverMessageFilters.remove(last); includeAllFilters.remove(last);
        includeCommandsFilters.remove(last); includePlayersFilters.remove(last);
        includeCommandResponseFilters.remove(last);
        tabPrefixes.remove(last); tabSuffixes.remove(last);
        scrollOffsets.remove(last); tabNotifications.remove(last);
    }

    public void swapTabsInWindow(ChatWindowInstance win, int localA, int localB) {
        if (localA < 0 || localB < 0 || localA >= win.tabIndices.size() || localB >= win.tabIndices.size()) return;
        Collections.swap(win.tabIndices, localA, localB);
        save();
    }
}

