package com.betterchat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.lang.reflect.Field;
import java.util.*;

public class ChatTabHandler {
    private final ChatTabData data = new ChatTabData();
    private final ChatSettingsGui settings = new ChatSettingsGui(data);

    private int editingTabGlobalIndex = -1;
    private int pendingDeleteGlobalIndex = -1;
    private boolean isSettingsOpen = false;
    private boolean wasChatOpen = false;
    private GuiTextField renameField, customChatField;
    private long lastClickTime = 0;
    private int lastClickedGlobalIndex = -1;

    // -------------------------------------------------------------------------
    // Per-window drag state
    // -------------------------------------------------------------------------
    // Window being dragged / resized
    private int draggingWindowIndex = -1;
    private int dragWinOffsetX, dragWinOffsetY;
    private int resizingWindowIndex = -1;

    // Tab drag state — tracks a single tab being dragged globally across windows
    private boolean isDraggingTab = false;
    private int draggingTabGlobalIndex = -1;   // which global tab is being dragged
    private ChatTabData.ChatWindowInstance draggingTabSourceWindow = null;  // window it came from (by reference)
    private int dragTabVisualX = 0, dragTabVisualY = 0; // current mouse position for ghost rendering
    private int dragTabMouseOffsetX = 0;
    // When the tab has been "detached" (mouse left top bar), we show a ghost and look for drop targets
    private boolean tabIsDetached = false;
    // Drop target highlight: window index that the dragged tab is hovering over (-1 = none = will create new)
    private int dropTargetWindowIndex = -1;
    // In-window reorder: insert position (0 = before first tab, n = after last tab). -1 = not reordering
    private int tabReorderInsertPos = -1;

    // Scroll bar drag (per-window — track window index too)
    private boolean isDraggingScrollBar = false;
    private int scrollBarDragWindowIndex = -1;
    private int scrollBarDragStartY = 0;
    private int scrollBarDragStartOffset = 0;

    // Per-window day-scroll state: min/max scroll offset for the currently visible day,
    // and the Y positions of the prev/next day nav buttons (for click detection).
    // Populated each frame by renderScrollBar; read by onMouseClick and the drag handler.
    private final Map<Integer, Integer> dayScrollMin  = new HashMap<>(); // offset of bottom of current day
    private final Map<Integer, Integer> dayScrollMax  = new HashMap<>(); // offset of top of current day
    private final Map<Integer, Integer> dayNavPrevY   = new HashMap<>(); // Y of "prev day" (older) button
    private final Map<Integer, Integer> dayNavNextY   = new HashMap<>(); // Y of "next day" (newer) button
    private final Map<Integer, Integer> dayNavBarX    = new HashMap<>(); // X of scroll bar (for hit-test)

    // -------------------------------------------------------------------------
    // Hover / click / tooltip (keyed by window index)
    // -------------------------------------------------------------------------
    private final Map<Integer, List<HoverTarget>> hoverTargetCache = new HashMap<>();
    private final Map<Integer, List<ClickTarget>>  clickTargetCache  = new HashMap<>();
    private final Map<Integer, Integer>  targetCacheScrollOffset = new HashMap<>();
    private final Map<Integer, Integer>  targetCacheWindowX = new HashMap<>();
    private final Map<Integer, Integer>  targetCacheWindowY = new HashMap<>();
    private final Map<Integer, Integer>  targetCacheWindowH = new HashMap<>();

    private final List<HoverTarget> activeHoverTargets = new ArrayList<>();
    private final List<ClickTarget>  activeClickTargets  = new ArrayList<>();

    // Line cache keyed by global tab index
    private final Map<Integer, List<RenderableLine>> lineCache = new HashMap<>();
    private final Map<Integer, Integer> lineCacheHistorySize = new HashMap<>();
    private final Map<Integer, Integer> lineCacheFilterVersion = new HashMap<>();
    private final Map<Integer, Integer> lineCacheWidth = new HashMap<>();

    private HoverEvent lastHoveredEvent = null;
    private List<TooltipLine> cachedTooltipLines = null;
    private int tooltipMouseX, tooltipMouseY;

    private long lastPlayerCommandTime = 0;
    private static final long COMMAND_RESPONSE_WINDOW_MS = 3000;
    private long lastPlayerSendTime = 0;
    private static final long SEND_ECHO_DEBOUNCE_MS = 1000;

    // Smooth one-way fade: set to 1.0 on new message, only decreases from there
    private float hudFade = 0f;
    private long hudFadeLastFrame = 0L;

    // -------------------------------------------------------------------------
    // Screen helpers
    // -------------------------------------------------------------------------
    private void clampWindowToScreen(ChatTabData.ChatWindowInstance win, ScaledResolution sr) {
        int screenW = sr.getScaledWidth(), screenH = sr.getScaledHeight();
        int requiredWidth = 5;
        for (int idx : win.tabIndices) requiredWidth += Minecraft.getMinecraft().fontRendererObj.getStringWidth(data.tabs.get(idx)) + 18 + 4;
        requiredWidth += 45; // [+] + gear
        if (win.width < requiredWidth) win.width = requiredWidth;
        if (win.height < 50) win.height = 50;
        if (win.x < 0) win.x = 0;
        if (win.y < 0) win.y = 0;
        if (win.x + win.width  > screenW) win.x = screenW - win.width;
        if (win.y + win.height + 16 > screenH) win.y = screenH - win.height - 16;
        // Keep legacy single-window fields in sync for window 0
        if (!data.windows.isEmpty() && data.windows.get(0) == win) {
            data.windowX = win.x; data.windowY = win.y; data.windowWidth = win.width; data.windowHeight = win.height;
        }
    }

    // -------------------------------------------------------------------------
    // HUD fade
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public void onRenderHUD(RenderGameOverlayEvent.Text event) {
        if (!data.hideDefaultChat) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiChat) return;
        long elapsed = System.currentTimeMillis() - data.lastMessageTime;
        if (elapsed > 7000) return;
        float fade = (elapsed > 6000) ? Math.max(0f, Math.min(1f, 1.0f - (float)(elapsed - 6000) / 1000f)) : 1.0f;
        if (fade <= 0f) return;
        int fadeAlpha = Math.max(0, Math.min(255, (int)(fade * 255)));

        for (ChatTabData.ChatWindowInstance win : data.windows) {
            int bgColor  = applyFadeToColor(data.getHex(data.colorFadeBackground, data.opacFadeBackground), fadeAlpha);
            int barColor = applyFadeToColor(data.getHex(data.colorFadeTopBar,     data.opacFadeTopBar),     fadeAlpha);
            Gui.drawRect(win.x, win.y + 22, win.x + win.width, win.y + win.height, bgColor);
            Gui.drawRect(win.x, win.y,      win.x + win.width, win.y + 22,         barColor);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, fade);
            renderWindowContent(mc, win, fadeAlpha, true);
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
        if (data.hideDefaultChat && event.type == RenderGameOverlayEvent.ElementType.CHAT) event.setCanceled(true);
    }

    // -------------------------------------------------------------------------
    // Chat received
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String formatted = event.message.getFormattedText();
        String plain     = event.message.getUnformattedText();
        // Keep playerName in sync so filter matching works
        data.playerName = Minecraft.getMinecraft().thePlayer.getName();
        boolean isLocal   = plain.startsWith("<" + data.playerName + ">") || plain.startsWith(data.playerName + ":");
        boolean isOtherPlayer = (plain.startsWith("<") && plain.contains(">")) || (plain.contains(":") && !plain.startsWith("["));
        boolean isCommand = plain.startsWith("/");
        boolean isCommandResponse = !isLocal && !isOtherPlayer && (System.currentTimeMillis() - lastPlayerCommandTime) < COMMAND_RESPONSE_WINDOW_MS;

        // Add to the global log — always, unconditionally
        ChatTabData.ChatMessage msg = new ChatTabData.ChatMessage(
            formatted, false, event.message,
            isLocal, isOtherPlayer, isCommand, isCommandResponse, plain);
        data.globalLog.add(msg);

        // Invalidate all tab line caches so they re-filter on next draw,
        // and fire notifications for tabs that match but aren't focused
        for (int i = 0; i < data.tabs.size(); i++) {
            if (data.messagePassesFilter(i, msg)) {
                lineCache.remove(i);
                lineCacheHistorySize.put(i, -1);
                for (ChatTabData.ChatWindowInstance win : data.windows) {
                    if (!isLocal && win.getSelectedGlobalIndex() != i && win.tabIndices.contains(i))
                        data.tabNotifications.put(i, true);
                }
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastPlayerSendTime > SEND_ECHO_DEBOUNCE_MS) {
            data.lastMessageTime = now;
            hudFade = 1.0f;
            hudFadeLastFrame = now;
        }
        data.save();
    }

    public void onPlayerSentCommand() { lastPlayerCommandTime = System.currentTimeMillis(); }

    // -------------------------------------------------------------------------
    // Draw Pre — cancel vanilla chat
    // -------------------------------------------------------------------------
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!(event.gui instanceof GuiChat)) return;
        if (data.hideDefaultChat) event.setCanceled(true);
    }

    // Fired whenever any GUI is opened (including null = GUI closed).
    // We use this to reset scroll to bottom whenever GuiChat is closed.
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (wasChatOpen && !(event.gui instanceof GuiChat)) {
            // Chat was open and is now closing — jump every tab back to the bottom
            for (int i = 0; i < data.tabs.size(); i++) data.scrollOffsets.put(i, 0);
            targetCacheScrollOffset.replaceAll((k, v) -> Integer.MIN_VALUE);
            wasChatOpen = false;
        }
        if (event.gui instanceof GuiChat) {
            wasChatOpen = true;
        }
    }

    // -------------------------------------------------------------------------
    // Main draw loop
    // -------------------------------------------------------------------------
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiChat)) { customChatField = null; return; }
        wasChatOpen = true;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        // Use Mouse.getX()/getY() (real-time position) instead of Mouse.getEventX()/getEventY()
        // (event-based values only update when a mouse event is polled, causing choppy drag/hover at ~5-10fps)
        int mx = Mouse.getX() * event.gui.width / mc.displayWidth;
        int my = event.gui.height - Mouse.getY() * event.gui.height / mc.displayHeight - 1;

        if (isSettingsOpen) { settings.draw(mx, my); return; }

        // --- Mouse release ---
        if (!Mouse.isButtonDown(0)) {
            if (draggingWindowIndex != -1 || resizingWindowIndex != -1) data.save();
            draggingWindowIndex = -1; resizingWindowIndex = -1;
            isDraggingScrollBar = false;

            // Handle tab drop
            if (isDraggingTab) {
                finalizeDrop(mx, my, sr);
            }
        }

        // --- Mouse held ---
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
                ChatTabData.ChatWindowInstance win = data.windows.get(scrollBarDragWindowIndex);
                int globalIdx = win.getSelectedGlobalIndex();
                if (globalIdx != -1) {
                    List<RenderableLine> lines = lineCache.get(globalIdx);
                    if (lines != null) {
                        int winIdx2 = scrollBarDragWindowIndex;
                        int minOff = dayScrollMin.getOrDefault(winIdx2, 0);
                        int maxOff = dayScrollMax.getOrDefault(winIdx2, Math.max(0, lines.size() - (win.height - 30) / 10));
                        int barAreaY = win.y + 25, barAreaH = win.height - 35;
                        // Map mouse Y across the track: top = maxOff (oldest in day), bottom = minOff (newest in day)
                        double fraction = 1.0 - Math.max(0.0, Math.min(1.0, (double)(my - barAreaY) / barAreaH));
                        int newOffset = minOff + (int)(fraction * (maxOff - minOff));
                        newOffset = Math.max(minOff, Math.min(maxOff, newOffset));
                        data.scrollOffsets.put(globalIdx, newOffset);
                        targetCacheScrollOffset.put(winIdx2, Integer.MIN_VALUE);
                    }
                }
            } else if (isDraggingTab) {
                // Update ghost position
                dragTabVisualX = mx; dragTabVisualY = my;
                // Determine drop target window (not the source)
                dropTargetWindowIndex = -1;
                for (int w = 0; w < data.windows.size(); w++) {
                    ChatTabData.ChatWindowInstance win = data.windows.get(w);
                    if (win == draggingTabSourceWindow) continue;
                    if (mx >= win.x && mx <= win.x + win.width && my >= win.y && my <= win.y + 22) {
                        dropTargetWindowIndex = w; break;
                    }
                }
                // Check if re-attaching to source window (mouse re-enters its top bar)
                if (draggingTabSourceWindow != null && data.windows.contains(draggingTabSourceWindow)) {
                    ChatTabData.ChatWindowInstance src = draggingTabSourceWindow;
                    if (!tabIsDetached) {
                        if (my < src.y - 20 || my > src.y + 22 + 20) tabIsDetached = true;
                    } else {
                        if (mx >= src.x && mx <= src.x + src.width && my >= src.y && my <= src.y + 22)
                            tabIsDetached = false;
                    }
                } else {
                    tabIsDetached = true;
                }
                // Compute in-window reorder insert position when not detached
                if (!tabIsDetached && draggingTabSourceWindow != null && data.windows.contains(draggingTabSourceWindow)) {
                    ChatTabData.ChatWindowInstance src = draggingTabSourceWindow;
                    int cx = src.x + 5;
                    tabReorderInsertPos = 0;
                    for (int li = 0; li < src.tabIndices.size(); li++) {
                        int gIdx = src.tabIndices.get(li);
                        if (gIdx >= data.tabs.size()) continue;
                        int tabW = Minecraft.getMinecraft().fontRendererObj.getStringWidth(data.tabs.get(gIdx)) + 18 + 4;
                        int tabMid = cx + tabW / 2;
                        if (mx > tabMid) tabReorderInsertPos = li + 1;
                        cx += tabW;
                    }
                } else {
                    tabReorderInsertPos = -1;
                }
            }
        }

        // --- Draw each window ---
        activeHoverTargets.clear(); activeClickTargets.clear();
        for (int w = 0; w < data.windows.size(); w++) {
            ChatTabData.ChatWindowInstance win = data.windows.get(w);
            clampWindowToScreen(win, sr);
            drawWindow(mc, w, win, mx, my, sr);
        }

        // --- Draw dragged tab ghost ---
        if (isDraggingTab && draggingTabGlobalIndex < data.tabs.size()) {
            String tabName = data.tabs.get(draggingTabGlobalIndex);
            int ghostW = mc.fontRendererObj.getStringWidth(tabName) + 18;
            Gui.drawRect(dragTabVisualX - dragTabMouseOffsetX, dragTabVisualY - 8, dragTabVisualX - dragTabMouseOffsetX + ghostW, dragTabVisualY + 6, 0xAA222233);
            mc.fontRendererObj.drawString(tabName, dragTabVisualX - dragTabMouseOffsetX + 9, dragTabVisualY - 4, 0xFFFFFF);
            // Show drop hint
            if (dropTargetWindowIndex != -1) {
                mc.fontRendererObj.drawString("+ merge", dragTabVisualX + 5, dragTabVisualY - 16, 0x00FFAA);
            } else if (tabIsDetached) {
                mc.fontRendererObj.drawString("+ new window", dragTabVisualX + 5, dragTabVisualY - 16, 0x00CCFF);
            }
        }

        drawHoverTooltip(mx, my);
    }

    private void drawWindow(Minecraft mc, int winIdx, ChatTabData.ChatWindowInstance win, int mx, int my, ScaledResolution sr) {
        // Background + top bar
        Gui.drawRect(win.x, win.y + 22, win.x + win.width, win.y + win.height, data.getHex(data.colorBackground, data.opacBackground));
        Gui.drawRect(win.x, win.y,      win.x + win.width, win.y + 22,         data.getHex(data.colorTopBar,     data.opacTopBar));

        // Input bar (only for window 0 / the focused one)
        if (winIdx == 0 && data.hideDefaultChat) {
            // find the vanilla field via the current GuiChat
            Minecraft mcInst = Minecraft.getMinecraft();
            if (mcInst.currentScreen instanceof GuiChat) {
                GuiTextField vf = getVanillaInputField((GuiChat) mcInst.currentScreen);
                if (vf != null) {
                    vf.width = 0; vf.yPosition = -100;
                    if (customChatField == null) {
                        customChatField = new GuiTextField(999, mc.fontRendererObj, win.x + 4, win.y + win.height + 4, win.width - 8, 12);
                        customChatField.setMaxStringLength(100); customChatField.setEnableBackgroundDrawing(false);
                    }
                    customChatField.setText(vf.getText());
                    customChatField.setCursorPosition(vf.getCursorPosition());
                    Gui.drawRect(win.x, win.y + win.height, win.x + win.width, win.y + win.height + 16, data.getHex(data.colorInput, data.opacInput));
                    customChatField.xPosition = win.x + 4; customChatField.yPosition = win.y + win.height + 4; customChatField.width = win.width - 8;
                    customChatField.drawTextBox();
                }
            }
        }

        // Gear icon
        mc.fontRendererObj.drawString("\u2699", win.x + win.width - 15, win.y + 7, 0xFFFFFF);

        // Resize handle
        if (!data.isLocked) Gui.drawRect(win.x + win.width - 5, win.y + win.height - 5, win.x + win.width, win.y + win.height, 0x55FFFFFF);

        // Tabs
        int selectionHex = data.getHex(data.colorSelection, 255);
        int curX = win.x + 5;
        // Collect tab X positions for the reorder indicator
        List<Integer> tabStartXList = new ArrayList<>();
        for (int li = 0; li < win.tabIndices.size(); li++) {
            int globalIdx = win.tabIndices.get(li);
            if (globalIdx >= data.tabs.size()) continue;
            // Skip drawing if this is the tab being dragged and it's detached
            if (isDraggingTab && draggingTabGlobalIndex == globalIdx && tabIsDetached) {
                tabStartXList.add(curX);
                curX += mc.fontRendererObj.getStringWidth(data.tabs.get(globalIdx)) + 22; continue;
            }
            tabStartXList.add(curX);
            int tw = mc.fontRendererObj.getStringWidth(
                    (editingTabGlobalIndex == globalIdx && renameField != null) ? renameField.getText() : data.tabs.get(globalIdx));
            int tabW = tw + 18;

            // Highlight drop target
            boolean isDropTarget = dropTargetWindowIndex == winIdx;
            if (isDropTarget) {
                Gui.drawRect(win.x, win.y, win.x + win.width, win.y + 22, 0x3300FFFF);
            }
            if (li == win.selectedLocalTab) Gui.drawRect(curX, win.y + 20, curX + tabW, win.y + 22, data.getHex(data.colorSelection, data.opacSelection));
            if (data.showNotifications && data.tabNotifications.getOrDefault(globalIdx, false)) Gui.drawRect(curX + 2, win.y + 4, curX + 6, win.y + 8, selectionHex);
            if (editingTabGlobalIndex == globalIdx && renameField != null) {
                renameField.xPosition = curX + 9; renameField.yPosition = win.y + 7; renameField.drawTextBox();
            } else {
                mc.fontRendererObj.drawString(data.tabs.get(globalIdx), curX + 9, win.y + 7, (pendingDeleteGlobalIndex == globalIdx) ? 0xFFFF5555 : 0xFFFFFF);
            }
            curX += tabW + 4;
        }

        // Draw reorder insert-position indicator (vertical bar between tabs)
        if (isDraggingTab && !tabIsDetached && draggingTabSourceWindow == win && tabReorderInsertPos != -1) {
            int insertX;
            if (tabReorderInsertPos < tabStartXList.size()) {
                insertX = tabStartXList.get(tabReorderInsertPos) - 2;
            } else {
                insertX = curX - 2; // after the last tab
            }
            Gui.drawRect(insertX, win.y + 2, insertX + 2, win.y + 20, 0xFFFFFFFF);
        }

        // [+] button
        mc.fontRendererObj.drawString("[+]", curX, win.y + 7, selectionHex);

        // Content
        renderWindowContent(mc, win, 255, false);

        // Collect hover/click targets for this window into the active lists
        activeHoverTargets.addAll(hoverTargetCache.getOrDefault(winIdx, Collections.emptyList()));
        activeClickTargets.addAll(clickTargetCache.getOrDefault(winIdx, Collections.emptyList()));
    }

    // -------------------------------------------------------------------------
    // Drop finalization
    // -------------------------------------------------------------------------
    private void finalizeDrop(int mx, int my, ScaledResolution sr) {
        isDraggingTab = false;
        if (draggingTabGlobalIndex < 0 || draggingTabGlobalIndex >= data.tabs.size()) {
            draggingTabGlobalIndex = -1; draggingTabSourceWindow = null;
            dropTargetWindowIndex = -1; tabIsDetached = false;
            return;
        }

        if (!tabIsDetached) {
            // Dropped back in source window — reorder if insert position changed
            ChatTabData.ChatWindowInstance srcWin = draggingTabSourceWindow;
            if (srcWin != null && data.windows.contains(srcWin) && tabReorderInsertPos != -1) {
                int currentLocalIdx = srcWin.tabIndices.indexOf(draggingTabGlobalIndex);
                if (currentLocalIdx != -1) {
                    // Adjust insert pos for the removal of the tab itself
                    int insertPos = tabReorderInsertPos;
                    if (insertPos > currentLocalIdx) insertPos--;
                    if (insertPos != currentLocalIdx) {
                        srcWin.tabIndices.remove(currentLocalIdx);
                        srcWin.tabIndices.add(insertPos, draggingTabGlobalIndex);
                        // Keep selectedLocalTab pointing at the moved tab
                        srcWin.selectedLocalTab = insertPos;
                    }
                }
            }
        } else if (dropTargetWindowIndex != -1 && dropTargetWindowIndex < data.windows.size()) {
            // Merge into target window
            data.mergeTabIntoWindow(draggingTabGlobalIndex, dropTargetWindowIndex);
        } else {
            // Detach: only spawn new window if source window still exists and has >1 tab
            ChatTabData.ChatWindowInstance srcWin = draggingTabSourceWindow;
            if (srcWin != null && data.windows.contains(srcWin) && srcWin.tabIndices.size() > 1) {
                data.detachTab(draggingTabGlobalIndex, mx - 20, my - 11);
            }
            // If source has only 1 tab, don't detach (keep it in place)
        }
        draggingTabGlobalIndex = -1;
        draggingTabSourceWindow = null;
        dropTargetWindowIndex = -1;
        tabIsDetached = false;
        tabReorderInsertPos = -1;
        data.save();
    }

    // -------------------------------------------------------------------------
    // Render window content
    // -------------------------------------------------------------------------
    private void renderWindowContent(Minecraft mc, ChatTabData.ChatWindowInstance win, int globalAlpha, boolean isHUD) {
        int globalIdx = win.getSelectedGlobalIndex();
        if (globalIdx == -1) return;

        int wrapWidth = win.width - 15;
        int winIdx = data.windows.indexOf(win);

        // The cache is stale if the global log has grown, the width changed, filters changed, or it was explicitly invalidated
        int cachedGlobalSize = lineCacheHistorySize.getOrDefault(globalIdx, -1);
        int cachedFilterVer  = lineCacheFilterVersion.getOrDefault(globalIdx, -1);
        boolean stale = !lineCacheWidth.containsKey(globalIdx)
                || lineCacheWidth.get(globalIdx) != wrapWidth
                || cachedGlobalSize != data.globalLog.size()
                || cachedFilterVer  != data.filterVersion
                || !lineCache.containsKey(globalIdx);
        if (stale) {
            // Rebuild by filtering the global log through this tab's settings
            List<ChatTabData.ChatMessage> history = data.buildFilteredHistory(globalIdx);
            List<RenderableLine> built = new ArrayList<>();
            for (ChatTabData.ChatMessage msg : history) {
                if (msg.isDateSeparator) built.add(new RenderableLine(msg.text, true, msg.time, msg.date, msg, 0));
                else {
                    List<String> wrapped = mc.fontRendererObj.listFormattedStringToWidth(msg.text, wrapWidth);
                    int charOffset = 0;
                    for (int j = 0; j < wrapped.size(); j++) {
                        built.add(new RenderableLine(wrapped.get(j), false, j == 0 ? msg.time : "", msg.date, msg, charOffset));
                        charOffset += EnumChatFormatting.getTextWithoutFormattingCodes(wrapped.get(j)).length();
                    }
                }
            }
            lineCache.put(globalIdx, built);
            lineCacheHistorySize.put(globalIdx, data.globalLog.size());
            lineCacheFilterVersion.put(globalIdx, data.filterVersion);
            lineCacheWidth.put(globalIdx, wrapWidth);
            hoverTargetCache.remove(winIdx); clickTargetCache.remove(winIdx);
            targetCacheScrollOffset.put(winIdx, Integer.MIN_VALUE);
        }

        List<RenderableLine> allLines = lineCache.get(globalIdx);
        if (allLines == null || allLines.isEmpty()) return;
        int maxLines   = (win.height - 30) / 10;
        int totalLines = allLines.size();
        int currentOffset = data.scrollOffsets.getOrDefault(globalIdx, 0);

        // Scroll wheel
        if (!isHUD && Mouse.hasWheel()) {
            Minecraft mcInst = Minecraft.getMinecraft();
            int mx2 = Mouse.getX() * mcInst.displayWidth / mcInst.displayWidth; // raw
            // Only scroll if mouse is inside this window
            int rawMx = Mouse.getX() * (mcInst.currentScreen != null ? mcInst.currentScreen.width : 1) / mcInst.displayWidth;
            int rawMy = (mcInst.currentScreen != null ? mcInst.currentScreen.height : 1) - Mouse.getY() * (mcInst.currentScreen != null ? mcInst.currentScreen.height : 1) / mcInst.displayHeight - 1;
            if (rawMx >= win.x && rawMx <= win.x + win.width && rawMy >= win.y && rawMy <= win.y + win.height) {
                int wheel = Mouse.getDWheel();
                if (wheel != 0) {
                    int newOffset = Math.max(0, Math.min(Math.max(0, totalLines - maxLines), currentOffset + (wheel > 0 ? 1 : -1)));
                    if (newOffset != currentOffset) {
                        currentOffset = newOffset;
                        data.scrollOffsets.put(globalIdx, currentOffset);
                        targetCacheScrollOffset.put(winIdx, Integer.MIN_VALUE);
                    }
                }
            }
        }

        int end   = Math.max(0, totalLines - currentOffset);
        int start = Math.max(0, end - maxLines);
        int baseY = win.y + win.height - 12;
        int y = baseY;

        for (int i = end - 1; i >= start; i--) {
            if (i < 0 || i >= allLines.size()) continue;
            RenderableLine line = allLines.get(i);
            if (line.isSeparator) {
                int tw = mc.fontRendererObj.getStringWidth(line.text);
                int cx = win.x + win.width / 2;
                Gui.drawRect(win.x + 5, y + 4, cx - tw/2 - 5, y + 5, 0x22FFFFFF);
                Gui.drawRect(cx + tw/2 + 5, y + 4, win.x + win.width - 5, y + 5, 0x22FFFFFF);
                mc.fontRendererObj.drawString(line.text, cx - tw/2, y, 0xAAAAAA | (globalAlpha << 24));
            } else {
                mc.fontRendererObj.drawStringWithShadow(line.text, win.x + 5, y, data.getHex(data.colorText, (int)(data.opacText / 255.0 * globalAlpha)));
                if (data.showTimeStamps && !line.time.isEmpty() && !isHUD) {
                    int timeW = mc.fontRendererObj.getStringWidth(line.time);
                    mc.fontRendererObj.drawString(line.time, win.x + win.width - timeW - 8, y, data.getHex(data.colorTime, (int)(data.opacTime / 255.0 * globalAlpha)));
                }
            }
            y -= 10;
        }

        if (!isHUD && totalLines > maxLines) {
            renderScrollBar(win, totalLines, maxLines, currentOffset, data.getHex(data.colorSelection, 255));
        }

        // Target cache rebuild
        if (!isHUD && winIdx != -1) {
            int cso = targetCacheScrollOffset.getOrDefault(winIdx, Integer.MIN_VALUE);
            int cwx = targetCacheWindowX.getOrDefault(winIdx, Integer.MIN_VALUE);
            int cwy = targetCacheWindowY.getOrDefault(winIdx, Integer.MIN_VALUE);
            int cwh = targetCacheWindowH.getOrDefault(winIdx, Integer.MIN_VALUE);
            if (!hoverTargetCache.containsKey(winIdx) || cso != currentOffset || cwx != win.x || cwy != win.y || cwh != win.height) {
                List<HoverTarget> nh = new ArrayList<>(); List<ClickTarget> nc = new ArrayList<>();
                y = baseY;
                for (int i = end - 1; i >= start; i--) {
                    if (i < 0 || i >= allLines.size()) continue;
                    RenderableLine line = allLines.get(i);
                    if (!line.isSeparator && line.sourceMsg != null && line.sourceMsg.rawComponent != null)
                        buildTargetsForLine(mc, line, y, win.x, nh, nc);
                    y -= 10;
                }
                hoverTargetCache.put(winIdx, nh); clickTargetCache.put(winIdx, nc);
                targetCacheScrollOffset.put(winIdx, currentOffset);
                targetCacheWindowX.put(winIdx, win.x); targetCacheWindowY.put(winIdx, win.y); targetCacheWindowH.put(winIdx, win.height);
            }
        }
    }

    private void renderScrollBar(ChatTabData.ChatWindowInstance win, int total, int visible, int offset, int color) {
        int globalIdx = win.getSelectedGlobalIndex();
        List<RenderableLine> allLines = (globalIdx != -1) ? lineCache.get(globalIdx) : null;
        if (allLines == null || allLines.isEmpty()) return;

        int winIdx   = data.windows.indexOf(win);
        int barX     = win.x + win.width - 4;
        int barAreaY = win.y + 25;
        int barAreaH = win.height - 35;
        if (barAreaH <= 0) return;

        // ── Build date segments ──────────────────────────────────────────────
        // Each segment: [firstLineIdx, lastLineIdx_inclusive, contentLineCount]
        // Lines are stored bottom-to-top in the scroll system (offset 0 = bottom).
        // allLines is ordered top-to-bottom (index 0 = oldest).
        List<int[]> segs = new ArrayList<>(); // [firstIdx, lastIdx, count]
        int si = -1;
        for (int i = 0; i < allLines.size(); i++) {
            if (allLines.get(i).isSeparator) {
                si = i; // separator starts a new day
            } else {
                if (si >= 0) {
                    // new segment starting at this separator
                    segs.add(new int[]{si, i, 0});
                    si = -1;
                } else if (segs.isEmpty()) {
                    // messages before any separator (no date header yet)
                    segs.add(new int[]{0, i, 0});
                }
                // extend last segment's last line
                segs.get(segs.size() - 1)[1] = i;
                segs.get(segs.size() - 1)[2]++;
            }
        }

        if (segs.isEmpty()) {
            // Fallback: no segments, plain scroll bar
            Gui.drawRect(barX, barAreaY, barX + 2, barAreaY + barAreaH, 0x22FFFFFF);
            int thumbH = Math.max(10, (int)(barAreaH * ((double)visible / total)));
            // offset=0 → thumb at bottom; offset=max → thumb at top
            int thumbY = barAreaY + (barAreaH - thumbH) - (int)((barAreaH - thumbH) * ((double)offset / Math.max(1, total - visible)));
            Gui.drawRect(barX, thumbY, barX + 2, thumbY + thumbH, color);
            dayScrollMin.put(winIdx, 0); dayScrollMax.put(winIdx, Math.max(0, total - visible));
            dayNavPrevY.put(winIdx, -1); dayNavNextY.put(winIdx, -1); dayNavBarX.put(winIdx, barX);
            return;
        }

        // ── Find which segment the current viewport bottom is in ─────────────
        // "offset" = how many lines from the bottom are scrolled off.
        // The bottom-most visible line index (in allLines, 0=oldest) = total - 1 - offset
        int bottomIdx = total - 1 - offset;
        if (bottomIdx < 0) bottomIdx = 0;

        int curSegIdx = segs.size() - 1;
        for (int s = 0; s < segs.size(); s++) {
            if (bottomIdx <= segs.get(s)[1]) { curSegIdx = s; break; }
        }
        int[] cur = segs.get(curSegIdx);
        int dayFirst = cur[0]; // index of separator (or first line) of this day
        int dayLast  = cur[1]; // index of last content line of this day
        int dayCount = cur[2]; // number of content lines in this day

        // ── Compute offset range for this day ────────────────────────────────
        // offset = total - 1 - bottomIdx
        // When at the very bottom of this day: bottomIdx = dayLast  → offset = total - 1 - dayLast
        // When at the very top    of this day: bottomIdx = dayFirst → offset = total - 1 - dayFirst
        //   (but clamped so we don't scroll off screen)
        int offsetAtDayBottom = Math.max(0, total - 1 - dayLast);
        int offsetAtDayTop    = Math.min(total - visible, total - 1 - dayFirst);
        if (offsetAtDayTop < offsetAtDayBottom) offsetAtDayTop = offsetAtDayBottom;

        // Store for drag/click handler
        dayScrollMin.put(winIdx, offsetAtDayBottom);
        dayScrollMax.put(winIdx, offsetAtDayTop);
        dayNavBarX.put(winIdx, barX);

        // ── Nav button layout ────────────────────────────────────────────────
        // Reserve 10px at top for "▲ older" button if there's a previous day,
        // and 10px at bottom for "▼ newer" button if there's a next day.
        boolean hasPrev = curSegIdx > 0;
        boolean hasNext = curSegIdx < segs.size() - 1;

        int trackTop    = barAreaY + (hasPrev ? 11 : 0);
        int trackBottom = barAreaY + barAreaH - (hasNext ? 11 : 0);
        int trackH      = trackBottom - trackTop;
        if (trackH <= 0) trackH = 1;

        // Draw track
        Gui.drawRect(barX, trackTop, barX + 2, trackBottom, 0x22FFFFFF);

        // Prev (older) button — above the track
        if (hasPrev) {
            int btnY = barAreaY;
            Minecraft mc = Minecraft.getMinecraft();
            mc.fontRendererObj.drawString("\u25B2", barX - 1, btnY, 0xAAFFFFFF);
            // Only show the previous day's date label when we are NOT on the current/today's day
            // (i.e. there is a newer day after us, meaning we are not already on today)
            boolean onToday = !hasNext; // last segment = newest = today
            if (!onToday) {
                int[] prevSeg = segs.get(curSegIdx - 1);
                String prevDate = allLines.get(prevSeg[0]).isSeparator ? allLines.get(prevSeg[0]).text : "";
                if (!prevDate.isEmpty()) {
                    String label = prevDate.length() > 5 ? prevDate.substring(5) : prevDate; // MM/DD
                    mc.fontRendererObj.drawString(label, barX - mc.fontRendererObj.getStringWidth(label) - 3, btnY, 0x77FFFFFF);
                }
            }
            dayNavPrevY.put(winIdx, btnY);
        } else {
            dayNavPrevY.put(winIdx, -1);
        }

        // Next (newer) button — below the track
        if (hasNext) {
            int btnY = trackBottom + 1;
            Minecraft mc = Minecraft.getMinecraft();
            mc.fontRendererObj.drawString("\u25BC", barX - 1, btnY, 0xAAFFFFFF);
            int[] nextSeg = segs.get(curSegIdx + 1);
            String nextDate = allLines.get(nextSeg[0]).isSeparator ? allLines.get(nextSeg[0]).text : "";
            if (!nextDate.isEmpty()) {
                String label = nextDate.length() > 5 ? nextDate.substring(5) : nextDate;
                mc.fontRendererObj.drawString(label, barX - mc.fontRendererObj.getStringWidth(label) - 3, btnY, 0x77FFFFFF);
            }
            dayNavNextY.put(winIdx, btnY);
        } else {
            dayNavNextY.put(winIdx, -1);
        }

        // ── Thumb: sized to this day only ────────────────────────────────────
        int scrollRangeInDay = Math.max(1, offsetAtDayTop - offsetAtDayBottom);
        // dayFraction: 0.0 = at bottom of day (newest, offset = offsetAtDayBottom)
        //              1.0 = at top of day (oldest, offset = offsetAtDayTop)
        double dayFraction = (offsetAtDayTop == offsetAtDayBottom) ? 0.0
                : (double)(offset - offsetAtDayBottom) / scrollRangeInDay;
        dayFraction = Math.max(0, Math.min(1, dayFraction));

        // Thumb height = trackH * visible / dayCount
        int thumbH = Math.max(8, (int)(trackH * ((double)visible / Math.max(1, dayCount))));
        thumbH = Math.min(thumbH, trackH);
        // thumbY: fraction=0 (bottom/newest) → thumb at BOTTOM of track
        //         fraction=1 (top/oldest)    → thumb at TOP of track
        int thumbY = trackTop + (int)((1.0 - dayFraction) * (trackH - thumbH));

        Gui.drawRect(barX, thumbY, barX + 2, thumbY + thumbH, color);
    }

    // -------------------------------------------------------------------------
    // Mouse click
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public void onMouseClick(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || !Mouse.getEventButtonState()) return;
        int mx = Mouse.getEventX() * event.gui.width / Minecraft.getMinecraft().displayWidth;
        int my = event.gui.height - Mouse.getEventY() * event.gui.height / Minecraft.getMinecraft().displayHeight - 1;
        int btn = Mouse.getEventButton();

        if (isSettingsOpen) { settings.mouseClicked(mx, my, btn); return; }

        for (int w = data.windows.size() - 1; w >= 0; w--) {
            ChatTabData.ChatWindowInstance win = data.windows.get(w);

            // Gear
            if (btn == 0 && mx >= win.x + win.width - 20 && mx <= win.x + win.width && my >= win.y && my <= win.y + 22) {
                isSettingsOpen = true; event.setCanceled(true); return;
            }
            // Resize
            if (!data.isLocked && btn == 0 && mx >= win.x + win.width - 10 && mx <= win.x + win.width && my >= win.y + win.height - 10 && my <= win.y + win.height) {
                resizingWindowIndex = w; event.setCanceled(true); return;
            }
            // Scroll bar + day nav buttons
            if (btn == 0) {
                int globalIdx = win.getSelectedGlobalIndex();
                if (globalIdx != -1) {
                    List<RenderableLine> lines = lineCache.get(globalIdx);
                    if (lines != null && lines.size() > (win.height - 30) / 10) {
                        int barX = dayNavBarX.getOrDefault(w, win.x + win.width - 4);
                        int barAreaY = win.y + 25, barAreaH = win.height - 35;
                        int prevBtnY = dayNavPrevY.getOrDefault(w, -1);
                        int nextBtnY = dayNavNextY.getOrDefault(w, -1);

                        // ▲ Prev (older) day button
                        if (prevBtnY != -1 && mx >= barX - 8 && mx <= barX + 8 && my >= prevBtnY && my <= prevBtnY + 9) {
                            // Jump to the bottom of the previous (older) day
                            int newOff = dayScrollMin.getOrDefault(w, 0);
                            // "bottom of previous day" = just above our current day's bottom
                            // = offsetAtDayBottom of cur day + 1 line (to land at last line of prev day)
                            // Actually: we want to navigate into the prev day, so set offset to
                            // one line above the current day's bottom limit.
                            newOff = newOff + 1;
                            newOff = Math.max(0, Math.min(Math.max(0, lines.size() - (win.height - 30) / 10), newOff));
                            data.scrollOffsets.put(globalIdx, newOff);
                            targetCacheScrollOffset.put(w, Integer.MIN_VALUE);
                            event.setCanceled(true); return;
                        }
                        // ▼ Next (newer) day button
                        if (nextBtnY != -1 && mx >= barX - 8 && mx <= barX + 8 && my >= nextBtnY && my <= nextBtnY + 9) {
                            // Jump to the top of the next (newer) day
                            int newOff = dayScrollMax.getOrDefault(w, 0);
                            newOff = newOff - 1;
                            newOff = Math.max(0, Math.min(Math.max(0, lines.size() - (win.height - 30) / 10), newOff));
                            data.scrollOffsets.put(globalIdx, newOff);
                            targetCacheScrollOffset.put(w, Integer.MIN_VALUE);
                            event.setCanceled(true); return;
                        }
                        // Track drag
                        if (mx >= barX - 2 && mx <= barX + 4 && my >= barAreaY && my <= barAreaY + barAreaH) {
                            isDraggingScrollBar = true; scrollBarDragWindowIndex = w;
                            scrollBarDragStartY = my; scrollBarDragStartOffset = data.scrollOffsets.getOrDefault(globalIdx, 0);
                            event.setCanceled(true); return;
                        }
                    }
                }
            }
            // Chat component click
            if (btn == 0 && my > win.y + 22 && my < win.y + win.height) {
                for (ClickTarget t : clickTargetCache.getOrDefault(w, Collections.emptyList())) {
                    if (mx >= t.x1 && mx <= t.x2 && my >= t.y1 && my <= t.y2) {
                        dispatchClickEvent(t.clickEvent); event.setCanceled(true); return;
                    }
                }
            }
            // Top bar — tabs + drag + [+]
            if (my >= win.y && my <= win.y + 22) {
                int cx = win.x + 5;
                int settingsX = win.x + win.width - 20;
                for (int li = 0; li < win.tabIndices.size(); li++) {
                    int globalIdx = win.tabIndices.get(li);
                    if (globalIdx >= data.tabs.size()) continue;
                    int tw = Minecraft.getMinecraft().fontRendererObj.getStringWidth(data.tabs.get(globalIdx)) + 18;
                    if (mx >= cx && mx <= cx + tw) {
                        handleTabClick(w, li, globalIdx, btn, mx, cx); // w not winIdx
                        event.setCanceled(true); return;
                    }
                    cx += tw + 4;
                }
                // [+] button
                if (btn == 0 && mx >= cx && mx <= cx + 20) {
                    data.addTab();
                    pendingDeleteGlobalIndex = -1;
                    editingTabGlobalIndex = -1;
                    // New tab added to window 0 by default — if this is another window, move it here
                    if (w != 0 && !data.windows.isEmpty()) {
                        int newIdx = data.tabs.size() - 1;
                        // find which window it ended up in (may not be index 0 if window 0 was removed)
                        int winOfNew = data.windowIndexForTab(newIdx);
                        if (winOfNew != w && winOfNew != -1) {
                            data.windows.get(winOfNew).tabIndices.remove((Integer)newIdx);
                            data.windows.get(w).tabIndices.add(newIdx);
                            data.save();
                        }
                    }
                    ScaledResolution sr2 = new ScaledResolution(Minecraft.getMinecraft());
                    clampWindowToScreen(win, sr2);
                    event.setCanceled(true); return;
                }
                // Drag window
                if (!data.isLocked && btn == 0) {
                    draggingWindowIndex = w; dragWinOffsetX = mx - win.x; dragWinOffsetY = my - win.y;
                    event.setCanceled(true); return;
                }
            }
        }
    }

    private void handleTabClick(int winIdx, int localIdx, int globalIdx, int btn, int mx, int tabX) {
        ChatTabData.ChatWindowInstance win = data.windows.get(winIdx);
        if (btn == 0) {
            if (globalIdx == lastClickedGlobalIndex && (System.currentTimeMillis() - lastClickTime) < 350) {
                // Double click = rename
                editingTabGlobalIndex = globalIdx;
                renameField = new GuiTextField(0, Minecraft.getMinecraft().fontRendererObj, 0, 0, 100, 12);
                renameField.setEnableBackgroundDrawing(false); renameField.setText(data.tabs.get(globalIdx)); renameField.setFocused(true);
            } else {
                // Single click = select + start drag
                win.selectedLocalTab = localIdx;
                data.tabNotifications.put(globalIdx, false);
                editingTabGlobalIndex = -1;
                // Begin tab drag
                isDraggingTab = true;
                draggingTabGlobalIndex = globalIdx;
                draggingTabSourceWindow = win;
                dragTabMouseOffsetX = mx - tabX;
                dragTabVisualX = mx; dragTabVisualY = win.y + 11;
                tabIsDetached = false;
                dropTargetWindowIndex = -1;
            }
        } else if (btn == 1) {
            if (pendingDeleteGlobalIndex == globalIdx) {
                pendingDeleteGlobalIndex = -1; editingTabGlobalIndex = -1;
                data.deleteTab(globalIdx);
                // After deletion the window list may have changed; reset stale drag/tab state
                isDraggingTab = false;
                draggingTabGlobalIndex = -1;
                draggingTabSourceWindow = null;
                tabIsDetached = false;
                dropTargetWindowIndex = -1;
            } else {
                pendingDeleteGlobalIndex = globalIdx;
            }
        }
        lastClickTime = System.currentTimeMillis(); lastClickedGlobalIndex = globalIdx;
    }

    // -------------------------------------------------------------------------
    // Keyboard
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public void onKeyTyped(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!Keyboard.getEventKeyState()) return;
        int k = Keyboard.getEventKey(); char c = Keyboard.getEventCharacter();
        if (isSettingsOpen) { if (k == Keyboard.KEY_ESCAPE) isSettingsOpen = false; else settings.keyTyped(c, k); event.setCanceled(true); return; }
        if (editingTabGlobalIndex != -1 && renameField != null && editingTabGlobalIndex < data.tabs.size()) {
            if (k == Keyboard.KEY_RETURN) { if (!renameField.getText().trim().isEmpty()) { data.tabs.set(editingTabGlobalIndex, renameField.getText().trim()); data.save(); } editingTabGlobalIndex = -1; }
            else if (k == Keyboard.KEY_ESCAPE) editingTabGlobalIndex = -1;
            else renameField.textboxKeyTyped(c, k);
            event.setCanceled(true); return;
        }
        if (k == Keyboard.KEY_RETURN && event.gui instanceof GuiChat) {
            GuiTextField inputField = getVanillaInputField((GuiChat) event.gui);
            String rawText = "";
            if (customChatField != null && !customChatField.getText().isEmpty()) rawText = customChatField.getText();
            else if (inputField != null) rawText = inputField.getText();

            if (!rawText.isEmpty()) {
                // Use selected tab from window 0 for prefix/suffix
                int globalIdx = data.windows.isEmpty() ? 0 : data.windows.get(0).getSelectedGlobalIndex();
                String prefix = data.tabPrefixes.getOrDefault(globalIdx, "");
                String suffix = data.tabSuffixes.getOrDefault(globalIdx, "");
                String finalText = prefix + rawText + suffix;
                if (finalText.startsWith("/")) onPlayerSentCommand();
                if (inputField != null) inputField.setText("");
                if (customChatField != null) customChatField.setText("");
                Minecraft.getMinecraft().thePlayer.sendChatMessage(finalText);
                lastPlayerSendTime = System.currentTimeMillis();
                data.lastMessageTime = System.currentTimeMillis();
                Minecraft.getMinecraft().displayGuiScreen(null);
                event.setCanceled(true); return;
            } else {
                Minecraft.getMinecraft().displayGuiScreen(null);
                event.setCanceled(true); return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Hover tooltip
    // -------------------------------------------------------------------------
    private void drawHoverTooltip(int mx, int my) {
        HoverEvent activeEvent = null;
        for (HoverTarget t : activeHoverTargets) {
            if (mx >= t.x1 && mx <= t.x2 && my >= t.y1 && my <= t.y2) { activeEvent = t.hoverEvent; break; }
        }
        if (activeEvent == null) { lastHoveredEvent = null; return; }
        Minecraft mc = Minecraft.getMinecraft();
        if (activeEvent != lastHoveredEvent) { lastHoveredEvent = activeEvent; cachedTooltipLines = buildTooltipLines(mc, activeEvent); }
        List<TooltipLine> lines = cachedTooltipLines;
        if (lines == null || lines.isEmpty()) return;
        final int PAD = 5, LINE_H = 10;
        int maxW = 0;
        for (TooltipLine tl : lines) { int w = mc.fontRendererObj.getStringWidth(tl.text); if (w > maxW) maxW = w; }
        int tipW = maxW + PAD * 2, tipH = lines.size() * LINE_H + PAD * 2 - 1;
        ScaledResolution sr = new ScaledResolution(mc);
        int tipX = mx + 8, tipY = my - tipH / 2;
        if (tipX + tipW > sr.getScaledWidth() - 2)  tipX = mx - tipW - 6;
        if (tipY + tipH > sr.getScaledHeight() - 2)  tipY = sr.getScaledHeight() - tipH - 2;
        if (tipY < 2) tipY = 2;
        Gui.drawRect(tipX - 1, tipY - 1, tipX + tipW + 1, tipY + tipH + 1, 0xCC111116);
        Gui.drawRect(tipX, tipY, tipX + tipW, tipY + tipH, 0xEE0D0D12);
        Gui.drawRect(tipX, tipY, tipX + 1, tipY + tipH, data.getHex(data.colorSelection, 200));
        int ty = tipY + PAD;
        for (TooltipLine tl : lines) { mc.fontRendererObj.drawStringWithShadow(tl.text, tipX + PAD + 1, ty, tl.color); ty += LINE_H; }
    }

    // -------------------------------------------------------------------------
    // Helpers (kept from original, adapted for window offsets)
    // -------------------------------------------------------------------------
    private void buildTargetsForLine(Minecraft mc, RenderableLine line, int lineY, int windowOffsetX,
                                     List<HoverTarget> outHover, List<ClickTarget> outClick) {
        IChatComponent root = line.sourceMsg.rawComponent;
        String lineUnformatted = EnumChatFormatting.getTextWithoutFormattingCodes(line.text);
        if (lineUnformatted == null || lineUnformatted.isEmpty()) return;
        int lineStartChar = line.lineCharOffset;
        int curChar = 0, curX = windowOffsetX + 5;
        for (IChatComponent comp : root) {
            String nodeText = comp.getUnformattedTextForChat();
            if (nodeText.isEmpty()) continue;
            int nodeLen = nodeText.length(), nodeEnd = curChar + nodeLen;
            int overlapStart = Math.max(curChar, lineStartChar), overlapEnd = Math.min(nodeEnd, lineStartChar + lineUnformatted.length());
            if (overlapStart < overlapEnd) {
                String beforeOnLine = nodeText.substring(Math.max(0, lineStartChar - curChar), overlapStart - curChar);
                String onLine       = nodeText.substring(overlapStart - curChar, overlapEnd - curChar);
                int preW = mc.fontRendererObj.getStringWidth(applyStyle(beforeOnLine, comp));
                int onW  = mc.fontRendererObj.getStringWidth(applyStyle(onLine, comp));
                HoverEvent he = comp.getChatStyle().getChatHoverEvent();
                if (he != null && onW > 0) { int tx = curX + preW; outHover.add(new HoverTarget(tx, lineY - 1, tx + onW, lineY + 9, he)); }
                ClickEvent ce = comp.getChatStyle().getChatClickEvent();
                if (ce != null && onW > 0) { int tx = curX + preW; outClick.add(new ClickTarget(tx, lineY - 1, tx + onW, lineY + 9, ce)); }
            }
            if (curChar >= lineStartChar) curX += mc.fontRendererObj.getStringWidth(applyStyle(nodeText, comp));
            else if (nodeEnd > lineStartChar) { String tail = nodeText.substring(lineStartChar - curChar); curX += mc.fontRendererObj.getStringWidth(applyStyle(tail, comp)); }
            curChar += nodeLen;
        }
    }

    private String applyStyle(String text, IChatComponent comp) {
        if (text.isEmpty()) return text;
        net.minecraft.util.ChatStyle style = comp.getChatStyle();
        StringBuilder prefix = new StringBuilder();
        if (style.getColor() != null)                prefix.append(style.getColor().toString());
        if (Boolean.TRUE.equals(style.getBold()))          prefix.append(EnumChatFormatting.BOLD);
        if (Boolean.TRUE.equals(style.getItalic()))        prefix.append(EnumChatFormatting.ITALIC);
        if (Boolean.TRUE.equals(style.getUnderlined()))    prefix.append(EnumChatFormatting.UNDERLINE);
        if (Boolean.TRUE.equals(style.getStrikethrough())) prefix.append(EnumChatFormatting.STRIKETHROUGH);
        if (Boolean.TRUE.equals(style.getObfuscated()))    prefix.append(EnumChatFormatting.OBFUSCATED);
        return prefix + text;
    }

    private void dispatchClickEvent(ClickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        ClickEvent.Action action = event.getAction(); String value = event.getValue();
        if (action == ClickEvent.Action.RUN_COMMAND) { if (value.startsWith("/")) onPlayerSentCommand(); mc.thePlayer.sendChatMessage(value); }
        else if (action == ClickEvent.Action.SUGGEST_COMMAND) {
            if (mc.currentScreen instanceof GuiChat) { GuiTextField f = getVanillaInputField((GuiChat) mc.currentScreen); if (f != null) { f.setText(value); f.setCursorPositionEnd(); } }
            if (customChatField != null) { customChatField.setText(value); customChatField.setCursorPositionEnd(); }
        } else if (action == ClickEvent.Action.OPEN_URL) { try { java.awt.Desktop.getDesktop().browse(new java.net.URI(value)); } catch (Exception ignored) {} }
        else if (action == ClickEvent.Action.OPEN_FILE) { try { java.awt.Desktop.getDesktop().open(new java.io.File(value)); } catch (Exception ignored) {} }
    }

    private GuiTextField getVanillaInputField(GuiChat gui) {
        try { for (String name : new String[]{"inputField","field_146415_a"}) { try { Field f = GuiChat.class.getDeclaredField(name); f.setAccessible(true); return (GuiTextField) f.get(gui); } catch (NoSuchFieldException ignored) {} } } catch (Exception e) {}
        return null;
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------
    private static class RenderableLine {
        String text, time, date; boolean isSeparator; ChatTabData.ChatMessage sourceMsg; int lineCharOffset;
        RenderableLine(String t, boolean s, String tm, String dt, ChatTabData.ChatMessage msg, int co) { text=t; isSeparator=s; time=tm; date=dt; sourceMsg=msg; lineCharOffset=co; }
    }
    private static class HoverTarget { int x1,y1,x2,y2; HoverEvent hoverEvent; HoverTarget(int x1,int y1,int x2,int y2,HoverEvent e){this.x1=x1;this.y1=y1;this.x2=x2;this.y2=y2;this.hoverEvent=e;} }
    private static class ClickTarget  { int x1,y1,x2,y2; ClickEvent clickEvent;  ClickTarget (int x1,int y1,int x2,int y2,ClickEvent  e){this.x1=x1;this.y1=y1;this.x2=x2;this.y2=y2;this.clickEvent =e;} }
    private static class TooltipLine  { String text; int color; TooltipLine(String t, int c){text=t;color=c;} }

    private List<TooltipLine> buildTooltipLines(Minecraft mc, HoverEvent event) {
        List<TooltipLine> lines = new ArrayList<>();
        HoverEvent.Action action = event.getAction(); IChatComponent value = event.getValue();
        if (value == null) return lines;
        if (action == HoverEvent.Action.SHOW_TEXT) {
            String full = value.getFormattedText();
            for (String part : full.split("\n")) if (!part.isEmpty()) lines.add(new TooltipLine(part, getColorFromFormatted(part)));
            if (lines.isEmpty() && !full.isEmpty()) lines.add(new TooltipLine(full, 0xFFFFFF));
        } else if (action == HoverEvent.Action.SHOW_ACHIEVEMENT) {
            String id = value.getUnformattedText(); net.minecraft.stats.Achievement ach = null;
            for (Object obj : net.minecraft.stats.AchievementList.achievementList) if (obj instanceof net.minecraft.stats.Achievement) { net.minecraft.stats.Achievement a = (net.minecraft.stats.Achievement)obj; if (a.statId.equals(id)||a.getStatName().getUnformattedText().equals(id)){ach=a;break;} }
            if (ach != null) { lines.add(new TooltipLine("\u2605 "+ach.getStatName().getFormattedText(),0xFFFF55)); String desc=ach.getDescription(); if(desc!=null&&!desc.isEmpty()) for(String w:mc.fontRendererObj.listFormattedStringToWidth(desc,160)) lines.add(new TooltipLine(w,0xAAAAAA)); }
            else lines.add(new TooltipLine("\u2605 "+value.getFormattedText(),0xFFFF55));
        } else if (action == HoverEvent.Action.SHOW_ITEM) {
            String raw = value.getUnformattedText(); lines.add(new TooltipLine("[Item]",0x55FF55));
            try { net.minecraft.item.ItemStack stack = net.minecraft.item.ItemStack.loadItemStackFromNBT(net.minecraft.nbt.JsonToNBT.getTagFromJson(raw)); if(stack!=null){lines.set(0,new TooltipLine(stack.getDisplayName(),0x55FF55));lines.add(new TooltipLine(EnumChatFormatting.DARK_GRAY+stack.getItem().getUnlocalizedName(),0x555555));} } catch(Exception ignored){lines.add(new TooltipLine(raw.length()>40?raw.substring(0,40)+"…":raw,0xAAAAAA));}
        } else if (action == HoverEvent.Action.SHOW_ENTITY) {
            String raw = value.getUnformattedText(); lines.add(new TooltipLine("[Entity]",0xFF5555)); lines.add(new TooltipLine(raw.length()>40?raw.substring(0,40)+"…":raw,0xAAAAAA));
        } else { String raw = value.getFormattedText(); lines.add(new TooltipLine(raw.length()>60?raw.substring(0,60)+"…":raw,0xFFFFFF)); }
        return lines;
    }

    private int getColorFromFormatted(String formatted) {
        int[] mcColors = {0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF};
        for (int i = 0; i < formatted.length()-1; i++) { if(formatted.charAt(i)=='\u00A7'){char c=Character.toLowerCase(formatted.charAt(i+1));if(c>='0'&&c<='9')return 0xFF000000|mcColors[c-'0'];if(c>='a'&&c<='f')return 0xFF000000|mcColors[10+(c-'a')];} }
        return 0xFFFFFF;
    }
}
