package io.github.fchatcolor.api.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidades para trabajar con colores hexadecimales y con su aproximacion
 * al formato legacy de Minecraft (&amp;0-&amp;f / &sect;0-&sect;f).
 *
 * <p>Esta clase solo usa tipos primitivos y {@code String}, por lo que es
 * utilizable desde cualquier version de servidor (1.8 incluida).</p>
 */
public final class HexColor {

    /** Códigos legacy válidos (0-9, a-f). */
    private static final char[] LEGACY_CODES = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /** RGB oficial de cada color legacy de Minecraft (orden coincidente con LEGACY_CODES). */
    private static final int[] LEGACY_RGB = {
            0x000000, // 0 black
            0x0000AA, // 1 dark_blue
            0x00AA00, // 2 dark_green
            0x00AAAA, // 3 dark_aqua
            0xAA0000, // 4 dark_red
            0xAA00AA, // 5 dark_purple
            0xFFAA00, // 6 gold
            0xAAAAAA, // 7 gray
            0x555555, // 8 dark_gray
            0x5555FF, // 9 blue
            0x55FF55, // a green
            0x55FFFF, // b aqua
            0xFF5555, // c red
            0xFF55FF, // d light_purple
            0xFFFF55, // e yellow
            0xFFFFFF  // f white
    };

    private HexColor() {
    }

    /**
     * Convierte una cadena hexadecimal en RGB.
     *
     * <p>Acepta los formatos {@code #RRGGBB}, {@code RRGGBB}, {@code 0xRRGGBB}
     * y {@code &#RRGGBB} (sin distinguir mayúsculas de minúsculas).</p>
     *
     * @param input cadena a interpretar
     * @return el valor RGB como entero (0xRRGGBB) o {@code null} si no es válido
     */
    public static Integer parseRgb(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("&#")) {
            s = s.substring(2);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Devuelve el color as normalizado {@code #RRGGBB} (mayúsculas). */
    public static String toHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /**
     * Genera la secuencia legacy que usa Minecraft desde la 1.16 para mostrar
     * colores hex en el chat:
     * {@code §x§R§R§G§G§B§B}.
     */
    public static String toLegacySectionHex(int rgb) {
        String hex = String.format("%06x", rgb & 0xFFFFFF);
        StringBuilder sb = new StringBuilder(14);
        sb.append('\u00A7').append('x');
        for (int i = 0; i < hex.length(); i++) {
            sb.append('\u00A7').append(hex.charAt(i));
        }
        return sb.toString();
    }

    /** Idéntico a {@link #toLegacySectionHex(int)} pero usando {@code &x&...} como separador. */
    public static String toLegacyAmpersandHex(int rgb) {
        String hex = String.format("%06x", rgb & 0xFFFFFF);
        StringBuilder sb = new StringBuilder(14);
        sb.append("&x");
        for (int i = 0; i < hex.length(); i++) {
            sb.append('&').append(hex.charAt(i));
        }
        return sb.toString();
    }

    /** Devuelve el código legacy (0-9a-f) más cercano al RGB dado, por distancia euclidiana. */
    public static char nearestLegacyCode(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        char best = 'f';
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int ref = LEGACY_RGB[i];
            long dr = r - ((ref >> 16) & 0xFF);
            long dg = g - ((ref >> 8) & 0xFF);
            long db = b - (ref & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = LEGACY_CODES[i];
            }
        }
        return best;
    }

    /** Versión "section sign" del color legacy más cercano: {@code §c}. */
    public static String toNearestLegacySection(int rgb) {
        return "\u00A7" + nearestLegacyCode(rgb);
    }

    /** Versión "ampersand" del color legacy más cercano: {@code &c}. */
    public static String toNearestLegacyAmpersand(int rgb) {
        return "&" + nearestLegacyCode(rgb);
    }

    /** Comprueba si la cadena es un código legacy de un solo carácter (0-9a-f). */
    public static boolean isLegacyCode(String input) {
        if (input == null || input.length() != 1) {
            return false;
        }
        return isLegacyCode(input.charAt(0));
    }

    /** Comprueba si el carácter es un código legacy de color (0-9a-f), sin importar mayúsculas. */
    public static boolean isLegacyCode(char code) {
        char lower = Character.toLowerCase(code);
        for (char c : LEGACY_CODES) {
            if (c == lower) {
                return true;
            }
        }
        return false;
    }

    /** Normaliza un código legacy a minúsculas. */
    public static String normalizeLegacyCode(String code) {
        return code == null ? null : code.trim().toLowerCase(Locale.ROOT);
    }

    /** Bare {@code #RRGGBB} pattern (must not run inside {@code <...>} tags). */
    private static final Pattern BARE_HEX_PATTERN =
            Pattern.compile("(?i)(?<![&<])#([0-9A-Fa-f]{6})(?![0-9A-Fa-f])");

    /** MiniMessage tag spans: {@code #RRGGBB} inside them (e.g. {@code <gradient:#FF4141:...>}) must be kept intact. */
    private static final Pattern TAG_SPAN_PATTERN = Pattern.compile("<[^<>]*>");

    /**
     * Converts bare {@code #RRGGBB} to {@code &#RRGGBB}, skipping anything
     * inside {@code <...>} tags so MiniMessage tags like
     * {@code <gradient:#FF4141:#36EFAB>} survive preprocessing.
     */
    public static String replaceBareHexOutsideTags(String input) {
        if (input == null || input.indexOf('#') < 0) {
            return input;
        }
        Matcher tags = TAG_SPAN_PATTERN.matcher(input);
        if (!tags.find()) {
            return BARE_HEX_PATTERN.matcher(input).replaceAll("&#$1");
        }
        StringBuffer out = new StringBuffer(input.length() + 8);
        int last = 0;
        tags.reset();
        while (tags.find()) {
            out.append(BARE_HEX_PATTERN.matcher(input.substring(last, tags.start())).replaceAll("&#$1"));
            out.append(tags.group());
            last = tags.end();
        }
        out.append(BARE_HEX_PATTERN.matcher(input.substring(last)).replaceAll("&#$1"));
        return out.toString();
    }
}
