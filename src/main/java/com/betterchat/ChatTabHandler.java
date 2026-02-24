package com.betterchat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.*;

/**
 * The main Forge event handler for BetterChat.
 * Wires together all the other classes and owns the UI interaction state.
 *
 * What it does:
 *  - Receives incoming chat messages and adds them to ChatTabData.
 *  - Tracks window drag, resize, tab drag, and scroll bar drag state.
 *  - Draws every chat window each frame, delegating message rendering to ChatRenderer.
 *  - Routes mouse clicks to the right window (tabs, scroll, chat links, settings).
 *  - Routes keyboard input to tab renaming or message sending via ChatInputHandler.
 *  - Renders a fading HUD overlay of recent messages when the chat GUI is closed.
 */
public class ChatTabHandler {

    // ── Core data & helpers ───────────────────────────────────────────────────
    private final ChatTabData      data      = new ChatTabData();
    private final ChatSettingsGui  settings  = new ChatSettingsGui(data);
    private final ChatRenderer     renderer  = new ChatRenderer(data);
    private final ChatInputHandler input     = new ChatInputHandler(data);

    // ── UI state ──────────────────────────────────────────────────────────────
    private int          editingTabGlobalIndex    = -1;  // tab currently being renamed, or -1
    private int          pendingDeleteGlobalIndex = -1;  // tab awaiting a second right-click to delete
    private boolean      isSettingsOpen           = false;
    private boolean      wasChatOpen              = false;
    private GuiTextField renameField, customChatField;
    /** Last text we pushed into the vanilla field — used to detect tab-autocomplete mutations. */
    private String       lastSentToVanilla = "";
    private long         lastClickTime            = 0;
    private int          lastClickedGlobalIndex   = -1;

    // ── Window drag / resize ──────────────────────────────────────────────────
    private int draggingWindowIndex = -1;     // index of the window being moved, or -1
    private int dragWinOffsetX, dragWinOffsetY;
    private int resizingWindowIndex = -1;     // index of the window being resized, or -1

    // ── Tab drag ──────────────────────────────────────────────────────────────
    private boolean                        isDraggingTab           = false;
    private int                            draggingTabGlobalIndex  = -1;
    private ChatTabData.ChatWindowInstance draggingTabSourceWindow = null;
    private int                            dragTabVisualX = 0, dragTabVisualY = 0;
    private int                            dragTabMouseOffsetX     = 0;
    private boolean                        tabIsDetached           = false; // true once the tab leaves its bar
    private int                            dropTargetWindowIndex   = -1;   // window the ghost is hovering over
    private int                            tabReorderInsertPos     = -1;   // insertion slot for in-window reorder

    // ── Player context menu (shift+click) ─────────────────────────────────────
    private boolean showPlayerMenu      = false;
    private String  playerMenuName      = "";
    private int     playerMenuX         = 0, playerMenuY = 0;
    private static final int PM_W = 130, PM_ROW = 16;
    private static final String[] PM_LABELS = {"Temp Mute (10 min)", "Perma Mute"};
    // Latch so we only trigger the menu once per right-click press
    private boolean rightClickWasDown   = false;
    // Counts frames since menu opened — ignore dismiss clicks for the first few frames.
    private int     menuOpenFrames      = 0;
    // Per-keybind latch for edge-detection (prevents repeated firing while held)
    private final java.util.IdentityHashMap<ChatTabData.KeybindEntry, Boolean> keybindLatch = new java.util.IdentityHashMap<>();
    private boolean isDraggingScrollBar      = false;
    private int     scrollBarDragWindowIndex = -1;
    private int     scrollBarDragStartY      = 0;
    private int     scrollBarDragStartOffset = 0;

    // ── HUD fade ──────────────────────────────────────────────────────────────
    /** Timestamp when the HUD fade was last triggered by a visible message. -1 = never shown. */
    private long hudFadeStartTime = -1L;

    // -------------------------------------------------------------------------
    // Screen helpers
    // -------------------------------------------------------------------------

    /** Keeps a window fully on-screen and enforces minimum size. Also syncs window 0's
     *  position back into the legacy ChatTabData fields used for saving. */
    private void clampWindowToScreen(ChatTabData.ChatWindowInstance win, ScaledResolution sr) {
        int screenW = sr.getScaledWidth(), screenH = sr.getScaledHeight();
        int requiredWidth = 5;
        for (int idx : win.tabIndices)
            requiredWidth += Minecraft.getMinecraft().fontRendererObj.getStringWidth(data.tabs.get(idx)) + 18 + 4;
        requiredWidth += 45; // [+] + gear
        if (win.width  < requiredWidth) win.width  = requiredWidth;
        if (win.height < 50)            win.height = 50;
        if (win.x < 0) win.x = 0;
        if (win.y < 0) win.y = 0;
        if (win.x + win.width  > screenW) win.x = screenW - win.width;
        if (win.y + win.height + 16 > screenH) win.y = screenH - win.height - 16;
        if (!data.windows.isEmpty() && data.windows.get(0) == win) {
            data.windowX = win.x; data.windowY = win.y;
            data.windowWidth = win.width; data.windowHeight = win.height;
        }
    }

    // -------------------------------------------------------------------------
    // HUD fade overlay (shown when chat GUI is closed)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onRenderHUD(RenderGameOverlayEvent.Text event) {
        if (!data.hideDefaultChat) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiChat) return;

        // ── Poll keybinds ────────────────────────────────────────────────────
        for (ChatTabData.KeybindEntry kb : data.keybinds) {
            if (!kb.message.isEmpty() && !kb.keyCodes.isEmpty()) {
                // All keys in the combo must be held simultaneously
                boolean pressed = true;
                for (int kc : kb.keyCodes) {
                    if (!org.lwjgl.input.Keyboard.isKeyDown(kc)) { pressed = false; break; }
                }
                if (pressed) {
                    if (!keybindLatch.getOrDefault(kb, false)) {
                        keybindLatch.put(kb, true);
                        if (mc.thePlayer != null) mc.thePlayer.sendChatMessage(kb.message);
                    }
                } else {
                    keybindLatch.put(kb, false);
                }
            }
        }

        if (hudFadeStartTime < 0) return;
        long elapsed = System.currentTimeMillis() - hudFadeStartTime;
        if (elapsed >= 7000) {
            hudFadeStartTime = -1L; // fully expired — reset so we don't re-enter next frame
            return;
        }
        float fade = (elapsed > 6000)
                ? Math.max(0f, 1.0f - (float)(elapsed - 6000) / 1000f)
                : 1.0f;
        int fadeAlpha = (int)(fade * 255);
        if (fadeAlpha <= 0) {
            hudFadeStartTime = -1L;
            return;
        }

        for (ChatTabData.ChatWindowInstance win : data.windows) {
            int bgColor  = applyFadeToColor(data.getHex(data.colorFadeBackground, data.opacFadeBackground), fadeAlpha);
            int barColor = applyFadeToColor(data.getHex(data.colorFadeTopBar,     data.opacFadeTopBar),     fadeAlpha);
            Gui.drawRect(win.x, win.y + 22, win.x + win.width, win.y + win.height, bgColor);
            Gui.drawRect(win.x, win.y,      win.x + win.width, win.y + 22,         barColor);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, fade);
            renderer.renderWindowContent(mc, win, fadeAlpha, true);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    private int applyFadeToColor(int argb, int fadeAlpha) {
        int originalAlpha = (argb >>> 24) & 0xFF;
        int newAlpha = Math.max(0, Math.min(255, (originalAlpha * fadeAlpha) / 255));
        return (argb & 0x00FFFFFF) | (newAlpha << 24);
    }

    @SubscribeEvent
    public void onRenderChat(RenderGameOverlayEvent.Pre event) {
        if (data.hideDefaultChat && event.type == RenderGameOverlayEvent.ElementType.CHAT)
            event.setCanceled(true);
    }

    // -------------------------------------------------------------------------
    // Chat received
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String plain   = event.message.getUnformattedText();
        String formatted = event.message.getFormattedText();
        data.playerName = Minecraft.getMinecraft().thePlayer.getName();

        boolean isLocal           = plain.startsWith("<" + data.playerName + ">") || plain.startsWith(data.playerName + ":");
        // isOtherPlayer: ONLY the strict <Name> text format where Name is a valid MC username.
        // We match "<" + 1-16 word chars + ">" at the start, then exclude our own name.
        // This prevents server messages with angle brackets (e.g. "<Usage: /give <player>>",
        // "<red>some text") from being misclassified as player chat.
        boolean isOtherPlayer     = !isLocal
                && java.util.regex.Pattern.compile("^<\\w{1,16}>\\s").matcher(plain).find()
                && !plain.startsWith("<" + data.playerName + ">");
        // Command responses: any non-local, non-player-chat message that arrives while the
        // player is in command-response mode (set by sending "/" commands, cleared by normal chat).
        boolean isCommandResponse = !isLocal && !isOtherPlayer && input.isWithinCommandResponseWindow();
        // isCommand is for player-sent commands injected directly into the log (never echoed by server).
        boolean isCommand         = plain.startsWith("/");

        ChatTabData.ChatMessage msg = new ChatTabData.ChatMessage(
                formatted, false, event.message,
                isLocal, isOtherPlayer, isCommand, isCommandResponse, plain);

        // Message combining: always add the message to the log.
        // If this message's plain text matches the last real message, assign them the same
        // groupId so the renderer can collapse them into "<xN>" when combining is enabled.
        // Toggling the setting only affects display — the full log is always preserved.
        if (!data.globalLog.isEmpty()) {
            for (int gi = data.globalLog.size() - 1; gi >= 0; gi--) {
                ChatTabData.ChatMessage prev = data.globalLog.get(gi);
                if (prev.isDateSeparator) continue;
                if (prev.plainText != null && prev.plainText.equals(plain)) {
                    // Determine the group id (reuse prev's group, or start a new one)
                    int gid = (prev.groupId != 0) ? prev.groupId : (data.globalLog.size()); // unique id
                    prev.groupId = gid;
                    msg.groupId = gid;
                    // repeatCount on the incoming message = how many in the group so far + 1
                    msg.repeatCount = prev.repeatCount + 1;
                    prev.repeatCount = msg.repeatCount; // keep in sync for cache-miss recovery
                }
                break; // only compare with the most recent real message
            }
        }

        data.globalLog.add(msg);

        boolean withinDebounce = (System.currentTimeMillis() - input.getLastPlayerSendTime())
                <= ChatInputHandler.SEND_ECHO_DEBOUNCE_MS;
        // The tab the player was actively typing in when they sent (used for the echo exception)
        int sentFromTab = input.getLastSentFromTabIndex();

        boolean passedAnyTab        = false;
        boolean passedBackgroundTab = false; // true if message went into a tab not currently on screen
        boolean passedSelectedTab   = false; // true if message went into a tab currently selected in any window
        for (int i = 0; i < data.tabs.size(); i++) {
            // Normal filter check; PLUS: if this is the player's own echo arriving within the
            // debounce window, always show it in the tab they sent from (regardless of filter).
            boolean passes = data.messagePassesFilter(i, msg)
                    || (isLocal && withinDebounce && i == sentFromTab);
            if (passes) {
                passedAnyTab = true;
                renderer.lineCache.remove(i);
                renderer.lineCacheHistorySize.put(i, -1);
                for (ChatTabData.ChatWindowInstance win : data.windows) {
                    if (win.tabIndices.contains(i)) {
                        if (win.getSelectedGlobalIndex() == i) {
                            passedSelectedTab = true;
                        } else if (!isLocal) {
                            data.tabNotifications.put(i, true);
                            passedBackgroundTab = true;
                        }
                    }
                }
            }
        }

        long now = System.currentTimeMillis();
        // For non-echo messages (or after debounce expires), trigger HUD/notifications.
        if (!withinDebounce || !isLocal) {
            // Only trigger the HUD fade if the message appeared in a currently selected tab
            if (passedSelectedTab) {
                hudFadeStartTime = now;
                data.lastMessageTime = now;
            }

            // ── Notifications — only fire if message passed at least one tab's filter ──
            final Minecraft mc = Minecraft.getMinecraft();
            if (data.showNotifications && passedAnyTab) {
                if (data.soundNotifications && mc.theWorld != null && mc.thePlayer != null) {
                    net.minecraft.client.audio.PositionedSoundRecord sound =
                        net.minecraft.client.audio.PositionedSoundRecord.create(
                            new net.minecraft.util.ResourceLocation("betterchat", "notify"), 1.0f);
                    mc.getSoundHandler().playSound(sound);
                }
                // Windows notifications only fire for background tabs (no point alerting for visible messages)
                if (data.windowsNotifications && passedBackgroundTab && !org.lwjgl.opengl.Display.isActive()) {
                    sendWindowsNotification("BetterChat", plain);
                }
            }

            // ── Auto-responses ───────────────────────────────────────────────
            if (!isLocal) {
                String lowerPlain = plain.toLowerCase();
                for (ChatTabData.AutoResponseEntry ar : data.autoResponses) {
                    if (!ar.trigger.isEmpty() && lowerPlain.contains(ar.trigger.toLowerCase())) {
                        final String resp = ar.response;
                        mc.addScheduledTask(new Runnable() {
                            public void run() {
                                if (Minecraft.getMinecraft().thePlayer != null)
                                    Minecraft.getMinecraft().thePlayer.sendChatMessage(resp);
                            }
                        });
                        break;
                    }
                }
            }
        }
        data.save();
    }

    // ── Windows / system-tray notifications ──────────────────────────────────

    private static java.awt.TrayIcon trayIcon   = null;
    private static boolean           trayFailed = false;

    /** Shows a system-tray balloon notification. Safe to call from any thread. */
    private static void sendWindowsNotification(String title, String message) {
        if (trayFailed) return;
        try {
            if (!java.awt.SystemTray.isSupported()) { trayFailed = true; return; }

            // Lazy-init the tray icon once
            if (trayIcon == null) {
                java.awt.SystemTray tray = java.awt.SystemTray.getSystemTray();
                // 16×16 solid green icon as a stand-in (no external image needed)
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                        16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = img.createGraphics();
                g.setColor(new java.awt.Color(0x4E9EFF));
                g.fillOval(0, 0, 16, 16);
                g.dispose();
                trayIcon = new java.awt.TrayIcon(img, "BetterChat");
                trayIcon.setImageAutoSize(true);
                tray.add(trayIcon);
            }

            // Truncate message to avoid overly long balloons
            String safeMsg = message.length() > 200 ? message.substring(0, 197) + "..." : message;
            trayIcon.displayMessage(title, safeMsg, java.awt.TrayIcon.MessageType.INFO);
        } catch (Exception e) {
            trayFailed = true;
        }
    }

    // -------------------------------------------------------------------------
    // Cancel vanilla chat rendering
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!(event.gui instanceof GuiChat)) return;
        if (data.hideDefaultChat) event.setCanceled(true);
    }

    // -------------------------------------------------------------------------
    // GUI open / close  —  reset scroll to bottom when chat closes
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (wasChatOpen && !(event.gui instanceof GuiChat)) {
            for (int i = 0; i < data.tabs.size(); i++) data.scrollOffsets.put(i, 0);
            renderer.targetCacheScrollOffset.replaceAll((k, v) -> Integer.MIN_VALUE);
            wasChatOpen = false;
        }
        if (event.gui instanceof GuiChat) {
            wasChatOpen = true;
            input.resetHistoryCursor(); // fresh navigation every time chat opens
        }
    }

    // -------------------------------------------------------------------------
    // Main draw loop  —  called every frame while GuiChat is open
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiChat)) { customChatField = null; lastSentToVanilla = ""; return; }
        wasChatOpen = true;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int mx = Mouse.getX() * event.gui.width / mc.displayWidth;
        int my = event.gui.height - Mouse.getY() * event.gui.height / mc.displayHeight - 1;

        if (isSettingsOpen) { settings.draw(mx, my); return; }

        // Mouse button released — commit drag/resize and finalize any tab drop
        if (!Mouse.isButtonDown(0)) {
            if (draggingWindowIndex != -1 || resizingWindowIndex != -1) data.save();
            draggingWindowIndex = -1;
            resizingWindowIndex = -1;
            isDraggingScrollBar = false;
            if (isDraggingTab) finalizeDrop(mx, my, sr);
        }

        // Mouse button held — update whatever is being dragged
        if (Mouse.isButtonDown(0)) {
            if (draggingWindowIndex != -1 && draggingWindowIndex < data.windows.size()) {
                ChatTabData.ChatWindowInstance win = data.windows.get(draggingWindowIndex);
                win.x = mx - dragWinOffsetX; win.y = my - dragWinOffsetY;
                clampWindowToScreen(win, sr);

            } else if (resizingWindowIndex != -1 && resizingWindowIndex < data.windows.size()) {
                ChatTabData.ChatWindowInstance win = data.windows.get(resizingWindowIndex);
                win.width  = Math.max(100, mx - win.x);
                win.height = Math.max(50,  my - win.y);
                clampWindowToScreen(win, sr);

            } else if (isDraggingScrollBar && scrollBarDragWindowIndex < data.windows.size()) {
                handleScrollBarDrag(mx, my);

            } else if (isDraggingTab) {
                handleTabDrag(mx, my);
            }
        }

        // Draw all windows
        renderer.activeHoverTargets.clear();
        renderer.activeClickTargets.clear();
        for (int w = 0; w < data.windows.size(); w++) {
            ChatTabData.ChatWindowInstance win = data.windows.get(w);
            clampWindowToScreen(win, sr);
            drawWindow(mc, w, win, mx, my);
        }

        // ── Shift+right-click: detect via direct mouse poll (btn==1 doesn't
        //    reliably fire through MouseInputEvent in GuiChat on MC 1.8.9). ──
        boolean rightDown = Mouse.isButtonDown(1);
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                         || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (rightDown && !rightClickWasDown && shiftDown && !isSettingsOpen && !showPlayerMenu) {
            for (int w = data.windows.size() - 1; w >= 0; w--) {
                ChatTabData.ChatWindowInstance win = data.windows.get(w);
                float scaleTabS = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTabs)) : 1.0f;
                int tabBarHS = Math.max(16, (int)(22 * scaleTabS));
                if (mx < win.x || mx > win.x + win.width) continue;
                if (my <= win.y + tabBarHS || my >= win.y + win.height) continue;

                String foundName = null;

                // ── Method 1: check clickTargetCache rects (same as left-click) ──
                // Works on servers like Hypixel that send /msg or /tell ClickEvents
                // on player names, even when the name isn't in <Name> format.
                for (ChatTargets.ClickTarget t : renderer.clickTargetCache.getOrDefault(w, Collections.emptyList())) {
                    if (mx >= t.x1 && mx <= t.x2 && my >= t.y1 && my <= t.y2) {
                        foundName = ChatTabData.extractNameFromClickEvent(t.clickEvent);
                        break;
                    }
                }

                // ── Method 2: line-scan + plainText fallback (vanilla / any server) ──
                // Uses the full original plainText of the source message (not the wrapped
                // line snippet) so "Strategy 2: word before ': '" works correctly.
                if (foundName == null) {
                    int globalIdx = win.getSelectedGlobalIndex();
                    if (globalIdx != -1) {
                        List<RenderableLine> lines = renderer.lineCache.get(globalIdx);
                        if (lines != null && !lines.isEmpty()) {
                            float scaleTextS = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSize)) : 1.0f;
                            int lineHS    = Math.max(6, (int)(10 * scaleTextS));
                            int contentHS = win.height - tabBarHS - 8;
                            int maxLines2 = Math.max(1, contentHS / lineHS);
                            int offsetS   = data.scrollOffsets.getOrDefault(globalIdx, 0);
                            int endS      = Math.max(0, lines.size() - offsetS);
                            int startS    = Math.max(0, endS - maxLines2);
                            int baseYS    = win.y + win.height - lineHS - 8;
                            for (int i = endS - 1; i >= startS; i--) {
                                int lineTop = baseYS - (endS - 1 - i) * lineHS;
                                if (my >= lineTop && my < lineTop + lineHS) {
                                    RenderableLine rl = lines.get(i);
                                    if (rl.sourceMsg != null) {
                                        // Prefer plainText (unformatted full message)
                                        if (rl.sourceMsg.plainText != null)
                                            foundName = ChatTabData.extractPlayerName(rl.sourceMsg.plainText);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }

                if (foundName != null && !foundName.isEmpty()) {
                    playerMenuName = foundName;
                    playerMenuX    = mx + 2;
                    playerMenuY    = my + 2;
                    showPlayerMenu = true;
                }
                break; // only handle topmost window
            }
        }
        rightClickWasDown = rightDown;

        // Draw a ghost of the tab being dragged, with a hint label
        if (isDraggingTab && draggingTabGlobalIndex < data.tabs.size()) {
            String tabName = data.tabs.get(draggingTabGlobalIndex);
            int ghostW = mc.fontRendererObj.getStringWidth(tabName) + 18;
            Gui.drawRect(dragTabVisualX - dragTabMouseOffsetX, dragTabVisualY - 8,
                    dragTabVisualX - dragTabMouseOffsetX + ghostW, dragTabVisualY + 6, 0xAA222233);
            mc.fontRendererObj.drawString(tabName, dragTabVisualX - dragTabMouseOffsetX + 9,
                    dragTabVisualY - 4, 0xFFFFFF);
            if (dropTargetWindowIndex != -1)
                mc.fontRendererObj.drawString("+ merge", dragTabVisualX + 5, dragTabVisualY - 16, 0x00FFAA);
            else if (tabIsDetached)
                mc.fontRendererObj.drawString("+ new window", dragTabVisualX + 5, dragTabVisualY - 16, 0x00CCFF);
        }

        renderer.drawHoverTooltip(mx, my);

        // Draw player context menu
        if (showPlayerMenu) {
            menuOpenFrames++;
            int pmH = PM_ROW * PM_LABELS.length + 18;
            int pmX = Math.min(playerMenuX, sr.getScaledWidth()  - PM_W - 2);
            int pmY = Math.min(playerMenuY, sr.getScaledHeight() - pmH   - 2);
            Gui.drawRect(pmX+2, pmY+2, pmX+PM_W+2, pmY+pmH+2, 0x66000000);
            Gui.drawRect(pmX, pmY, pmX+PM_W, pmY+pmH, 0xF0141820);
            Gui.drawRect(pmX, pmY, pmX+PM_W, pmY+16, 0xF00D1015);
            Gui.drawRect(pmX, pmY+15, pmX+PM_W, pmY+16, 0xFF4E9EFF);
            String header = playerMenuName;
            while (mc.fontRendererObj.getStringWidth(header + "...") > PM_W - 8 && header.length() > 1)
                header = header.substring(0, header.length() - 1);
            if (!header.equals(playerMenuName)) header += "...";
            mc.fontRendererObj.drawString(header, pmX + 5, pmY + 4, 0xFF4E9EFF);
            String[] colors = {"FFCC44", "FF4444"};
            for (int i = 0; i < PM_LABELS.length; i++) {
                int ry = pmY + 18 + i * PM_ROW;
                boolean rHov = mx>=pmX && mx<=pmX+PM_W && my>=ry && my<=ry+PM_ROW;
                Gui.drawRect(pmX, ry, pmX+PM_W, ry+PM_ROW, rHov ? 0xFF1E2836 : 0xFF141820);
                mc.fontRendererObj.drawString(PM_LABELS[i], pmX+8, ry+4,
                        rHov ? (int)(0xFF000000L | Long.parseLong(colors[i], 16)) : 0xFFBBBBBB);
            }
        } else {
            menuOpenFrames = 0;
        }
    }

    /** Moves the scroll position to match the mouse's position on the scroll track. */
    private void handleScrollBarDrag(int mx, int my) {
        ChatTabData.ChatWindowInstance win = data.windows.get(scrollBarDragWindowIndex);
        int globalIdx = win.getSelectedGlobalIndex();
        if (globalIdx == -1) return;
        List<RenderableLine> lines = renderer.lineCache.get(globalIdx);
        if (lines == null) return;

        int w       = scrollBarDragWindowIndex;
        int minOff  = renderer.dayScrollMin.getOrDefault(w, 0);
        int maxOff  = renderer.dayScrollMax.getOrDefault(w,
                Math.max(0, lines.size() - (win.height - 30) / 10));
        // Use dynamic tab bar height matching renderScrollBar
        float scaleTab = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTabs)) : 1.0f;
        int tabBarH = Math.max(16, (int)(22 * scaleTab));
        int barAreaY = win.y + tabBarH + 3;
        int barAreaH = win.height - tabBarH - 13;
        double fraction = 1.0 - Math.max(0.0, Math.min(1.0, (double)(my - barAreaY) / barAreaH));
        int newOffset = minOff + (int)(fraction * (maxOff - minOff));
        newOffset = Math.max(minOff, Math.min(maxOff, newOffset));
        data.scrollOffsets.put(globalIdx, newOffset);
        renderer.targetCacheScrollOffset.put(w, Integer.MIN_VALUE);
    }

    /** Updates ghost position, drop-target window, detach state, and reorder insert slot. */
    private void handleTabDrag(int mx, int my) {
        dragTabVisualX = mx; dragTabVisualY = my;
        dropTargetWindowIndex = -1;
        float tabScaleDrag = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTabs)) : 1.0f;
        for (int w = 0; w < data.windows.size(); w++) {
            ChatTabData.ChatWindowInstance win = data.windows.get(w);
            if (win == draggingTabSourceWindow) continue;
            int tbH = Math.max(16, (int)(22 * tabScaleDrag));
            if (mx >= win.x && mx <= win.x + win.width && my >= win.y && my <= win.y + tbH) {
                dropTargetWindowIndex = w; break;
            }
        }
        if (draggingTabSourceWindow != null && data.windows.contains(draggingTabSourceWindow)) {
            ChatTabData.ChatWindowInstance src = draggingTabSourceWindow;
            int srcTabBarH = Math.max(16, (int)(22 * tabScaleDrag));
            if (!tabIsDetached) {
                if (my < src.y - 20 || my > src.y + srcTabBarH + 20) tabIsDetached = true;
            } else {
                if (mx >= src.x && mx <= src.x + src.width && my >= src.y && my <= src.y + srcTabBarH)
                    tabIsDetached = false;
            }
        } else {
            tabIsDetached = true;
        }
        // Compute in-window reorder insert position
        if (!tabIsDetached && draggingTabSourceWindow != null && data.windows.contains(draggingTabSourceWindow)) {
            ChatTabData.ChatWindowInstance src = draggingTabSourceWindow;
            float tabScale2 = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTabs)) : 1.0f;
            int cx = src.x + 5;
            tabReorderInsertPos = 0;
            for (int li = 0; li < src.tabIndices.size(); li++) {
                int gIdx = src.tabIndices.get(li);
                if (gIdx >= data.tabs.size()) continue;
                int tabW   = (int)(Minecraft.getMinecraft().fontRendererObj.getStringWidth(data.tabs.get(gIdx)) * tabScale2) + 18 + 4;
                int tabMid = cx + tabW / 2;
                if (mx > tabMid) tabReorderInsertPos = li + 1;
                cx += tabW;
            }
        } else {
            tabReorderInsertPos = -1;
        }
    }

    // -------------------------------------------------------------------------
    // Window draw
    // -------------------------------------------------------------------------

    private void drawWindow(Minecraft mc, int winIdx, ChatTabData.ChatWindowInstance win,
                            int mx, int my) {
        // Compute tab bar height from tab font scale
        float tabScale = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTabs)) : 1.0f;
        int tabBarH = Math.max(16, (int)(22 * tabScale));
        // Y offset for text inside the tab bar (vertically centered)
        int tabTextY = win.y + (tabBarH - 9) / 2;

        // Background + top bar
        Gui.drawRect(win.x, win.y + tabBarH, win.x + win.width, win.y + win.height,
                data.getHex(data.colorBackground, data.opacBackground));
        Gui.drawRect(win.x, win.y, win.x + win.width, win.y + tabBarH,
                data.getHex(data.colorTopBar, data.opacTopBar));

        // Custom input bar (window 0 only)
        if (winIdx == 0 && data.hideDefaultChat && mc.currentScreen instanceof GuiChat) {
            GuiTextField vf = input.getVanillaInputField((GuiChat) mc.currentScreen);
            if (vf != null) {
                // Keep vanilla field off-screen so it isn't drawn
                vf.width = 0; vf.yPosition = -100;

                if (customChatField == null) {
                    customChatField = new GuiTextField(999, mc.fontRendererObj,
                            win.x + 4, win.y + win.height + 4, win.width - 8, 12);
                    customChatField.setMaxStringLength(256);
                    customChatField.setEnableBackgroundDrawing(false);
                    // Seed from vanilla only on first creation (e.g. /command pre-fill)
                    if (!vf.getText().isEmpty()) {
                        customChatField.setText(vf.getText());
                        customChatField.setCursorPositionEnd();
                    }
                    customChatField.setFocused(true);
                    lastSentToVanilla = customChatField.getText();
                }

                String vfText = vf.getText();
                // If vanilla changed its own text (tab-autocomplete callback from server),
                // pull that change into customChatField.
                if (!vfText.equals(lastSentToVanilla)) {
                    customChatField.setText(vfText);
                    customChatField.setCursorPositionEnd();
                }

                // Always keep vanilla in sync with what we have, so future TAB
                // presses / autocomplete requests have the right text to work from.
                vf.setText(customChatField.getText());
                vf.setCursorPosition(customChatField.getCursorPosition());
                lastSentToVanilla = customChatField.getText();

                Gui.drawRect(win.x, win.y + win.height,
                        win.x + win.width, win.y + win.height + 16,
                        data.getHex(data.colorInput, data.opacInput));
                customChatField.xPosition = win.x + 4;
                customChatField.yPosition = win.y + win.height + 4;
                customChatField.width     = win.width - 8;
                customChatField.drawTextBox();
            }
        }

        // Gear icon (right edge of tab bar, vertically centered)
        int gearSize = Math.max(9, (int)(9 * tabScale));
        renderer.drawTabString(mc, "\u2699", win.x + win.width - gearSize - 6, tabTextY, 0xFFFFFFFF);
        if (!data.isLocked)
            Gui.drawRect(win.x + win.width - 5, win.y + win.height - 5,
                    win.x + win.width, win.y + win.height, 0x55FFFFFF);

        // Tabs
        int selectionHex = data.getHex(data.colorSelection, 255);
        int curX = win.x + 5;
        List<Integer> tabStartXList = new ArrayList<>();

        for (int li = 0; li < win.tabIndices.size(); li++) {
            int globalIdx = win.tabIndices.get(li);
            if (globalIdx >= data.tabs.size()) continue;

            String tabName = (editingTabGlobalIndex == globalIdx && renameField != null)
                    ? renameField.getText() : data.tabs.get(globalIdx);
            int tabW = (int)(mc.fontRendererObj.getStringWidth(tabName) * tabScale) + 18;

            if (isDraggingTab && draggingTabGlobalIndex == globalIdx && tabIsDetached) {
                tabStartXList.add(curX);
                curX += tabW + 4;
                continue;
            }
            tabStartXList.add(curX);

            if (dropTargetWindowIndex == winIdx)
                Gui.drawRect(win.x, win.y, win.x + win.width, win.y + tabBarH, 0x3300FFFF);
            if (li == win.selectedLocalTab)
                Gui.drawRect(curX, win.y + tabBarH - 2, curX + tabW, win.y + tabBarH,
                        data.getHex(data.colorSelection, data.opacSelection));
            // Notification dot
            int dotSize = Math.max(3, (int)(4 * tabScale));
            if (data.showNotifications && data.tabNotifications.getOrDefault(globalIdx, false))
                Gui.drawRect(curX + 2, win.y + 3, curX + 2 + dotSize, win.y + 3 + dotSize, selectionHex);

            if (editingTabGlobalIndex == globalIdx && renameField != null) {
                renameField.xPosition = curX + 9; renameField.yPosition = tabTextY;
                renameField.drawTextBox();
            } else {
                int tabColor = (pendingDeleteGlobalIndex == globalIdx) ? 0xFFFF5555 : 0xFFFFFFFF;
                renderer.drawTabString(mc, data.tabs.get(globalIdx), curX + 9, tabTextY, tabColor);
            }
            curX += tabW + 4;
        }

        // Reorder insert-position indicator
        if (isDraggingTab && !tabIsDetached && draggingTabSourceWindow == win
                && tabReorderInsertPos != -1) {
            int insertX = (tabReorderInsertPos < tabStartXList.size())
                    ? tabStartXList.get(tabReorderInsertPos) - 2
                    : curX - 2;
            Gui.drawRect(insertX, win.y + 2, insertX + 2, win.y + tabBarH - 2, 0xFFFFFFFF);
        }

        renderer.drawTabString(mc, "[+]", curX, tabTextY, selectionHex);

        // Message content
        renderer.renderWindowContent(mc, win, 255, false);

        // Collect active hover/click targets
        renderer.activeHoverTargets.addAll(
                renderer.hoverTargetCache.getOrDefault(winIdx, Collections.emptyList()));
        renderer.activeClickTargets.addAll(
                renderer.clickTargetCache.getOrDefault(winIdx, Collections.emptyList()));
    }

    // -------------------------------------------------------------------------
    // Tab drop  —  called when the mouse button is released during a tab drag
    // -------------------------------------------------------------------------

    private void finalizeDrop(int mx, int my, ScaledResolution sr) {
        isDraggingTab = false;
        if (draggingTabGlobalIndex < 0 || draggingTabGlobalIndex >= data.tabs.size()) {
            resetDragState(); return;
        }

        if (!tabIsDetached) {
            ChatTabData.ChatWindowInstance srcWin = draggingTabSourceWindow;
            if (srcWin != null && data.windows.contains(srcWin) && tabReorderInsertPos != -1) {
                int currentLocalIdx = srcWin.tabIndices.indexOf(draggingTabGlobalIndex);
                if (currentLocalIdx != -1) {
                    int insertPos = tabReorderInsertPos;
                    if (insertPos > currentLocalIdx) insertPos--;
                    if (insertPos != currentLocalIdx) {
                        srcWin.tabIndices.remove(currentLocalIdx);
                        srcWin.tabIndices.add(insertPos, draggingTabGlobalIndex);
                        srcWin.selectedLocalTab = insertPos;
                    }
                }
            }
        } else if (dropTargetWindowIndex != -1 && dropTargetWindowIndex < data.windows.size()) {
            data.mergeTabIntoWindow(draggingTabGlobalIndex, dropTargetWindowIndex);
        } else {
            ChatTabData.ChatWindowInstance srcWin = draggingTabSourceWindow;
            if (srcWin != null && data.windows.contains(srcWin) && srcWin.tabIndices.size() > 1)
                data.detachTab(draggingTabGlobalIndex, mx - 20, my - 11);
        }
        resetDragState();
        data.save();
    }

    private void resetDragState() {
        draggingTabGlobalIndex  = -1;
        draggingTabSourceWindow = null;
        dropTargetWindowIndex   = -1;
        tabIsDetached           = false;
        tabReorderInsertPos     = -1;
    }

    // -------------------------------------------------------------------------
    // Mouse click
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onMouseClick(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || !Mouse.getEventButtonState()) return;
        int mx  = Mouse.getEventX() * event.gui.width / Minecraft.getMinecraft().displayWidth;
        int my  = event.gui.height - Mouse.getEventY() * event.gui.height
                / Minecraft.getMinecraft().displayHeight - 1;
        int btn = Mouse.getEventButton();

        // ── Player context menu clicks ──────────────────────────────────────
        if (showPlayerMenu && menuOpenFrames >= 3) {
            ScaledResolution srPM = new ScaledResolution(Minecraft.getMinecraft());
            int pmH = PM_ROW * PM_LABELS.length + 18;
            int pmX = Math.min(playerMenuX, srPM.getScaledWidth()  - PM_W - 2);
            int pmY = Math.min(playerMenuY, srPM.getScaledHeight() - pmH   - 2);
            if (btn == 0 && mx >= pmX && mx <= pmX+PM_W && my >= pmY && my <= pmY+pmH) {
                int row = (my - pmY - 18) / PM_ROW;
                if (row >= 0 && row < PM_LABELS.length) {
                    switch (row) {
                        case 0:
                            data.mutedPlayers.put(playerMenuName,
                                    System.currentTimeMillis() + 10 * 60 * 1000L);
                            break;
                        case 1:
                            data.mutedPlayers.put(playerMenuName, Long.MAX_VALUE);
                            break;
                    }
                    data.filterVersion++;
                    data.save();
                }
                showPlayerMenu = false;
                event.setCanceled(true); return;
            }
            // Left-click outside menu dismisses it
            if (btn == 0) showPlayerMenu = false;
        }

        if (isSettingsOpen) {
            settings.mouseClicked(mx, my, btn);
            if (settings.isCloseRequested()) isSettingsOpen = false;
            return;
        }

        for (int w = data.windows.size() - 1; w >= 0; w--) {
            ChatTabData.ChatWindowInstance win = data.windows.get(w);
            float tabScale = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTabs)) : 1.0f;
            int tabBarH = Math.max(16, (int)(22 * tabScale));

            // Gear → open settings
            if (btn == 0 && mx >= win.x + win.width - 20 && mx <= win.x + win.width
                    && my >= win.y && my <= win.y + tabBarH) {
                isSettingsOpen = true; event.setCanceled(true); return;
            }
            // Resize handle
            if (!data.isLocked && btn == 0
                    && mx >= win.x + win.width - 10 && mx <= win.x + win.width
                    && my >= win.y + win.height - 10 && my <= win.y + win.height) {
                resizingWindowIndex = w; event.setCanceled(true); return;
            }
            // Scroll bar / day nav
            if (btn == 0 && handleScrollClick(w, win, mx, my, event)) return;
            // Chat component click (left-click)
            if (btn == 0 && my > win.y + tabBarH && my < win.y + win.height) {
                for (ChatTargets.ClickTarget t
                        : renderer.clickTargetCache.getOrDefault(w, Collections.emptyList())) {
                    if (mx >= t.x1 && mx <= t.x2 && my >= t.y1 && my <= t.y2) {
                        input.dispatchClickEvent(t.clickEvent, customChatField);
                        event.setCanceled(true); return;
                    }
                }
            }
            // Top bar — tabs + drag + [+]
            if (my >= win.y && my <= win.y + tabBarH) {
                if (handleTopBarClick(w, win, mx, my, btn, event)) return;
            }
        }
    }

    /** Checks if the click hit the scroll bar track or day-nav buttons for this window.
     *  Returns true if the event was consumed. */
    private boolean handleScrollClick(int w, ChatTabData.ChatWindowInstance win,
                                      int mx, int my,
                                      GuiScreenEvent.MouseInputEvent.Pre event) {
        int globalIdx = win.getSelectedGlobalIndex();
        if (globalIdx == -1) return false;
        List<RenderableLine> lines = renderer.lineCache.get(globalIdx);
        if (lines == null || lines.size() <= (win.height - 30) / 10) return false;

        int barX = renderer.dayNavBarX.getOrDefault(w, win.x + win.width - 4);
        float scaleTab = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTabs)) : 1.0f;
        int tabBarH  = Math.max(16, (int)(22 * scaleTab));
        int barAreaY = win.y + tabBarH + 3;
        int barAreaH = win.height - tabBarH - 13;
        int prevBtnY = renderer.dayNavPrevY.getOrDefault(w, -1);
        int nextBtnY = renderer.dayNavNextY.getOrDefault(w, -1);

        // ▲ older day
        if (prevBtnY != -1 && mx >= barX - 8 && mx <= barX + 8
                && my >= prevBtnY && my <= prevBtnY + 9) {
            int newOff = renderer.dayScrollMin.getOrDefault(w, 0) + 1;
            newOff = Math.max(0, Math.min(Math.max(0, lines.size() - (win.height - 30) / 10), newOff));
            data.scrollOffsets.put(globalIdx, newOff);
            renderer.targetCacheScrollOffset.put(w, Integer.MIN_VALUE);
            event.setCanceled(true); return true;
        }
        // ▼ newer day
        if (nextBtnY != -1 && mx >= barX - 8 && mx <= barX + 8
                && my >= nextBtnY && my <= nextBtnY + 9) {
            int newOff = renderer.dayScrollMax.getOrDefault(w, 0) - 1;
            newOff = Math.max(0, Math.min(Math.max(0, lines.size() - (win.height - 30) / 10), newOff));
            data.scrollOffsets.put(globalIdx, newOff);
            renderer.targetCacheScrollOffset.put(w, Integer.MIN_VALUE);
            event.setCanceled(true); return true;
        }
        // Track drag
        if (mx >= barX - 2 && mx <= barX + 4 && my >= barAreaY && my <= barAreaY + barAreaH) {
            isDraggingScrollBar      = true;
            scrollBarDragWindowIndex = w;
            scrollBarDragStartY      = my;
            scrollBarDragStartOffset = data.scrollOffsets.getOrDefault(globalIdx, 0);
            event.setCanceled(true); return true;
        }
        return false;
    }

    /** Handles a click in the top bar — tab select/drag, [+] add tab, or start window drag.
     *  Returns true if the event was consumed. */
    private boolean handleTopBarClick(int w, ChatTabData.ChatWindowInstance win,
                                      int mx, int my, int btn,
                                      GuiScreenEvent.MouseInputEvent.Pre event) {
        float tabScale = data.fontSizeEnabled ? Math.max(0.5f, Math.min(3.0f, data.fontSizeTabs)) : 1.0f;
        int cx = win.x + 5;
        for (int li = 0; li < win.tabIndices.size(); li++) {
            int globalIdx = win.tabIndices.get(li);
            if (globalIdx >= data.tabs.size()) continue;
            int tw = (int)(Minecraft.getMinecraft().fontRendererObj.getStringWidth(data.tabs.get(globalIdx)) * tabScale) + 18;
            if (mx >= cx && mx <= cx + tw) {
                handleTabClick(w, li, globalIdx, btn, mx, cx);
                event.setCanceled(true); return true;
            }
            cx += tw + 4;
        }
        // [+] button
        if (btn == 0 && mx >= cx && mx <= cx + 20) {
            data.addTab();
            pendingDeleteGlobalIndex = -1; editingTabGlobalIndex = -1;
            if (w != 0 && !data.windows.isEmpty()) {
                int newIdx    = data.tabs.size() - 1;
                int winOfNew  = data.windowIndexForTab(newIdx);
                if (winOfNew != w && winOfNew != -1) {
                    data.windows.get(winOfNew).tabIndices.remove((Integer) newIdx);
                    data.windows.get(w).tabIndices.add(newIdx);
                    data.save();
                }
            }
            clampWindowToScreen(win, new ScaledResolution(Minecraft.getMinecraft()));
            event.setCanceled(true); return true;
        }
        // Drag window
        if (!data.isLocked && btn == 0) {
            draggingWindowIndex = w;
            dragWinOffsetX = mx - win.x; dragWinOffsetY = my - win.y;
            event.setCanceled(true); return true;
        }
        return false;
    }

    private void handleTabClick(int winIdx, int localIdx, int globalIdx,
                                int btn, int mx, int tabX) {
        ChatTabData.ChatWindowInstance win = data.windows.get(winIdx);
        if (btn == 0) {
            if (globalIdx == lastClickedGlobalIndex
                    && (System.currentTimeMillis() - lastClickTime) < 350) {
                editingTabGlobalIndex = globalIdx;
                renameField = new GuiTextField(0, Minecraft.getMinecraft().fontRendererObj, 0, 0, 100, 12);
                renameField.setEnableBackgroundDrawing(false);
                renameField.setText(data.tabs.get(globalIdx));
                renameField.setFocused(true);
            } else {
                win.selectedLocalTab = localIdx;
                data.tabNotifications.put(globalIdx, false);
                editingTabGlobalIndex  = -1;
                isDraggingTab          = true;
                draggingTabGlobalIndex = globalIdx;
                draggingTabSourceWindow = win;
                dragTabMouseOffsetX    = mx - tabX;
                dragTabVisualX         = mx; dragTabVisualY = win.y + 11;
                tabIsDetached          = false;
                dropTargetWindowIndex  = -1;
            }
        } else if (btn == 1) {
            if (pendingDeleteGlobalIndex == globalIdx) {
                pendingDeleteGlobalIndex = -1; editingTabGlobalIndex = -1;
                data.deleteTab(globalIdx);
                isDraggingTab = false; draggingTabGlobalIndex = -1;
                draggingTabSourceWindow = null; tabIsDetached = false;
                dropTargetWindowIndex = -1;
            } else {
                pendingDeleteGlobalIndex = globalIdx;
            }
        }
        lastClickTime = System.currentTimeMillis();
        lastClickedGlobalIndex = globalIdx;
    }

    // -------------------------------------------------------------------------
    // Keyboard
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onKeyTyped(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!Keyboard.getEventKeyState()) return;
        int  k = Keyboard.getEventKey();
        char c = Keyboard.getEventCharacter();

        // Settings open
        if (isSettingsOpen) {
            if (k == Keyboard.KEY_ESCAPE && !settings.isRecordingKeybind()) {
                isSettingsOpen = false;
            } else {
                settings.keyTyped(c, k);
            }
            event.setCanceled(true); return;
        }

        // Tab rename
        if (editingTabGlobalIndex != -1 && renameField != null
                && editingTabGlobalIndex < data.tabs.size()) {
            if (k == Keyboard.KEY_RETURN) {
                if (!renameField.getText().trim().isEmpty())
                    data.tabs.set(editingTabGlobalIndex, renameField.getText().trim());
                data.save(); editingTabGlobalIndex = -1;
            } else if (k == Keyboard.KEY_ESCAPE) {
                editingTabGlobalIndex = -1;
            } else {
                renameField.textboxKeyTyped(c, k);
            }
            event.setCanceled(true); return;
        }

        // Send message
        if (k == Keyboard.KEY_RETURN && event.gui instanceof GuiChat) {
            input.trySendMessage((GuiChat) event.gui, customChatField);
            hudFadeStartTime = System.currentTimeMillis();
            // If a command was injected into globalLog by trySendMessage, invalidate
            // the line cache for every tab so the new entry shows up immediately.
            if (!data.globalLog.isEmpty() && data.globalLog.get(data.globalLog.size() - 1).isCommand) {
                for (int i = 0; i < data.tabs.size(); i++) {
                    renderer.lineCache.remove(i);
                    renderer.lineCacheHistorySize.put(i, -1);
                }
            }
            Minecraft.getMinecraft().displayGuiScreen(null);
            event.setCanceled(true);
            return;
        }

        // History navigation — UP = older, DOWN = newer
        if ((k == Keyboard.KEY_UP || k == Keyboard.KEY_DOWN) && event.gui instanceof GuiChat) {
            if (customChatField != null) {
                input.navigateHistory(k == Keyboard.KEY_UP, customChatField);
                event.setCanceled(true);
            }
            return;
        }

        // TAB autocomplete — vanilla field is kept in sync every draw frame, so
        // just let the event fall through to vanilla GuiChat unchanged.
        // The draw loop will detect when vanilla's autocomplete callback mutates
        // the vanilla field and copy the result back to customChatField.
        if (k == Keyboard.KEY_TAB && event.gui instanceof GuiChat) {
            return; // not canceled — vanilla processes TAB normally
        }

        // Route all other keystrokes directly to customChatField so it is the
        // sole source of truth. This also resets history browsing when the user
        // types a new character.
        if (customChatField != null && event.gui instanceof GuiChat
                && k != Keyboard.KEY_ESCAPE) {
            // Any real character typed resets history cursor to live input
            if (c >= 32 || k == Keyboard.KEY_BACK || k == Keyboard.KEY_DELETE) {
                input.resetHistoryCursor();
            }
            customChatField.textboxKeyTyped(c, k);
            event.setCanceled(true);
        }
    }
}
