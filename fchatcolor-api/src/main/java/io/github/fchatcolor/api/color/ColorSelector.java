package io.github.fchatcolor.api.color;

import io.github.fchatcolor.api.settings.ChatDefaults;
import io.github.fchatcolor.api.settings.PluginSettings;
import org.bukkit.entity.Player;

/**
 * Resuelve qué {@link ColorMode} usar para un jugador concreto,
 * combinando permisos, configuración y capacidades del servidor.
 */
public final class ColorSelector {

    /** Permiso para usar MiniMessage en el chat. */
    public static final String PERMISO_MINIMESSAGE = "fchatcolor.chat.minimessage";
    /** Permission to use hex colors in chat. */
    public static final String PERMISO_HEX = "fchatcolor.chat.hex";
    /** Base permission to use color translation. */
    public static final String PERMISO_USO = "fchatcolor.chat.use";

    private ColorSelector() {
    }

    /**
     * Chooses the highest mode the player is allowed to use.
     *
     * <p>Orden de preferencia: MiniMessage → Hex → modo predeterminado → legacy.</p>
     *
     * @param player    jugador (puede ser {@code null} en contexto de consola)
     * @param settings  ajustes del plugin
     * @param service   servicio de color activo (para consultar capacidades)
     */
    public static ColorMode resolve(Player player, PluginSettings settings, ColorService service) {
        if (service == null || settings == null) {
            return ColorMode.LEGACY;
        }

        if (settings.isModeEnabled(ColorMode.MINIMESSAGE)
                && hasPermission(player, PERMISO_MINIMESSAGE)
                && service.supports(ColorMode.MINIMESSAGE)) {
            return ColorMode.MINIMESSAGE;
        }

        if (settings.isModeEnabled(ColorMode.HEX)
                && hasPermission(player, PERMISO_HEX)
                && service.supports(ColorMode.HEX)) {
            return ColorMode.HEX;
        }

        // Default mode hard-coded in code (not configurable)
        return ChatDefaults.DEFAULT_MODE;
    }

    /** {@code hasPermission} seguro también para {@code null}. */
    private static boolean hasPermission(Player player, String permission) {
        if (player == null) {
            return false;
        }
        try {
            return player.hasPermission(permission);
        } catch (Throwable t) {
            return false;
        }
    }
}
