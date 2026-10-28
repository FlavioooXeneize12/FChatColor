package io.github.fchatcolor.legacy;

import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorSelector;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.settings.ChatDefaults;
import io.github.fchatcolor.api.storage.ColorStorage;
import io.github.fchatcolor.api.util.HexColor;
import io.github.fchatcolor.api.util.RainbowColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Chat listener for legacy servers (1.8 en adelante sin la API de
 * eventos de Paper).
 *
 * <p>Event is async: hitting the database here doesn't block the
 * main thread (colors are cached anyway).</p>
 */
public final class LegacyChatListener implements Listener {

    /** Only the first two events are logged (diagnostics without spam). */
    private static final java.util.concurrent.atomic.AtomicBoolean LOG_START =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean LOG_END =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private final PluginContext context;

    public LegacyChatListener(PluginContext context) {
        this.context = context;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Diagnostics (once per log): proves the event arrived, which
        // permission the player has and which color is stored in the database
        if (LOG_START.compareAndSet(false, true)) {
            context.getLogger().info("Chat#1 START " + event.getClass().getName()
                    + " -> player=" + (player == null ? "?" : player.getName())
                    + ", usePermission=" + (player != null && player.hasPermission(ColorSelector.PERMISO_USO))
                    + ", dbColor=" + readColorSafely(player));
        }

        if (player != null && !player.hasPermission(ColorSelector.PERMISO_USO)) {
            return;
        }

        String raw = event.getMessage();
        if (raw == null || raw.isEmpty()) {
            return;
        }

        ColorMode mode = ColorSelector.resolve(player, context.getSettings(), context.getColorService());
        String message = raw;
        // Fast-path: skip translation when there is nothing to translate (large-server micro-opt)
        if (raw.indexOf('&') >= 0 || raw.indexOf('<') >= 0 || raw.indexOf('\u00A7') >= 0
                || raw.indexOf(ChatDefaults.COLOR_CHARACTER) >= 0) {
            message = context.getColorService().translate(raw, mode);
        }

        if (ChatDefaults.APPLY_PLAYER_COLOR && player != null) {
            message = applyPlayerColor(player, message, mode);
        }

        event.setMessage(message);

        if (LOG_END.compareAndSet(false, true)) {
            context.getLogger().info("Chat#1 END -> mode=" + mode
                    + ", finalMessage=[" + escapeCodes(message) + "]");
        }
    }

    /** Database value without letting a storage error hide the diagnostics. */
    private String readColorSafely(Player player) {
        if (player == null) {
            return "n/a";
        }
        try {
            String color = context.getStorage().getSelectedColor(player.getUniqueId());
            return color == null || color.isEmpty() ? "none" : color;
        } catch (Throwable t) {
            return "ERROR:" + t;
        }
    }

    /** § as \u00A7 so the log reads well in any console. */
    private static String escapeCodes(String text) {
        return text == null ? "null" : text.replace("\u00A7", "\\u00A7");
    }

    /**
     * Applies the player's chosen color (stored in DB) to the message.
     *
     * <p>For a normal color we prepend its code. For
     * {@code rainbow} el mensaje se pinta él mismo letra a letra con el
     * espectro (arcoíris dentro del texto).</p>
     */
    private String applyPlayerColor(Player player, String message, ColorMode mode) {
        try {
            ColorStorage storage = context.getStorage();
            String color = storage.getSelectedColor(player.getUniqueId());
            if (color == null || color.isEmpty()) {
                return message;
            }

            // Rainbow: el color va letra a letra dentro del mensaje
            if (RainbowColor.isRainbow(color)) {
                boolean hexAllowed = player.hasPermission(ColorSelector.PERMISO_HEX)
                        && context.getServerInfo().hasLegacyHex()
                        && context.getColorService().supports(ColorMode.HEX);
                return RainbowColor.colorize(message, hexAllowed);
            }

            String prefix = colorToSectionPrefix(player, color, mode);
            if (prefix == null || prefix.isEmpty()) {
                return message;
            }
            return prefix + message;
        } catch (Throwable t) {
            context.getLogger().warning("Could not read the color of player "
                    + player.getName() + ": " + t.getMessage());
            return message;
        }
    }

    /** Converts the stored DB color to § codes ready for chat. */
    private String colorToSectionPrefix(Player player, String color, ColorMode mode) {
        if (color.startsWith("#")) {
            Integer rgb = HexColor.parseRgb(color);
            if (rgb == null) {
                return null;
            }
            // Sin permiso hex (o sin soporte en el servidor): color legacy más cercano
            boolean hexAllowed = player.hasPermission(ColorSelector.PERMISO_HEX)
                    && context.getServerInfo().hasLegacyHex()
                    && context.getColorService().supports(ColorMode.HEX);
            return hexAllowed
                    ? HexColor.toLegacySectionHex(rgb)
                    : HexColor.toNearestLegacySection(rgb);
        }

        if (HexColor.isLegacyCode(color)) {
            return "\u00A7" + color.toLowerCase();
        }

        // In case DB still has & or tags
        return context.getColorService().translate(color, mode);
    }
}
