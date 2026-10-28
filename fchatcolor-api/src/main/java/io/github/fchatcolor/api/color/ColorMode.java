package io.github.fchatcolor.api.color;

import java.util.Locale;

/**
 * Color translation modes supported by the plugin.
 */
public enum ColorMode {

    /** Classic codes &a, &l etc. (every server).. */
    LEGACY,

    /** Hex colors &#RRGGBB (1.16+ / fallback to closest legacy).. */
    HEX,

    /** Etiquetas MiniMessage {@code <red>}, {@code <#FF5555>}, {@code <gradient:...>} (Paper 1.19.4+). */
    MINIMESSAGE;

    /**
     * Parses a config string into a mode.
     *
     * @param texto      valor leído de la config ("legacy", "hex", "minimessage", "mini")
     * @param predeterminado modo a usar si el valor no reconoce
     */
    public static ColorMode parse(String texto, ColorMode predeterminado) {
        if (texto == null) {
            return predeterminado;
        }
        String clean = texto.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (clean.equals("legacy") || clean.equals("classic")) {
            return LEGACY;
        }
        if (clean.equals("hex") || clean.equals("rgb")) {
            return HEX;
        }
        if (clean.equals("minimessage") || clean.equals("mini")) {
            return MINIMESSAGE;
        }
        return predeterminado;
    }
}
