package com.betterchat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.event.ClickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles everything related to player input while the chat GUI is open.
 *
 * - Sending messages, applying the active tab's prefix/suffix before sending.
 * - Dispatching chat component click-events (run command, suggest command, open URL/file).
 * - Tracking when the player last sent a command so that server replies can be
 *   tagged as "command responses" and routed to the right tab.
 * - Reflecting into GuiChat to grab the vanilla input field, which lets the mod
 *   replace it with its own styled text box.
 */
public class ChatInputHandler {

    private final ChatTabData data;

    // How long after a "/" command to treat incoming messages as command responses.
    private long lastPlayerCommandTime = 0;
    public static final long COMMAND_RESPONSE_WINDOW_MS = 3000;

    /**
     * True after the player sends a "/" command, false once they send normal chat.
     * Used to route server replies to the "Command Responses" filter reliably,
     * without depending on a narrow time window that network lag can break.
     */
    private boolean commandResponseMode = false;

    // Used to avoid counting the player's own message as a new "last message" (HUD fade debounce).
    private long lastPlayerSendTime = 0;
    public static final long SEND_ECHO_DEBOUNCE_MS = 1000;

    /** The global tab index the player was on when they last sent a message. -1 if unknown. */
    private int lastSentFromTabIndex = -1;

    // ── Sent-message history ──────────────────────────────────────────────────
    /** Messages sent this session, oldest-first.  Max 100 entries. */
    private final List<String> sentHistory = new ArrayList<>();
    /**
     * Current position while browsing history.
     * -1 = not browsing (live input).
     * 0  = most-recent sent message.
     * sentHistory.size()-1 = oldest sent message.
     */
    private int historyIndex = -1;
    /** Preserved draft text so we can restore it when the user navigates back down to -1. */
    private String draftText = "";

    public ChatInputHandler(ChatTabData data) {
        this.data = data;
    }

    // -------------------------------------------------------------------------
    // Timing
    // -------------------------------------------------------------------------

    /** Call this whenever the player sends a command so responses can be tagged. */
    public void onPlayerSentCommand() {
        lastPlayerCommandTime = System.currentTimeMillis();
        commandResponseMode = true;
    }

    /** Call this when the player sends normal (non-command) chat to end command-response mode. */
    public void onPlayerSentChat() {
        commandResponseMode = false;
    }

    public long getLastPlayerCommandTime()  { return lastPlayerCommandTime; }
    public long getLastPlayerSendTime()     { return lastPlayerSendTime; }
    public int  getLastSentFromTabIndex()   { return lastSentFromTabIndex; }

    /** Returns true if the next server message should be treated as a command response. */
    public boolean isWithinCommandResponseWindow() {
        return commandResponseMode
                || (System.currentTimeMillis() - lastPlayerCommandTime) < COMMAND_RESPONSE_WINDOW_MS;
    }

    // -------------------------------------------------------------------------
    // Sending messages
    // -------------------------------------------------------------------------

    /**
     * Reads whatever the player typed (from the custom field or vanilla field),
     * prepends/appends the active tab's prefix/suffix, and sends it.
     * Always returns true so the caller can close the chat GUI afterwards.
     */
    public boolean trySendMessage(GuiChat guiChat, GuiTextField customChatField) {
        GuiTextField inputField = getVanillaInputField(guiChat);
        String rawText = "";

        if (customChatField != null && !customChatField.getText().isEmpty()) {
            rawText = customChatField.getText();
        } else if (inputField != null) {
            rawText = inputField.getText();
        }

        if (rawText.isEmpty()) return true; // close GUI, nothing to send

        // Save to our own history (newest at the end; navigation goes from end → start)
        if (!rawText.trim().isEmpty()) {
            // Don't duplicate the most-recent entry
            if (sentHistory.isEmpty() || !sentHistory.get(sentHistory.size() - 1).equals(rawText)) {
                sentHistory.add(rawText);
                if (sentHistory.size() > 100) sentHistory.remove(0);
            }
        }
        // Reset history cursor
        historyIndex = -1;
        draftText    = "";

        int globalIdx = data.windows.isEmpty() ? 0 : data.windows.get(0).getSelectedGlobalIndex();
        String prefix    = data.tabPrefixes.getOrDefault(globalIdx, "");
        String suffix    = data.tabSuffixes.getOrDefault(globalIdx, "");
        String finalText = prefix + rawText + suffix;

        if (finalText.startsWith("/")) onPlayerSentCommand();
        else                           onPlayerSentChat();
        if (inputField != null)    inputField.setText("");
        if (customChatField != null) customChatField.setText("");

        // Inject player-sent commands directly into the global log.
        // Commands are never echoed back by the server, so onChatReceived never fires for them.
        // Without this they would never appear in any tab even with the "Commands" filter on.
        if (finalText.startsWith("/")) {
            ChatTabData.ChatMessage cmdMsg = new ChatTabData.ChatMessage(
                    finalText, false, null,
                    false, false, true, false, finalText);
            data.globalLog.add(cmdMsg);
        }

        Minecraft.getMinecraft().thePlayer.sendChatMessage(finalText);
        lastPlayerSendTime = System.currentTimeMillis();
        lastSentFromTabIndex = globalIdx;
        data.lastMessageTime = System.currentTimeMillis();
        return true;
    }

    // -------------------------------------------------------------------------
    // History navigation  (called from ChatTabHandler.onKeyTyped)
    // -------------------------------------------------------------------------

    /**
     * Navigate sent-message history via arrow keys.
     *
     * @param up   true = KEY_UP (go to older message), false = KEY_DOWN (go to newer)
     * @param field the custom chat text field to update
     */
    public void navigateHistory(boolean up, GuiTextField field) {
        if (sentHistory.isEmpty() || field == null) return;

        if (up) {
            // Moving into history for the first time — save the current draft
            if (historyIndex == -1) draftText = field.getText();
            if (historyIndex < sentHistory.size() - 1) historyIndex++;
        } else {
            if (historyIndex == -1) return; // already at live input, nothing to do
            historyIndex--;
        }

        if (historyIndex == -1) {
            // Returned past the most-recent entry → restore draft
            field.setText(draftText);
        } else {
            // Index 0 = most-recent, so invert to index from the end
            int realIdx = sentHistory.size() - 1 - historyIndex;
            field.setText(sentHistory.get(realIdx));
        }
        field.setCursorPositionEnd();
    }

    /** Call when the chat GUI is opened so fresh navigation always starts at -1. */
    public void resetHistoryCursor() {
        historyIndex = -1;
        draftText    = "";
    }

    // -------------------------------------------------------------------------
    // Click-event dispatch
    // -------------------------------------------------------------------------

    /** Executes the action carried by a chat component click-event. */
    public void dispatchClickEvent(ClickEvent event, GuiTextField customChatField) {
        Minecraft mc = Minecraft.getMinecraft();
        ClickEvent.Action action = event.getAction();
        String value = event.getValue();

        switch (action) {
            case RUN_COMMAND:
                if (value.startsWith("/")) onPlayerSentCommand();
                else                       onPlayerSentChat();
                mc.thePlayer.sendChatMessage(value);
                break;
            case SUGGEST_COMMAND:
                if (mc.currentScreen instanceof GuiChat) {
                    GuiTextField f = getVanillaInputField((GuiChat) mc.currentScreen);
                    if (f != null) { f.setText(value); f.setCursorPositionEnd(); }
                }
                if (customChatField != null) {
                    customChatField.setText(value);
                    customChatField.setCursorPositionEnd();
                }
                break;
            case OPEN_URL:
                try { java.awt.Desktop.getDesktop().browse(new java.net.URI(value)); }
                catch (Exception ignored) {}
                break;
            case OPEN_FILE:
                try { java.awt.Desktop.getDesktop().open(new java.io.File(value)); }
                catch (Exception ignored) {}
                break;
            default:
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Vanilla input field access
    // -------------------------------------------------------------------------

    /**
     * Reflects into GuiChat to find its private input field.
     * Tries both the de-obfuscated name and the SRG name as a fallback.
     */
    public GuiTextField getVanillaInputField(GuiChat gui) {
        try {
            for (String name : new String[]{"inputField", "field_146415_a"}) {
                try {
                    Field f = GuiChat.class.getDeclaredField(name);
                    f.setAccessible(true);
                    return (GuiTextField) f.get(gui);
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }
}
