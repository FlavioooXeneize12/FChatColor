package io.github.fchatcolor.api.settings;

import io.github.fchatcolor.api.color.ColorMode;

/**
 * Ajustes del plugin expuestos a las implementaciones (legacy/modern)
 * sin que dependan de la clase principal ni de {@code FileConfiguration}.
 *
 * <p>El comportamiento fijo del chat (char {@code &}, traducción de
 * códigos, aplicar el color del jugador y modo por defecto) vive en
 * {@link ChatDefaults} y no se configura.</p>
 */
public interface PluginSettings {

    /** ¿Está habilitado el modo de color indicado en la config? */
    boolean isModeEnabled(ColorMode mode);

    /** Cadena en milisegundos que un color guardado vive en caché antes de re-consultar la BD. */
    long getCacheMillis();

    /** Whether the /chatcolor GUI menu is enabled. */
    boolean isMenuEnabled();

    /** Default chat color for players without a selection or with revoked permission (e.g. "white", "#FFFFFF", "a", "rainbow", or "" for none). */
    String getDefaultColor();
}
