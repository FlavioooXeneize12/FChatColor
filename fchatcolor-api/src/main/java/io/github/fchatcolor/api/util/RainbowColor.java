package io.github.fchatcolor.api.util;

/**
 * Special stored value that makes the player's chat color cycle through the
 * spectrum (the "rainbow" color).
 *
 * <p>The rainbow paints the message itself letter by letter, not the whole
 * message in a single tone: every visible character gets the next hue of the
 * spectrum (red - orange - yellow - green - blue - ...) and the base hue also
 * rotates with time, so the rainbow both runs across each message and keeps
 * moving. Servers without hex support (and players without
 * {@code fchatcolor.chat.hex}) get the same effect approximated with the
 * legacy spectrum colors.</p>
 *
 * <p>The value can be used in {@code colors.yml}:</p>
 *
 * <pre>
 * rainbow:
 *   name: '&lt;rainbow&gt;Rainbow'
 *   permission: fchatcolor.color.rainbow
 *   value: rainbow
 * </pre>
 *
 * <p>Deliberately avoids {@code java.awt.Color} so the class also works on
 * minimal JREs without the {@code java.desktop} module.</p>
 */
public final class RainbowColor {

    /** Canonical value stored in the database and in {@code colors.yml}. */
    public static final String VALUE = "rainbow";

    /** Milliseconds for a full trip around the spectrum. */
    private static final long CYCLE_MILLIS = 20000L;

    /** Degrees of hue advanced per visible character. */
    private static final int HUE_STEP_PER_CHAR = 12;

    /** Legacy spectrum (used when hex is not available): red to magenta. */
    private static final char[] LEGACY_SPECTRUM = {'c', '6', 'e', 'a', 'b', 'd'};

    private RainbowColor() {
    }

    /** Is this stored/catalog value the rainbow color? (case-insensitive) */
    public static boolean isRainbow(String value) {
        return value != null && VALUE.equalsIgnoreCase(value.trim());
    }

    /** Current hue of the cycle in degrees, {@code [0, 360)}. */
    public static int currentHue() {
        long now = System.currentTimeMillis();
        return (int) ((now % CYCLE_MILLIS) * 360L / CYCLE_MILLIS);
    }

    /** Current color of the cycle as RGB ({@code 0xRRGGBB}). */
    public static int currentRgb() {
        return hsvToRgb(currentHue(), 1.0f, 1.0f);
    }

    /** Current color of the cycle in canonical form ({@code #RRGGBB}). */
    public static String currentHex() {
        return HexColor.toHex(currentRgb());
    }

    /**
     * Hue for the character at {@code index} of a message: the base hue of the
     * cycle plus one step per character, so the spectrum runs across the text.
     *
     * @return hue in degrees, {@code [0, 360)}
     */
    public static int hueAt(int index) {
        return (currentHue() + index * HUE_STEP_PER_CHAR) % 360;
    }

    /** RGB ({@code 0xRRGGBB}) of the character at {@code index}. */
    public static int rgbAt(int index) {
        return hsvToRgb(hueAt(index), 1.0f, 1.0f);
    }

    /** Canonical {@code #RRGGBB} of the character at {@code index}. */
    public static String hexAt(int index) {
        return HexColor.toHex(rgbAt(index));
    }

    /**
     * Legacy code of the spectrum for the character at {@code index}
     * (fallback used on servers/players without hex).
     */
    public static char legacyAt(int index) {
        return LEGACY_SPECTRUM[Math.floorMod(index, LEGACY_SPECTRUM.length)];
    }

    /**
     * RGB of the legacy spectrum code for the character at {@code index}
     * (same values as the {@code §c §6 §e §a §b §d} codes).
     */
    public static int legacyRgbAt(int index) {
        switch (Math.floorMod(index, LEGACY_SPECTRUM.length)) {
            case 0:
                return 0xFF5555; // §c red
            case 1:
                return 0xFFAA00; // §6 gold
            case 2:
                return 0xFFFF55; // §e yellow
            case 3:
                return 0x55FF55; // §a green
            case 4:
                return 0x55FFFF; // §b aqua
            default:
                return 0xFF55FF; // §d light purple
        }
    }

    /** Section hex codes ({@code §x§r§r§g§g§b§b}) for the character at {@code index}. */
    public static String sectionHexAt(int index) {
        return HexColor.toLegacySectionHex(rgbAt(index));
    }

    /**
     * Paints an already translated message letter by letter (the rainbow
     * itself), inserting color codes before every visible character.
     * Existing {@code §} codes in the text are preserved and do not count as
     * visible characters.
     *
     * @param text message already translated to section codes
     * @param hex  {@code true} for real hex per character,
     *             {@code false} for the legacy spectrum approximation
     * @return the text with one color code per visible character
     */
    public static String colorize(String text, boolean hex) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() * 3);
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                // Existing code: kept as is, does not advance the spectrum
                out.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            if (hex) {
                out.append(sectionHexAt(visible));
            } else {
                out.append('\u00A7').append(legacyAt(visible));
            }
            out.append(c);
            visible++;
        }
        return out.toString();
    }

    /**
     * Converts HSV to RGB without {@code java.awt}:
     * {@code hue} in {@code [0, 360)}, saturation and value in {@code [0, 1]}.
     *
     * @return RGB as {@code 0xRRGGBB}
     */
    static int hsvToRgb(float hue, float saturation, float value) {
        float sector = hue / 60.0f;
        float chroma = value * saturation;
        float x = chroma * (1.0f - Math.abs((sector % 2.0f) - 1.0f));
        float m = value - chroma;

        float red;
        float green;
        float blue;
        switch ((int) sector % 6) {
            case 0:
                red = chroma;
                green = x;
                blue = 0.0f;
                break;
            case 1:
                red = x;
                green = chroma;
                blue = 0.0f;
                break;
            case 2:
                red = 0.0f;
                green = chroma;
                blue = x;
                break;
            case 3:
                red = 0.0f;
                green = x;
                blue = chroma;
                break;
            case 4:
                red = x;
                green = 0.0f;
                blue = chroma;
                break;
            default:
                red = chroma;
                green = 0.0f;
                blue = x;
                break;
        }

        int r = Math.round((red + m) * 255.0f);
        int g = Math.round((green + m) * 255.0f);
        int b = Math.round((blue + m) * 255.0f);
        return (r << 16) | (g << 8) | b;
    }
}
