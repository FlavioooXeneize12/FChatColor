package io.github.fchatcolor.api.settings;

import io.github.fchatcolor.api.color.ColorMode;

/**
 * Comportamiento fijo del chat de FChatColor.
 *
 * <p>Estos valores NO son configurables a propósito: el plugin siempre
 * traduce los códigos de color que escribe el jugador y siempre aplica el
 * color elegido (guardado en la base de datos) a sus mensajes. Así el
 * comportamiento del chat es idéntico en todos los servidores y no puede
 * romperse tocando la configuración.</p>
 */
public final class ChatDefaults {

    /** Carácter que precede a los códigos de color en el chat ({@code &a}, {@code &#RRGGBB}). */
    public static final char COLOR_CHARACTER = '&';

    /** Traducir los códigos de color que escribe el jugador en el chat. */
    public static final boolean TRANSLATE_COLOR_CODES = true;

    /** Aplicar el color elegido por el jugador (BD) a sus mensajes de chat. */
    public static final boolean APPLY_PLAYER_COLOR = true;

    /** Modo de color usado when el jugador no has permisos especiales. */
    public static final ColorMode DEFAULT_MODE = ColorMode.LEGACY;

    private ChatDefaults() {
    }
}
