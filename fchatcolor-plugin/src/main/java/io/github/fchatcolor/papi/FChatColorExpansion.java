package io.github.fchatcolor.papi;

import io.github.fchatcolor.FChatColorPlugin;
import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.util.HexColor;
import io.github.fchatcolor.api.util.RainbowColor;
import io.github.fchatcolor.colors.ColorCatalog;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class FChatColorExpansion extends PlaceholderExpansion {
    private final FChatColorPlugin plugin;

    public FChatColorExpansion(FChatColorPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean register(FChatColorPlugin plugin) {
        try {
            return new FChatColorExpansion(plugin).register();
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not register the PlaceholderAPI expansion: " + t);
            return false;
        }
    }

    public String getIdentifier() { return "fchatcolor"; }
    public String getAuthor() { return "Fresa Network"; }
    public String getVersion() { return this.plugin.getPluginVersion(); }
    public boolean persist() { return true; }

    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null || params.isEmpty()) return null;
        try {
            Player online = player.getPlayer();
            boolean permUse = online == null || online.hasPermission("fchatcolor.chat.use");
            boolean permHex = online != null && online.hasPermission("fchatcolor.chat.hex");
            return resolve(this.plugin.getContext(), this.plugin.getColors(), player.getUniqueId(), permUse, permHex, params);
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Placeholder fchatcolor_" + params + " failed: " + t);
            return null;
        }
    }

    public String onPlaceholderRequest(Player player, String params) {
        return this.onRequest((OfflinePlayer) player, params);
    }

    public static String resolve(PluginContext context, UUID playerId, boolean permUse, boolean permHex, String params) {
        return resolve(context, null, playerId, permUse, permHex, params);
    }

    public static String resolve(PluginContext context, ColorCatalog catalog, UUID playerId, boolean permUse, boolean permHex, String params) {
        if (context == null || playerId == null || params == null) return null;
        ColorService service = context.getColorService();
        if (service == null) return null;
        String key = params.toLowerCase(Locale.ROOT).trim();
        String stored = readStoredColor(context, playerId);
        boolean debug = false;
        try { debug = context.getSettings() != null && context.getSettings().isDebug(); } catch (Throwable ignored) {}

        if ("has_color".equals(key)) return String.valueOf(stored != null && !stored.isEmpty());
        if ("color_hex".equals(key)) return stored == null ? "" : stored;
        if ("color_name".equals(key)) return findColorName(catalog, stored, service, permHex);
        if ("modifiers".equals(key) || "mods".equals(key)) return getModifiersPrefix(context, playerId);

        if (!permUse || stored == null || stored.isEmpty()) {
            if ("color".equals(key) || "color_legacy".equals(key) || "color_preview".equals(key)) return "";
            return null;
        }

        // modifiers prefix for rainbow
        String modsPrefix = getModifiersPrefix(context, playerId);
        if (debug) { try { context.getLogger().info("[FChatColor-DEBUG] placeholder fchatcolor_" + key + " player=" + playerId + " stored=" + stored + " permUse=" + permUse + " permHex=" + permHex + " lastMsg=" + io.github.fchatcolor.api.util.LastMessageCache.get(playerId)); } catch (Throwable ignored) {} }

        if (RainbowColor.isRainbow(stored)) {
            if ("color".equals(key) || "color_legacy".equals(key)) {
                return "";
            }
            if ("color_preview".equals(key)) {
                boolean hexAllowed = false;
                if (modsPrefix == null || modsPrefix.isEmpty()) return RainbowColor.colorize("\u2588\u2588\u2588\u2588\u2588", hexAllowed) + "\u00A7r";
                return rainbowWithModifiers("\u2588\u2588\u2588\u2588\u2588", hexAllowed, modsPrefix) + "\u00A7r";
            }
            if ("rainbow".equals(key)) {
                String last = io.github.fchatcolor.api.util.LastMessageCache.get(playerId);
                if (last == null || last.isEmpty()) last = "\u2588\u2588\u2588\u2588\u2588";
                boolean hexAllowed = false;
                return RainbowColor.colorize(last, hexAllowed);
            }
        }

        if (io.github.fchatcolor.api.util.GradientColor.isGradient(stored)) {
            java.util.List<String> palette = io.github.fchatcolor.api.util.GradientColor.parseColors(stored);
            if (!palette.isEmpty()) {
                if ("color".equals(key) || "color_legacy".equals(key)) {
                    return "";
                }
                if ("color_preview".equals(key)) {
                    boolean hexAllowed = permHex;
                    String blocks = "█████";
                    if (modsPrefix == null || modsPrefix.isEmpty()) return io.github.fchatcolor.api.util.GradientColor.colorize(blocks, palette, hexAllowed) + "§r";
                    return io.github.fchatcolor.api.util.GradientColor.colorizeWithModifiers(blocks, palette, hexAllowed, modsPrefix) + "§r";
                }
                if ("rainbow".equals(key)) {
                    String glast = io.github.fchatcolor.api.util.LastMessageCache.get(playerId);
                    if (glast == null || glast.isEmpty()) return "true";
                    return io.github.fchatcolor.api.util.GradientColor.colorize(glast, palette, permHex);
                }
            }
        }

        String effective = RainbowColor.isRainbow(stored) ? RainbowColor.currentHex() : stored;
        if ("color".equals(key)) return translateStored(effective, permHex, service, ColorMode.HEX) + (modsPrefix == null ? "" : translateModifiers(modsPrefix, service));
        if ("color_legacy".equals(key)) return translateStored(effective, false, service, ColorMode.LEGACY) + (modsPrefix == null ? "" : translateModifiers(modsPrefix, service));
        if ("color_preview".equals(key)) return translateStored(effective, permHex, service, ColorMode.HEX) + "\u2588\u2588\u2588\u2588\u2588\u00A7r";
        if ("rainbow".equals(key)) return RainbowColor.isRainbow(stored) ? "true" : "false";
        return null;
    }

    private static String translateModifiers(String mods, ColorService service) {
        if (mods == null || mods.isEmpty()) return "";
        // mods already contains section codes like §l§o, need to pass through translate to respect hex/legacy? just return as is
        return mods;
    }

    private static String getModifiersPrefix(PluginContext context, UUID playerId) {
        try {
            String raw = context.getStorage().getSelectedModifiers(playerId);
            if (raw == null || raw.trim().isEmpty()) return "";
            Set<String> set = parseModifiers(raw);
            if (set.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            // need player for perm check - try to get online player
            Player online = null;
            try { online = org.bukkit.Bukkit.getPlayer(playerId); } catch (Throwable ignored) {}
            for (String canon : set) {
                String perm = getPermissionForModifier(context, canon);
                if (perm != null && !perm.isEmpty() && online != null && !online.hasPermission(perm)) continue;
                if (canon.length() == 2 && canon.charAt(0) == '&') sb.append('\u00A7').append(Character.toLowerCase(canon.charAt(1)));
            }
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }

    private static Set<String> parseModifiers(String raw) {
        Set<String> set = new HashSet<String>();
        if (raw == null || raw.trim().isEmpty()) return set;
        String[] parts = raw.split(",");
        for (String p : parts) {
            p = p.trim().toLowerCase();
            if (p.isEmpty()) continue;
            if (p.contains("&") && p.length() > 2 && p.indexOf(',') == -1) {
                String[] sub = p.split("&");
                for (String s : sub) {
                    s = s.trim();
                    if (s.isEmpty()) continue;
                    String canon = canonicalizeModifier("&" + s);
                    if (canon != null) set.add(canon);
                }
            } else {
                String canon = canonicalizeModifier(p);
                if (canon == null && p.length() == 1) canon = canonicalizeModifier("&" + p);
                if (canon != null) set.add(canon);
            }
        }
        return set;
    }

    private static String canonicalizeModifier(String value) {
        if (value == null || value.isEmpty()) return null;
        String clean = value.trim();
        if (clean.startsWith("&") || clean.startsWith("\u00A7")) clean = clean.substring(1);
        if (clean.length() != 1) return null;
        char c = Character.toLowerCase(clean.charAt(0));
        if (c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'k' || c == 'r') return "&" + c;
        return null;
    }

    private static String getPermissionForModifier(PluginContext context, String canon) {
        return io.github.fchatcolor.api.util.CatalogReflector.modifierPermission(context.getPlugin(), canon);
    }

    private static String findColorName(ColorCatalog catalog, String stored, ColorService service, boolean allowHex) {
        if (catalog == null || stored == null || stored.isEmpty()) return "";
        ColorCatalog.Entry entry = catalog.findByValue(stored);
        if (entry == null) return "";
        return translateDisplay(entry.getDisplayName(), service, allowHex);
    }

    private static String translateDisplay(String display, ColorService service, boolean allowHex) {
        if (display == null || display.isEmpty()) return "";
        ColorMode mode = service.supports(ColorMode.MINIMESSAGE) ? ColorMode.MINIMESSAGE : (allowHex && service.supports(ColorMode.HEX) ? ColorMode.HEX : ColorMode.LEGACY);
        return service.translate(display, mode);
    }

    private static String readStoredColor(PluginContext context, UUID playerId) {
        try {
            String color = context.getStorage().getSelectedColor(playerId);
            return color == null || color.isEmpty() ? null : color;
        } catch (Throwable t) {
            try { context.getLogger().warning("[FChatColor-DEBUG] placeholder storage read failed for " + playerId + ": " + t); } catch (Throwable ignored) {}
            context.getLogger().warning("Could not read the color for the placeholder: " + t);
            return null;
        }
    }

    private static String translateStored(String stored, boolean allowHex, ColorService service, ColorMode desired) {
        if (stored == null || stored.isEmpty()) return "";
        if (!stored.startsWith("#")) return service.translate("&" + stored, ColorMode.LEGACY);
        if (desired == ColorMode.LEGACY || !allowHex) {
            Integer rgb = HexColor.parseRgb(stored);
            if (rgb == null) return "";
            char nearest = HexColor.nearestLegacyCode(rgb);
            return service.translate("&" + nearest, ColorMode.LEGACY);
        }
        return service.translate("&" + stored, ColorMode.HEX);
    }

    private static String rainbowWithModifiers(String text, boolean hex, String modifiers) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length() * 14);
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                out.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            if (hex) out.append(RainbowColor.sectionHexAt(visible));
            else out.append('\u00A7').append(RainbowColor.legacyAt(visible));
            if (modifiers != null && !modifiers.isEmpty()) out.append(modifiers);
            out.append(c);
            visible++;
        }
        return out.toString();
    }

    private static String rainbowWithModifiersAmp(String text, boolean hex, String modifiersAmp) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length() * 14);
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                out.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            if (hex) out.append(HexColor.toLegacyAmpersandHex(RainbowColor.rgbAt(visible)));
            else out.append('&').append(RainbowColor.legacyAt(visible));
            if (modifiersAmp != null && !modifiersAmp.isEmpty()) out.append(modifiersAmp);
            out.append(c);
            visible++;
        }
        return out.toString();
    }
}
