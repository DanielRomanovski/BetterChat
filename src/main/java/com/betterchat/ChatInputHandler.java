package com.betterchat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.event.ClickEvent;

import java.lang.reflect.Field;

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

    // Used to avoid counting the player's own message as a new "last message" (HUD fade debounce).
    private long lastPlayerSendTime = 0;
    public static final long SEND_ECHO_DEBOUNCE_MS = 1000;

    public ChatInputHandler(ChatTabData data) {
        this.data = data;
    }

    // -------------------------------------------------------------------------
    // Timing
    // -------------------------------------------------------------------------

    /** Call this whenever the player sends a command so responses can be tagged. */
    public void onPlayerSentCommand() {
        lastPlayerCommandTime = System.currentTimeMillis();
    }

    public long getLastPlayerCommandTime()  { return lastPlayerCommandTime; }
    public long getLastPlayerSendTime()     { return lastPlayerSendTime; }

    /** Returns true if we are still inside the command-response attribution window. */
    public boolean isWithinCommandResponseWindow() {
        return (System.currentTimeMillis() - lastPlayerCommandTime) < COMMAND_RESPONSE_WINDOW_MS;
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

        int globalIdx = data.windows.isEmpty() ? 0 : data.windows.get(0).getSelectedGlobalIndex();
        String prefix    = data.tabPrefixes.getOrDefault(globalIdx, "");
        String suffix    = data.tabSuffixes.getOrDefault(globalIdx, "");
        String finalText = prefix + rawText + suffix;

        if (finalText.startsWith("/")) onPlayerSentCommand();
        if (inputField != null)    inputField.setText("");
        if (customChatField != null) customChatField.setText("");

        Minecraft.getMinecraft().thePlayer.sendChatMessage(finalText);
        lastPlayerSendTime = System.currentTimeMillis();
        data.lastMessageTime = System.currentTimeMillis();
        return true;
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
