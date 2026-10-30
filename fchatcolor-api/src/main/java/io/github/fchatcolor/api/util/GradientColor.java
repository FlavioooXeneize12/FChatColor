package io.github.fchatcolor.api.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Custom per-letter gradients defined in {@code colors.yml} as a list:
 * <pre>
 * rainbow2:
 *   value: ["#FF4141", "#36EFAB", "#FF35F1", "#FDFF61"]
 * </pre>
 *
 * <p>Stored in the database as a single canonical string
 * ({@code gradient:#FF4141,#36EFAB,...}) so no schema change is needed
 * beyond widening the column. Letters cycle through the listed colors in
 * order, like {@link RainbowColor} but with a fixed palette.</p>
 *
 * <p>Java 8 compatible, no Adventure/Bukkit dependencies.</p>
 */
public final class GradientColor {

    /** Prefix of the canonical stored form. */
    public static final String PREFIX = "gradient:";

    private GradientColor() {
    }

    /** Is this stored/catalog value a custom gradient? (case-insensitive prefix) */
    public static boolean isGradient(String value) {
        if (value == null) return false;
        String s = value.trim();
        return s.length() > PREFIX.length() && s.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    /**
     * Parses the palette of a canonical gradient value.
     *
     * @return unmodifiable list of canonical entries ({@code #RRGGBB} uppercase
     *         or single legacy chars), empty if invalid
     */
    public static List<String> parseColors(String value) {
        if (!isGradient(value)) return Collections.emptyList();
        String body = value.trim().substring(PREFIX.length());
        String[] parts = body.split(",");
        List<String> out = new ArrayList<String>(parts.length);
        for (String p : parts) {
            String canon = canonicalEntry(p);
            if (canon == null) return Collections.emptyList();
            out.add(canon);
        }
        if (out.size() < 2) return Collections.emptyList();
        return Collections.unmodifiableList(out);
    }

    /** Encodes validated entries as the canonical stored form. Returns null if invalid. */
    public static String encode(List<String> entries) {
        if (entries == null || entries.size() < 2) return null;
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < entries.size(); i++) {
            String canon = canonicalEntry(entries.get(i));
            if (canon == null) return null;
            if (i > 0) sb.append(',');
            sb.append(canon);
        }
        return sb.toString();
    }

    /** Canonical form of a single palette entry: {@code #RRGGBB} or legacy char. Null if invalid. */
    public static String canonicalEntry(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        Integer rgb = HexColor.parseRgb(s);
        if (rgb != null) return HexColor.toHex(rgb);
        String clean = s;
        if (clean.startsWith("&") || clean.startsWith("\u00A7")) clean = clean.substring(1);
        if (HexColor.isLegacyCode(clean)) return HexColor.normalizeLegacyCode(clean);
        return null;
    }

    /** Does this gradient contain at least one hex entry? */
    public static boolean containsHex(List<String> colors) {
        if (colors == null) return false;
        for (String c : colors) {
            if (c != null && c.startsWith("#")) return true;
        }
        return false;
    }

    /** Section-code prefix ({@code §x§R...} or {@code §c}) for the palette entry at {@code index} (cycles). */
    public static String sectionAt(List<String> colors, int index) {
        String entry = colors.get(Math.floorMod(index, colors.size()));
        if (entry.startsWith("#")) {
            Integer rgb = HexColor.parseRgb(entry);
            if (rgb != null) return HexColor.toLegacySectionHex(rgb);
        }
        return "\u00A7" + entry.toLowerCase(java.util.Locale.ROOT);
    }

    /** Nearest-legacy section code for the palette entry at {@code index} (servers without hex, e.g. 1.8). */
    public static String nearestSectionAt(List<String> colors, int index) {
        String entry = colors.get(Math.floorMod(index, colors.size()));
        if (entry.startsWith("#")) {
            Integer rgb = HexColor.parseRgb(entry);
            if (rgb != null) return HexColor.toNearestLegacySection(rgb);
        }
        return "\u00A7" + entry.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Paints text letter by letter cycling the palette.
     * Existing {@code §} codes are preserved and do not advance the cycle.
     *
     * @param hex {@code true} for real hex codes, {@code false} for nearest-legacy approximation
     */
    public static String colorize(String text, List<String> colors, boolean hex) {
        if (text == null || text.isEmpty() || colors == null || colors.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length() * 4);
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                out.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            out.append(hex ? sectionAt(colors, visible) : nearestSectionAt(colors, visible));
            out.append(c);
            visible++;
        }
        return out.toString();
    }

    /** Like {@link #colorize} but appends modifiers codes after each color code. */
    public static String colorizeWithModifiers(String text, List<String> colors, boolean hex, String modifiers) {
        if (text == null || text.isEmpty() || colors == null || colors.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length() * 6);
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                out.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            out.append(hex ? sectionAt(colors, visible) : nearestSectionAt(colors, visible));
            if (modifiers != null && !modifiers.isEmpty()) out.append(modifiers);
            out.append(c);
            visible++;
        }
        return out.toString();
    }

    /** MiniMessage gradient tag for a palette, e.g. {@code <gradient:#FF4141:#36EFAB>}. */
    public static String miniTag(List<String> colors) {
        if (colors == null || colors.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<gradient:");
        for (int i = 0; i < colors.size(); i++) {
            if (i > 0) sb.append(':');
            sb.append(toMiniColor(colors.get(i)));
        }
        sb.append('>');
        return sb.toString();
    }

    /** MiniMessage gradient tag for a canonical stored gradient value. */
    public static String miniTag(String stored) {
        return miniTag(parseColors(stored));
    }

    /** Single palette entry as MiniMessage color ({@code #RRGGBB} or name). */
    public static String toMiniColor(String entry) {
        if (entry == null || entry.isEmpty()) return "white";
        if (entry.startsWith("#")) return entry;
        char c = Character.toLowerCase(entry.charAt(entry.length() - 1));
        switch (c) {
            case '0': return "black";
            case '1': return "dark_blue";
            case '2': return "dark_green";
            case '3': return "dark_aqua";
            case '4': return "dark_red";
            case '5': return "dark_purple";
            case '6': return "gold";
            case '7': return "gray";
            case '8': return "dark_gray";
            case '9': return "blue";
            case 'a': return "green";
            case 'b': return "aqua";
            case 'c': return "red";
            case 'd': return "light_purple";
            case 'e': return "yellow";
            default: return "white";
        }
    }

    /**
     * Resolves an entry display name: {@code <rainbow>} and {@code {value}}
     * are replaced with the palette gradient tag so the name itself renders
     * with the entry colors (menu, placeholders, messages).
     */
    public static String resolveDisplayName(String displayName, String canonicalValue) {
        if (displayName == null || displayName.isEmpty()) return displayName;
        if (!isGradient(canonicalValue)) return displayName;
        List<String> palette = parseColors(canonicalValue);
        if (palette.isEmpty()) return displayName;
        String tag = miniTag(palette);
        String out = displayName.replaceAll("(?i)<rainbow>", java.util.regex.Matcher.quoteReplacement(tag));
        out = out.replace("{value}", tag);
        return out;
    }
}
