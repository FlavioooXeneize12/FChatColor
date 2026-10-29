package io.github.fchatcolor.modern;

import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorSelector;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.settings.ChatDefaults;
import io.github.fchatcolor.api.settings.PluginSettings;
import io.github.fchatcolor.api.storage.ColorStorage;
import io.github.fchatcolor.api.util.RainbowColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import java.util.List;

/**
 * Chat listener for Paper-based servers
 * ({@code AsyncChatEvent}): 1.20.5+, 1.21.x, 26.x, Folia y Canvas.
 */
public final class ModernChatListener implements Listener {

    /** Only the first two events are logged (diagnostics without spam). */
    private static final java.util.concurrent.atomic.AtomicBoolean LOG_START =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean LOG_END =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private final PluginContext context;
    /** Reserva por si el contexto trae un servicio de otra implementación. */
    private final ModernColorService fallbackService;

    public ModernChatListener(PluginContext context) {
        this.context = context;
        this.fallbackService = new ModernColorService(context);
    }

    /**
     * Servicio activo. Se consulta en cada evento (no se cachea) para que
     * un {@code /fchatcolor reload} surta efecto de inmediato.
     */
    private ModernColorService service() {
        ColorService current = context.getColorService();
        if (current instanceof ModernColorService) {
            return (ModernColorService) current;
        }
        return fallbackService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
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

        PluginSettings settings = context.getSettings();
        ModernColorService colorService = service();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());

        ColorMode mode = ColorSelector.resolve(player, settings, colorService);
        Component message;

        if (!needsTranslation(raw)) {
            // Nada que traducir: conservamos el componente original
            message = event.message();
        } else {
            message = colorService.translateComponent(raw, mode);
        }

        if (ChatDefaults.APPLY_PLAYER_COLOR && player != null) {
            message = applyPlayerColor(message, player, colorService);
        }

        event.message(message);

        if (LOG_END.compareAndSet(false, true)) {
            context.getLogger().info("Chat#1 END -> mode=" + mode
                    + ", finalColor=" + (message.color() == null ? "no-root-color" : message.color().asHexString())
                    + ", text=[" + PlainTextComponentSerializer.plainText().serialize(message) + "]");
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

    /** Does the text contain any color code or tag we can interpret? */
    private boolean needsTranslation(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        if (raw.indexOf('<') >= 0) {
            return true;
        }
        if (raw.indexOf('#') >= 0) {
            return true;
        }
        char prefix = ChatDefaults.COLOR_CHARACTER;
        if (raw.indexOf(prefix) >= 0) {
            return true;
        }
        return raw.indexOf('\u00A7') >= 0 || raw.indexOf('&') >= 0;
    }

    /**
     * Applies the player's chosen color (stored in DB) to the message.
     *
     * <p>Used {@code colorIfAbsent} not a prefix: in Adventure
     * colors are inherited parent -> child, so we color the root component
     * and only uncolored text gets painted (codes typed by
     * the player keep priority, as you'd expect).</p>
     *
     * <p>Para {@code rainbow} el mensaje se pinta él mismo letra a letra con
     * el espectro (arcoíris dentro del texto).</p>
     */
    private Component applyPlayerColor(Component message, Player player, ModernColorService colorService) {
        try {
            ColorStorage storage = context.getStorage();
            String color = storage.getSelectedColor(player.getUniqueId());
            if (color == null || color.isEmpty()) {
                return message;
            }

            // Sin permiso hex: degradamos al color legacy más cercano
            boolean allowHex = player.hasPermission(ColorSelector.PERMISO_HEX);

            // Rainbow: el color va letra a letra dentro del mensaje
            if (RainbowColor.isRainbow(color)) {
                return applyRainbow(message, allowHex);
            }

            TextColor textColor = colorService.resolveTextColor(color, allowHex);
            if (textColor == null) {
                return message;
            }
            return message.colorIfAbsent(textColor);
        } catch (Throwable t) {
            context.getLogger().warning("Could not read the color of player "
                    + player.getName() + ": " + t.getMessage());
            return message;
        }
    }

    /**
     * Paints the message letter by letter with the spectrum (arcoíris dentro del
     * texto). Each char keeps its node's style; those
     * that already have a color (player typed codes) are left alone.
     */
    private Component applyRainbow(Component message, boolean allowHex) {
        return Component.empty().children(rainbowChildren(message, allowHex, new int[]{0}));
    }

    /** Flattens the component tree, one spectrum shade per char. */
    private List<Component> rainbowChildren(Component component, boolean allowHex, int[] index) {
        List<Component> out = new java.util.ArrayList<Component>();
        if (component instanceof net.kyori.adventure.text.TextComponent) {
            net.kyori.adventure.text.TextComponent text =
                    (net.kyori.adventure.text.TextComponent) component;
            String content = text.content();
            if (!content.isEmpty()) {
                TextColor ownColor = text.color();
                for (int i = 0; i < content.length(); i++) {
                    TextColor tint = ownColor != null ? ownColor : rainbowColor(index[0]++, allowHex);
                    out.add(Component.text(String.valueOf(content.charAt(i)))
                            .style(text.style().color(tint)));
                }
            }
        } else if (!component.equals(Component.empty())) {
            // Nodo no textual (translatable, espacio...): se conserva
            out.add(component.children(java.util.Collections.<Component>emptyList()));
        }
        for (Component child : component.children()) {
            out.addAll(rainbowChildren(child, allowHex, index));
        }
        return out;
    }

    /** Spectrum shade for position {@code index} in the message. */
    private TextColor rainbowColor(int index, boolean allowHex) {
        return TextColor.color(allowHex
                ? RainbowColor.rgbAt(index)
                : RainbowColor.legacyRgbAt(index));
    }
}
