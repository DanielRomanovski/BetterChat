package com.betterchat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.IChatComponent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Locale;

/**
 * Holds all persistent state for the mod: tabs, windows, colours, flags, and the
 * full chat history. Also owns load/save logic and the filter-matching rules.
 *
 * Nothing in here renders or handles input — it is pure data and business logic.
 */
public class ChatTabData {

    /** Display names of every tab, in order. Index into this to get per-tab settings. */
    public final List<String> tabs = new ArrayList<>();

    // Every message ever received is kept here forever so that re-filtering a tab
    // can always reconstruct the correct history without losing anything.
    public List<ChatMessage> globalLog = new ArrayList<>();

    // Per-tab filter settings, keyed by tab index.
    public final Map<Integer, String>  tabFilters                   = new HashMap<>();
    public final Map<Integer, String>  tabExclusions                = new HashMap<>();
    public final Map<Integer, Boolean> serverMessageFilters         = new HashMap<>();
    public final Map<Integer, Boolean> includeAllFilters            = new HashMap<>();
    public final Map<Integer, Boolean> includeCommandsFilters       = new HashMap<>();
    public final Map<Integer, Boolean> includePlayersFilters        = new HashMap<>();
    public final Map<Integer, Boolean> includeCommandResponseFilters = new HashMap<>();
    public final Map<Integer, String>  tabPrefixes                  = new HashMap<>();
    public final Map<Integer, String>  tabSuffixes                  = new HashMap<>();
    public final Map<Integer, Integer> scrollOffsets                = new HashMap<>();
    public final Map<Integer, Boolean> tabNotifications             = new HashMap<>();

    /** Bumped every time any filter changes, so ChatRenderer knows to rebuild its line cache. */
    public int filterVersion = 0;

    /** Player's username — kept in sync on each received message for filter matching. */
    public String playerName = "";

    /** Timestamp of the last received message, used to drive the HUD fade timer. */
    public long lastMessageTime = 0;

    // Position/size of window 0. Kept in sync with windows.get(0) and written to the config.
    public int windowX = 20, windowY = 20, windowWidth = 340, windowHeight = 172;
    public int lastResW = -1, lastResH = -1;
    // Saved position/size used when isLocked = true, along with the resolution it was saved at.
    public int lockedX, lockedY, lockedW, lockedH, lockedResW, lockedResH;

    // Colour hex strings and alpha values for each UI element.
    public String colorSelection = "7171ad", colorTopBar = "15151a", colorBackground = "15151a",
                  colorText = "FFFFFF", colorTime = "555555", colorInput = "000000";
    public int opacSelection = 255, opacTopBar = 255, opacBackground = 188,
               opacText = 255, opacTime = 255, opacInput = 204;
    // Separate fade colours shown on the HUD overlay (can be set to 0 alpha to hide them).
    public String colorFadeTopBar = "000000", colorFadeBackground = "000000";
    public int opacFadeTopBar = 0, opacFadeBackground = 0;

    // Feature flags
    public boolean hideDefaultChat  = true;
    public boolean saveChatLog      = true;
    public boolean isLocked         = false;
    public boolean showTimeStamps   = true;

    // Chat display features
    /** Master toggle — when true, all custom-font sub-settings are active. */
    public boolean fontSizeEnabled       = false;
    /** Font scale for chat message text. 1.0 = default MC size. */
    public float   fontSize              = 1.0f;
    /** Name of the system font to use for chat message text, or "" to use Minecraft's built-in font. */
    public String  fontName              = "";
    /** Whether a custom font is enabled for message text. */
    public boolean fontEnabled           = false;
    /** Font scale for tab labels. */
    public float   fontSizeTabs          = 1.0f;
    /** Name of the system font to use for tab labels, or "" to use Minecraft's built-in font. */
    public String  fontNameTabs          = "";
    /** Whether a custom font is enabled for tab labels. */
    public boolean fontTabsEnabled       = false;
    /** Font scale for timestamps and date dividers. */
    public float   fontSizeTimestamps    = 1.0f;
    /** Name of the system font to use for timestamps and date dividers, or "" to use Minecraft's built-in font. */
    public String  fontNameTimestamps    = "";
    /** Whether a custom font is enabled for timestamps and date dividers. */
    public boolean fontTimestampsEnabled = false;
    /** When true, consecutive identical messages are collapsed to "msg <xN>". */
    public boolean messageCombining      = true;
    /** When true, removes the surrounding angle brackets from player names:  <Name> → Name: */
    public boolean stripPlayerBrackets   = false;

    // Mute/ignore lists (player name → expiry ms, or Long.MAX_VALUE for permanent)
    public final Map<String, Long>    mutedPlayers  = new HashMap<>();
    public final java.util.Set<String> ignoredPlayers = new java.util.HashSet<>();

    // ── Notification types ────────────────────────────────────────────────────
    /** Master toggle — show any notification when a message arrives. */
    public boolean showNotifications    = true;
    /** Play an in-game sound when a message arrives in a tab with notifications on. */
    public boolean soundNotifications   = true;
    /** Send a Windows (system tray) notification when the game is not focused. */
    public boolean windowsNotifications = false;

    // ── Keybind entries ───────────────────────────────────────────────────────
    /** A keyboard shortcut (one or more keys held simultaneously) that sends a preset chat message. */
    public static class KeybindEntry implements java.io.Serializable {
        /** LWJGL key codes that must ALL be held simultaneously to trigger. */
        public List<Integer> keyCodes = new ArrayList<>();
        /** The message/command to send. */
        public String message = "";
        /** Human-readable label shown in the settings UI. */
        public String label   = "";

        public KeybindEntry() {}
        public KeybindEntry(List<Integer> keyCodes, String message, String label) {
            this.keyCodes = new ArrayList<>(keyCodes);
            this.message  = message;
            this.label    = label;
        }

        /** Returns a display string like "LSHIFT+A" for the bound key combo. */
        public String displayKey() {
            if (keyCodes.isEmpty()) return "None";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keyCodes.size(); i++) {
                if (i > 0) sb.append('+');
                String name = org.lwjgl.input.Keyboard.getKeyName(keyCodes.get(i));
                sb.append(name != null ? name : "?");
            }
            return sb.toString();
        }
    }

    /** An auto-response rule: if the chat contains {@code trigger}, send {@code response}. */
    public static class AutoResponseEntry implements java.io.Serializable {
        public String trigger  = "";
        public String response = "";

        public AutoResponseEntry() {}
        public AutoResponseEntry(String trigger, String response) {
            this.trigger  = trigger;
            this.response = response;
        }
    }

    public final List<KeybindEntry>      keybinds      = new ArrayList<>();
    public final List<AutoResponseEntry> autoResponses = new ArrayList<>();

    // -------------------------------------------------------------------------
    // ChatWindowInstance  —  one draggable/resizable chat window on screen
    // -------------------------------------------------------------------------

    /** Represents one chat window. A window holds an ordered list of tab indices
     *  and remembers which one is currently selected. */
    public static class ChatWindowInstance {
        public int x, y, width, height;
        /** Global tab indices shown in this window, left to right. */
        public List<Integer> tabIndices = new ArrayList<>();
        /** Local (within this window) index of the active tab. */
        public int selectedLocalTab = 0;

        public ChatWindowInstance(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.width = w; this.height = h;
        }

        /** Returns the global tab index of the currently selected tab, or -1 if empty. */
        public int getSelectedGlobalIndex() {
            if (tabIndices.isEmpty()) return -1;
            if (selectedLocalTab >= tabIndices.size()) selectedLocalTab = 0;
            return tabIndices.get(selectedLocalTab);
        }

        /** Selects the tab with the given global index, if it exists in this window. */
        public void selectGlobalTab(int globalIdx) {
            int local = tabIndices.indexOf(globalIdx);
            if (local != -1) selectedLocalTab = local;
        }
    }

    public final List<ChatWindowInstance> windows = new ArrayList<>();

    private final File configFile;
    private final File logFile;

    // -------------------------------------------------------------------------
    // ChatMessage  —  one entry in globalLog
    // -------------------------------------------------------------------------

    /** One chat message as received from the server, plus classification flags used for filtering. */
    public static class ChatMessage implements Serializable {
        private static final long serialVersionUID = 2L;
        public String text;       // formatted text (with colour codes)
        public String time;       // "HH:mm" timestamp
        public String date;       // "yyyy/MM/dd" date, used for date-separator grouping
        public boolean isDateSeparator;                 // true for injected date-divider rows
        public transient IChatComponent rawComponent;   // the original component for hover/click events
        // Classification used by the filter engine
        public boolean isLocal;           // sent by this player
        public boolean isOtherPlayer;     // sent by another player
        public boolean isCommand;         // starts with "/"
        public boolean isCommandResponse; // arrived shortly after a player command
        public String  plainText;         // unformatted, for keyword matching
        // Message combining — groupId links consecutive identical messages together.
        // repeatCount on the LAST message of the group holds how many are in the group.
        // Never mutated after being set; render-time only.
        public int    repeatCount = 1;  // count of identical messages in this group (set on last msg)
        public int    groupId     = 0;  // non-zero means this message belongs to a combine group

        /** Constructor for date separator rows. */
        public ChatMessage(String text, boolean isSeparator) {
            this.text = text; this.isDateSeparator = isSeparator;
            this.time = new SimpleDateFormat("HH:mm").format(new Date());
            this.date = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        }

        /** Full constructor for real chat messages. */
        public ChatMessage(String text, boolean isSeparator, IChatComponent component,
                           boolean isLocal, boolean isOtherPlayer, boolean isCommand,
                           boolean isCommandResponse, String plainText) {
            this(text, isSeparator);
            this.rawComponent      = component;
            this.isLocal           = isLocal;
            this.isOtherPlayer     = isOtherPlayer;
            this.isCommand         = isCommand;
            this.isCommandResponse = isCommandResponse;
            this.plainText         = plainText;
        }
    }

    public ChatTabData() {
        File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!configDir.exists()) configDir.mkdirs();
        this.configFile = new File(configDir, "betterchat.txt");
        this.logFile    = new File(configDir, "betterchat_logs.dat");
        load();
    }

    /** Converts a hex colour string + alpha byte into a packed ARGB int. */
    public int getHex(String hex, int alpha) {
        try { return (alpha << 24) | Integer.parseInt(hex.replace("#", ""), 16); }
        catch (Exception e) { return (alpha << 24) | 0xFFFFFF; }
    }

    // -------------------------------------------------------------------------
    // Filter matching
    // -------------------------------------------------------------------------

    /**
     * Returns true if a message should appear in the given tab.
     * Checks exclusion keywords first, then inclusion rules.
     * Date separator rows are never matched (they are injected by the renderer).
     */
    /**
     * If stripPlayerBrackets is on, converts  "&lt;Name&gt; text" → "Name: text"
     * for display. The original msg.text is never mutated.
     */
    public String applyBracketStrip(String text) {
        if (!stripPlayerBrackets) return text;
        // Match  <Name>  at the start (with optional colour codes before the bracket)
        // Pattern: optional §x codes, then '<', then name chars, then '>', then space
        String stripped = text;
        // Strip leading colour codes to find the '<'
        int start = 0;
        while (start + 1 < stripped.length() && stripped.charAt(start) == '\u00A7') start += 2;
        if (start < stripped.length() && stripped.charAt(start) == '<') {
            int end = stripped.indexOf('>', start);
            if (end != -1 && end + 1 < stripped.length()) {
                String name = stripped.substring(start + 1, end);
                String rest = stripped.substring(end + 1);
                // Preserve any colour codes that were before the '<'
                String prefix = stripped.substring(0, start);
                stripped = prefix + name + ":" + rest;
            }
        }
        return stripped;
    }

    /** Returns the player name from a plain-text chat message using multiple strategies.
     *  1. Strips colour codes, then looks for &lt;Name&gt; (vanilla format).
     *  2. Falls back to the word immediately before the first ": " separator
     *     which covers "[RANK] Name: msg" (Hypixel, most servers).
     *  Returns null if nothing plausible is found. */
    public static String extractPlayerName(String plainText) {
        if (plainText == null || plainText.isEmpty()) return null;

        // Strip Minecraft colour codes (§x pairs) to get clean plain text
        String clean = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(plainText);
        if (clean == null || clean.isEmpty()) return null;
        clean = clean.trim();

        // Strategy 1: classic <Name> format (vanilla servers)
        int lt = clean.indexOf('<');
        int gt = lt >= 0 ? clean.indexOf('>', lt + 1) : -1;
        if (lt >= 0 && gt > lt + 1) {
            String candidate = clean.substring(lt + 1, gt).trim();
            if (isPlausibleName(candidate)) return candidate;
        }

        // Strategy 2: "... Name: message" — find the last word before the first ": "
        // This covers "[MVP+] PlayerName: hello" and "PlayerName: hello"
        int colonIdx = clean.indexOf(": ");
        if (colonIdx > 0) {
            // Take the substring before ": " and grab the last space-separated token
            String before = clean.substring(0, colonIdx).trim();
            int lastSpace = before.lastIndexOf(' ');
            String candidate = (lastSpace >= 0) ? before.substring(lastSpace + 1) : before;
            // Strip any trailing punctuation like ] or )
            candidate = candidate.replaceAll("[\\]\\)>]+$", "").trim();
            if (isPlausibleName(candidate)) return candidate;
        }

        return null;
    }

    /** Returns true if the string looks like a plausible Minecraft player name. */
    private static boolean isPlausibleName(String s) {
        if (s == null || s.isEmpty() || s.length() > 40) return false;
        // Must not contain spaces; should be alphanumeric + _ (MC name rules)
        return s.matches("[A-Za-z0-9_]{1,40}");
    }

    /** Tries to extract a player name from a ClickEvent value (e.g. "/msg Name", "/tell Name").
     *  Returns null if the value doesn't match a known player-targeting command. */
    public static String extractNameFromClickEvent(net.minecraft.event.ClickEvent ce) {
        if (ce == null) return null;
        String val = ce.getValue();
        if (val == null) return null;
        String[] prefixes = {"/msg ", "/tell ", "/w ", "/whisper ", "/pm ", "/dm ",
                             "/r ", "/reply ", "/friend add ", "/ignore "};
        for (String p : prefixes) {
            if (val.toLowerCase().startsWith(p)) {
                String rest = val.substring(p.length()).trim();
                int sp = rest.indexOf(' ');
                String name = sp > 0 ? rest.substring(0, sp) : rest;
                if (isPlausibleName(name)) return name;
            }
        }
        return null;
    }

    /** Returns true if this player is currently muted (temp or permanent). */
    public boolean isPlayerMuted(String name) {
        Long exp = mutedPlayers.get(name);
        if (exp == null) return false;
        if (exp == Long.MAX_VALUE) return true;
        if (System.currentTimeMillis() < exp) return true;
        mutedPlayers.remove(name); return false;
    }

    public boolean messagePassesFilter(int tabIdx, ChatMessage msg) {
        if (msg.isDateSeparator) return false;
        String plain = msg.plainText != null ? msg.plainText : msg.text;

        // Mute / ignore checks
        String sender = extractPlayerName(plain);
        if (sender != null) {
            if (ignoredPlayers.contains(sender)) return false;
            if (isPlayerMuted(sender)) return false;
        }

        // Exclusion check
        String ex = tabExclusions.getOrDefault(tabIdx, "");
        if (!ex.isEmpty()) {
            for (String k : ex.split(",")) {
                if (!k.trim().isEmpty() && plain.toLowerCase().contains(k.trim().toLowerCase())) return false;
            }
        }

        // Inclusion checks
        if (msg.isLocal) return true;
        if (includeAllFilters.getOrDefault(tabIdx, false)) return true;
        if (!playerName.isEmpty() && plain.contains(playerName)) return true;

        String f = tabFilters.getOrDefault(tabIdx, "");
        if (!f.isEmpty()) {
            for (String k : f.split(",")) {
                if (!k.trim().isEmpty() && plain.toLowerCase().contains(k.trim().toLowerCase())) return true;
            }
        }
        if (includeCommandsFilters.getOrDefault(tabIdx, false) && msg.isCommand) return true;
        if (serverMessageFilters.getOrDefault(tabIdx, false) && !msg.isOtherPlayer) return true;
        if (includePlayersFilters.getOrDefault(tabIdx, false) && msg.isOtherPlayer) return true;
        if (includeCommandResponseFilters.getOrDefault(tabIdx, false) && msg.isCommandResponse) return true;

        return false;
    }

    /**
     * Scans globalLog and builds the filtered, date-separated message list for one tab.
     * Called by ChatRenderer when the line cache needs rebuilding.
     */
    public List<ChatMessage> buildFilteredHistory(int tabIdx) {
        List<ChatMessage> result = new ArrayList<>();
        String lastDate = null;
        for (ChatMessage msg : globalLog) {
            if (!messagePassesFilter(tabIdx, msg)) continue;
            // Insert a date separator when the date changes
            if (!msg.date.equals(lastDate)) {
                result.add(new ChatMessage(msg.date, true));
                lastDate = msg.date;
            }
            result.add(msg);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Window helpers
    // -------------------------------------------------------------------------

    /** Returns the index of the window that contains the given global tab index, or -1. */
    public int windowIndexForTab(int globalTabIdx) {
        for (int w = 0; w < windows.size(); w++) {
            if (windows.get(w).tabIndices.contains(globalTabIdx)) return w;
        }
        return -1;
    }

    /**
     * Removes a tab from whichever window owns it and opens it in a new window
     * positioned at (spawnX, spawnY). Empty windows are removed automatically.
     */
    public ChatWindowInstance detachTab(int globalTabIdx, int spawnX, int spawnY) {
        for (ChatWindowInstance win : windows) {
            win.tabIndices.remove((Integer) globalTabIdx);
            if (win.selectedLocalTab >= win.tabIndices.size()) win.selectedLocalTab = Math.max(0, win.tabIndices.size() - 1);
        }
        for (int w = windows.size() - 1; w >= 0; w--) {
            if (windows.get(w).tabIndices.isEmpty()) windows.remove(w);
        }
        ChatWindowInstance newWin = new ChatWindowInstance(spawnX, spawnY, windowWidth, windowHeight);
        newWin.tabIndices.add(globalTabIdx);
        newWin.selectedLocalTab = 0;
        windows.add(newWin);
        save();
        return newWin;
    }

    /**
     * Moves a tab into an existing window. Removes it from its current window first.
     * Empty windows left behind are removed automatically.
     */
    public void mergeTabIntoWindow(int globalTabIdx, int targetWindowIdx) {
        ChatWindowInstance target = windows.get(targetWindowIdx);
        if (target.tabIndices.contains(globalTabIdx)) return;
        for (ChatWindowInstance win : windows) {
            win.tabIndices.remove((Integer) globalTabIdx);
            if (win.selectedLocalTab >= win.tabIndices.size()) win.selectedLocalTab = Math.max(0, win.tabIndices.size() - 1);
        }
        for (int w = windows.size() - 1; w >= 0; w--) {
            if (windows.get(w).tabIndices.isEmpty()) windows.remove(w);
        }
        int newTargetIdx = windows.indexOf(target);
        if (newTargetIdx == -1) newTargetIdx = 0;
        if (newTargetIdx < windows.size()) {
            windows.get(newTargetIdx).tabIndices.add(globalTabIdx);
        } else {
            if (!windows.isEmpty()) windows.get(0).tabIndices.add(globalTabIdx);
        }
        save();
    }

    // -------------------------------------------------------------------------
    // Defaults / reset
    // -------------------------------------------------------------------------

    /** Resets all colour and opacity values to their defaults and saves. */
    public void resetToDefaults() {
        colorSelection = "7171ad"; opacSelection = 255;
        colorTopBar = "15151a";    opacTopBar    = 255;
        colorBackground = "15151a"; opacBackground = 188;
        colorText = "FFFFFF";      opacText      = 255;
        colorTime = "555555";      opacTime      = 255;
        colorInput = "000000";     opacInput     = 204;
        colorFadeTopBar = "000000";     opacFadeTopBar     = 0;
        colorFadeBackground = "000000"; opacFadeBackground = 0;
        showNotifications = true; soundNotifications = true; windowsNotifications = false;
        save();
    }

    // -------------------------------------------------------------------------
    // Save / Load
    // -------------------------------------------------------------------------

    /** Writes all settings and window layout to betterchat.txt, then saves the message log. */
    public void save() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.println("POS:" + windowX + "," + windowY + "," + windowWidth + "," + windowHeight);
            writer.println("RES:" + lastResW + "," + lastResH);
            writer.println("LOCK_SNAP:" + lockedX + "," + lockedY + "," + lockedW + "," + lockedH + "," + lockedResW + "," + lockedResH);
            writer.println("STYLE_V3:" + colorSelection + "," + colorTopBar + "," + colorBackground + "," + colorText + "," + colorTime + "," + colorInput);
            writer.println("OPAC_V2:" + opacSelection + "," + opacTopBar + "," + opacBackground + "," + opacText + "," + opacTime + "," + opacInput);
            writer.println("FADE_STYLE:" + colorFadeTopBar + "," + colorFadeBackground);
            writer.println("FADE_OPAC:" + opacFadeTopBar + "," + opacFadeBackground);
            writer.println("FLAGS_V2:" + hideDefaultChat + "," + saveChatLog + "," + isLocked + "," + showTimeStamps + "," + showNotifications + "," + soundNotifications + "," + windowsNotifications);
            // Keybinds: KEYBIND:k1;k2;k3|message|label
            for (KeybindEntry kb : keybinds) {
                StringBuilder kcs = new StringBuilder();
                for (int i = 0; i < kb.keyCodes.size(); i++) {
                    if (i > 0) kcs.append(';');
                    kcs.append(kb.keyCodes.get(i));
                }
                writer.println("KEYBIND:" + kcs + "|"
                        + kb.message.replace("|","§p") + "|" + kb.label.replace("|","§p"));
            }
            // Auto-responses
            for (AutoResponseEntry ar : autoResponses) {
                writer.println("AUTORESPONSE:" + ar.trigger.replace("|","§p") + "|" + ar.response.replace("|","§p"));
            }
            writer.println("DISPLAY2:" + fontSizeEnabled
                + "," + String.format(Locale.US, "%.2f", fontSize)
                + "," + fontEnabled + "," + fontName.replace(",","|")
                + "," + messageCombining
                + "," + fontTabsEnabled + "," + fontNameTabs.replace(",","|") + "," + String.format(Locale.US, "%.2f", fontSizeTabs)
                + "," + fontTimestampsEnabled + "," + fontNameTimestamps.replace(",","|") + "," + String.format(Locale.US, "%.2f", fontSizeTimestamps)
                + "," + stripPlayerBrackets);
            // Save muted players: name=expiryMs (Long.MAX_VALUE = permanent)
            for (Map.Entry<String, Long> e : mutedPlayers.entrySet()) {
                writer.println("MUTE:" + e.getKey().replace(",", "|") + "," + e.getValue());
            }
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
            saveHistory();
        } catch (IOException e) { e.printStackTrace(); }
    }

    /** Serialises globalLog to betterchat_logs.dat. */
    private void saveHistory() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(logFile))) {
            oos.writeObject(globalLog);
        } catch (IOException e) { e.printStackTrace(); }
    }

    /** Reads betterchat.txt and betterchat_logs.dat, populating all fields and windows. */
    @SuppressWarnings("unchecked")
    public void load() {
        tabs.clear(); windows.clear();
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
                        if (f.length >= 5) showNotifications    = Boolean.parseBoolean(f[4]);
                        if (f.length >= 6) soundNotifications   = Boolean.parseBoolean(f[5]);
                        if (f.length >= 7) windowsNotifications = Boolean.parseBoolean(f[6]);
                    } else if (line.startsWith("KEYBIND:")) {
                        String[] p = line.substring(8).split("\\|", 3);
                        if (p.length == 3) {
                            try {
                                List<Integer> kcs = new ArrayList<>();
                                for (String k : p[0].split(";")) {
                                    if (!k.isEmpty()) kcs.add(Integer.parseInt(k));
                                }
                                keybinds.add(new KeybindEntry(
                                    kcs, p[1].replace("§p","|"), p[2].replace("§p","|")));
                            } catch (Exception ignored) {}
                        }
                    } else if (line.startsWith("AUTORESPONSE:")) {
                        String[] p = line.substring(13).split("\\|", 2);
                        if (p.length == 2) {
                            autoResponses.add(new AutoResponseEntry(
                                p[0].replace("§p","|"), p[1].replace("§p","|")));
                        }
                    } else if (line.startsWith("DISPLAY2:")) {
                        String[] d = line.substring(9).split(",");
                        if (d.length >= 1) fontSizeEnabled       = Boolean.parseBoolean(d[0]);
                        if (d.length >= 2) { try { fontSize = Float.parseFloat(d[1]); } catch (Exception ignored) {} }
                        if (d.length >= 3) fontEnabled           = Boolean.parseBoolean(d[2]);
                        if (d.length >= 4) fontName              = d[3].replace("|", ",");
                        if (d.length >= 5) messageCombining      = Boolean.parseBoolean(d[4]);
                        if (d.length >= 6) fontTabsEnabled       = Boolean.parseBoolean(d[5]);
                        if (d.length >= 7) fontNameTabs          = d[6].replace("|", ",");
                        if (d.length >= 8) { try { fontSizeTabs = Float.parseFloat(d[7]); } catch (Exception ignored) {} }
                        if (d.length >= 9) fontTimestampsEnabled = Boolean.parseBoolean(d[8]);
                        if (d.length >= 10) fontNameTimestamps   = d[9].replace("|", ",");
                        if (d.length >= 11) { try { fontSizeTimestamps = Float.parseFloat(d[10]); } catch (Exception ignored) {} }
                        if (d.length >= 12) stripPlayerBrackets = Boolean.parseBoolean(d[11]);
                    } else if (line.startsWith("DISPLAY:")) {
                        // Legacy format — read what we can
                        String[] d = line.substring(8).split(",");
                        if (d.length >= 1) fontSizeEnabled  = Boolean.parseBoolean(d[0]);
                        if (d.length >= 2) { try { fontSize = Float.parseFloat(d[1]); } catch (Exception ignored) {} }
                        if (d.length >= 3) fontEnabled      = Boolean.parseBoolean(d[2]);
                        if (d.length >= 4) fontName         = d[3].replace("|", ",");
                        if (d.length >= 5) messageCombining = Boolean.parseBoolean(d[4]);
                    } else if (line.startsWith("MUTE:")) {
                        String[] m = line.substring(5).split(",");
                        if (m.length == 2) {
                            try {
                                String name = m[0].replace("|", ",");
                                long exp = Long.parseLong(m[1]);
                                mutedPlayers.put(name, exp);
                            } catch (Exception ignored) {}
                        }
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
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        if (tabs.isEmpty()) tabs.add("Global");
        for (int i = 0; i < tabs.size(); i++) scrollOffsets.put(i, 0);

        ChatWindowInstance primary = new ChatWindowInstance(windowX, windowY, windowWidth, windowHeight);
        if (primaryWindowRaw != null) {
            try { primary.selectedLocalTab = Integer.parseInt(primaryWindowRaw[0]); } catch (Exception ignored) {}
            for (int i = 1; i < primaryWindowRaw.length; i++) {
                if (primaryWindowRaw[i].startsWith("T")) {
                    try { primary.tabIndices.add(Integer.parseInt(primaryWindowRaw[i].substring(1))); } catch (Exception ignored) {}
                }
            }
        }
        if (primary.tabIndices.isEmpty()) for (int i = 0; i < tabs.size(); i++) primary.tabIndices.add(i);
        windows.add(primary);

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
                            primary.tabIndices.remove((Integer) tabIdx);
                        }
                    }
                }
                if (!win.tabIndices.isEmpty()) windows.add(win);
            } catch (Exception e) { e.printStackTrace(); }
        }

        // Load global log
        if (logFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(logFile))) {
                Object obj = ois.readObject();
                if (obj instanceof List) {
                    globalLog = (List<ChatMessage>) obj;
                } else if (obj instanceof Map) {
                    // Migrate old per-tab format: flatten all messages into globalLog in order
                    Map<Integer, List<ChatMessage>> oldHistories = (Map<Integer, List<ChatMessage>>) obj;
                    // Use tab 0 (Global) as the source of truth for migration
                    List<ChatMessage> tab0 = oldHistories.get(0);
                    if (tab0 != null) {
                        for (ChatMessage m : tab0) {
                            if (!m.isDateSeparator) {
                                if (m.plainText == null) m.plainText = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(m.text);
                                globalLog.add(m);
                            }
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // -------------------------------------------------------------------------
    // Tab management
    // -------------------------------------------------------------------------

    /** Adds a new blank tab to window 0 and saves. */
    public void addTab() {
        tabs.add("New Tab"); int idx = tabs.size() - 1;
        tabFilters.put(idx, "");
        tabExclusions.put(idx, ""); serverMessageFilters.put(idx, false);
        includeAllFilters.put(idx, false); includeCommandsFilters.put(idx, false);
        includePlayersFilters.put(idx, false); includeCommandResponseFilters.put(idx, false);
        scrollOffsets.put(idx, 0);
        if (!windows.isEmpty()) windows.get(0).tabIndices.add(idx);
        save();
    }

    /**
     * Deletes a tab by global index. Removes it from all windows, shifts indices
     * for tabs that came after it, and saves.
     */
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
        for (int w = windows.size() - 1; w >= 0; w--) {
            if (windows.get(w).tabIndices.isEmpty()) windows.remove(w);
        }
        if (windows.isEmpty()) {
            ChatWindowInstance primary = new ChatWindowInstance(windowX, windowY, windowWidth, windowHeight);
            if (!tabs.isEmpty()) primary.tabIndices.add(0);
            windows.add(primary);
        }
        rebuildSettingMapsAfterDeletion(globalIdx);
        save();
    }

    /** After a tab is deleted, shifts all per-tab setting maps down by one from removedIdx. */
    private void rebuildSettingMapsAfterDeletion(int removedIdx) {
        int size = tabs.size() + 1; // tabs already shrank, so +1 is the old size
        for (int i = removedIdx; i < size - 1; i++) {
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
        tabFilters.remove(last); tabExclusions.remove(last);
        serverMessageFilters.remove(last); includeAllFilters.remove(last);
        includeCommandsFilters.remove(last); includePlayersFilters.remove(last);
        includeCommandResponseFilters.remove(last);
        tabPrefixes.remove(last); tabSuffixes.remove(last);
        scrollOffsets.remove(last); tabNotifications.remove(last);
    }

    /** Swaps two tabs within the same window by their local indices. */
    public void swapTabsInWindow(ChatWindowInstance win, int localA, int localB) {
        if (localA < 0 || localB < 0 || localA >= win.tabIndices.size() || localB >= win.tabIndices.size()) return;
        Collections.swap(win.tabIndices, localA, localB);
        save();
    }
}
