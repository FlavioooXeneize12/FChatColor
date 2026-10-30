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

import java.util.HashSet;
import java.util.Set;

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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
        if (raw != null && !raw.isEmpty()) { try { io.github.fchatcolor.api.util.LastMessageCache.set(player.getUniqueId(), raw); } catch (Throwable ignored) {} }
        if (raw == null || raw.isEmpty()) {
            return;
        }

        ColorMode mode = ColorSelector.resolve(player, context.getSettings(), context.getColorService());
        String message = raw;
        // Fast-path: skip translation when there is nothing to translate (large-server micro-opt)
        if (raw.indexOf('&') >= 0 || raw.indexOf('<') >= 0 || raw.indexOf('\u00A7') >= 0
                || raw.indexOf('#') >= 0
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChatMonitor(AsyncPlayerChatEvent event) {
        // Reaplica rainbow/gradiente si otro plugin de chat lo sobrescribio
        try {
            Player p = event.getPlayer();
            if (p == null || event.getMessage() == null || event.getMessage().isEmpty()) return;
            String eff = resolveEffectiveColor(p, context.getStorage().getSelectedColor(p.getUniqueId()));
            try { String recent = io.github.fchatcolor.api.util.LastMessageCache.getColor(p.getUniqueId()); if (recent != null && !recent.isEmpty()) eff = recent; } catch (Throwable ignored) {}
            String msg = event.getMessage();
            if (RainbowColor.isRainbow(eff)) {
                if (msg.contains("§c") || msg.contains("§6") || msg.contains("§x")) return; // ya aplicado
                String mods = getModifiersPrefix(p);
                String rainbowed = (mods == null || mods.isEmpty())
                        ? RainbowColor.colorize(msg, false)
                        : rainbowWithModifiers(msg, false, mods);
                event.setMessage(rainbowed);
            } else if (io.github.fchatcolor.api.util.GradientColor.isGradient(eff)) {
                java.util.List<String> palette = io.github.fchatcolor.api.util.GradientColor.parseColors(eff);
                if (palette.isEmpty()) return;
                if (msg.contains("§x") || msg.contains("§c") || msg.contains("§6")) return; // ya aplicado
                boolean hexCapable = context.getServerInfo().hasLegacyHex()
                        && context.getColorService().supports(ColorMode.HEX);
                String mods = getModifiersPrefix(p);
                String out = (mods == null || mods.isEmpty())
                        ? io.github.fchatcolor.api.util.GradientColor.colorize(msg, palette, hexCapable)
                        : io.github.fchatcolor.api.util.GradientColor.colorizeWithModifiers(msg, palette, hexCapable, mods);
                event.setMessage(out);
            }
        } catch (Throwable ignored) {}
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
     *
     * <p>If the stored color's permission was revoked, falls back to the
     * configured default-color (chat.default-color).</p>
     */
    private String applyPlayerColor(Player player, String message, ColorMode mode) {
        try {
            ColorStorage storage = context.getStorage();
            String color = storage.getSelectedColor(player.getUniqueId());
            String effective = resolveEffectiveColor(player, color);
            if (effective == null || effective.isEmpty()) {
                return message;
            }

            // Rainbow legacy: c,6,e,a,b,d (sin hex para que ningun chat externo lo strippee a fef...)
            if (RainbowColor.isRainbow(effective)) {
                String rainbowMods = getModifiersPrefix(player);
                if (rainbowMods == null || rainbowMods.isEmpty()) {
                    return RainbowColor.colorize(message, false);
                }
                return rainbowWithModifiers(message, false, rainbowMods);
            }

            // Gradiente personalizado (value: ["#FF4141", ...]): cicla la paleta por letra
            if (io.github.fchatcolor.api.util.GradientColor.isGradient(effective)) {
                java.util.List<String> palette = io.github.fchatcolor.api.util.GradientColor.parseColors(effective);
                if (!palette.isEmpty()) {
                    boolean hexCapable = context.getServerInfo().hasLegacyHex()
                            && context.getColorService().supports(ColorMode.HEX);
                    String gradientMods = getModifiersPrefix(player);
                    if (gradientMods == null || gradientMods.isEmpty()) {
                        return io.github.fchatcolor.api.util.GradientColor.colorize(message, palette, hexCapable);
                    }
                    return io.github.fchatcolor.api.util.GradientColor.colorizeWithModifiers(message, palette, hexCapable, gradientMods);
                }
            }

            String prefix = colorToSectionPrefix(player, effective, mode);
            if (prefix == null || prefix.isEmpty()) {
                String onlyMods = getModifiersPrefix(player);
                if (onlyMods != null && !onlyMods.isEmpty()) {
                    return onlyMods + message;
                }
                try { if (context.getSettings() != null && context.getSettings().isDebug()) context.getLogger().info("[FChatColor-DEBUG] color FAIL(legacy) player=" + player.getName() + " stored=" + color + " effective=" + effective); } catch (Throwable ignored) {}
                return message;
            }
            String mods = getModifiersPrefix(player);
            try { if (context.getSettings() != null && context.getSettings().isDebug()) context.getLogger().info("[FChatColor-DEBUG] color OK(legacy) player=" + player.getName() + " stored=" + color + " effective=" + effective + " mods=" + mods); } catch (Throwable ignored) {}
            return prefix + (mods == null ? "" : mods) + message;
        } catch (Throwable t) {
            context.getLogger().warning("Could not read the color of player "
                    + player.getName() + ": " + t.getMessage());
            return message;
        }
    }

    private String resolveEffectiveColor(Player player, String stored) {
        // Rainbow/gradiente no piden permiso aqui (ya validados en /fchatcolor set) para que el primer mensaje tras rejoin no salga blanco
        if (RainbowColor.isRainbow(stored)) return stored;
        if (io.github.fchatcolor.api.util.GradientColor.isGradient(stored)) {
            if (!io.github.fchatcolor.api.util.GradientColor.parseColors(stored).isEmpty()) return stored;
        }
        // If stored is valid and permission still held, use it
        if (stored != null && !stored.isEmpty()) {
            String perm = getPermissionForStored(stored);
            if (perm == null || perm.isEmpty() || player.isOp() || player.hasPermission("fchatcolor.admin") || player.hasPermission("fchatcolor.*") || player.hasPermission("fchatcolor.color.*") || player.hasPermission(perm)) {
                return stored;
            }
            // Permission revoked -> fall through to per-permission defaults
        }
        // Per-permission defaults: first matching permission wins (chat.default-permission-colors)
        try {
            java.util.Map<String, String> permMap = context.getSettings().getDefaultPermissionColors();
            if (permMap != null) {
                for (java.util.Map.Entry<String, String> e : permMap.entrySet()) {
                    String permKey = e.getKey();
                    String col = e.getValue();
                    if (permKey != null && col != null && !permKey.isEmpty() && !col.isEmpty() && player.hasPermission(permKey)) {
                        String resolved = resolveDefaultColor(col);
                        if (resolved != null && !resolved.isEmpty()) {
                            try { if (context.getSettings() != null && context.getSettings().isDebug()) context.getLogger().info("[FChatColor-DEBUG] default-permission-color player=" + player.getName() + " perm=" + permKey + " -> " + resolved); } catch (Throwable ignored) {}
                            return resolved;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        // Fallback to default-color
        String def = null;
        try {
            def = context.getSettings().getDefaultColor();
        } catch (Throwable ignored) {}
        return resolveDefaultColor(def);
    }

    private String getPermissionForStored(String stored) {
        return io.github.fchatcolor.api.util.CatalogReflector.colorPermission(context.getPlugin(), stored);
    }

    private String resolveDefaultColor(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none") || s.equalsIgnoreCase("disabled") || s.equalsIgnoreCase("off")) return null;
        String catalogValue = io.github.fchatcolor.api.util.CatalogReflector.colorValue(context.getPlugin(), s);
        if (catalogValue != null) return catalogValue;
        // Fallback: try direct parse
        Integer rgb = HexColor.parseRgb(s);
        if (rgb != null) return HexColor.toHex(rgb);
        String clean = s.trim();
        if (clean.startsWith("&") || clean.startsWith("\u00A7")) clean = clean.substring(1);
        if (HexColor.isLegacyCode(clean)) return HexColor.normalizeLegacyCode(clean);
        if (RainbowColor.isRainbow(s)) return "rainbow";
        return s;
    }

    /** Converts the stored DB color to § codes ready for chat. */
    private String colorToSectionPrefix(Player player, String color, ColorMode mode) {
        // Gradients/rainbow never render as a prefix (per-letra en applyPlayerColor); evita filtrar "gradient:..." literal
        if (RainbowColor.isRainbow(color) || io.github.fchatcolor.api.util.GradientColor.isGradient(color)) {
            return "";
        }
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

    private String rainbowWithModifiers(String text, boolean hex, String modifiers) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length() * 8);
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                out.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            if (hex) {
                out.append(RainbowColor.sectionHexAt(visible));
            } else {
                out.append('§').append(RainbowColor.legacyAt(visible));
            }
            if (modifiers != null && !modifiers.isEmpty()) {
                out.append(modifiers);
            }
            out.append(c);
            visible++;
        }
        return out.toString();
    }

    private String getModifiersPrefix(Player player) {
        try {
            String raw = context.getStorage().getSelectedModifiers(player.getUniqueId());
            if (raw == null || raw.trim().isEmpty()) return "";
            Set<String> set = parseModifiers(raw);
            if (set.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (String canon : set) {
                String perm = getPermissionForModifier(canon);
                if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
                    continue;
                }
                if (canon.length() == 2 && canon.charAt(0) == '&') {
                    sb.append('§').append(Character.toLowerCase(canon.charAt(1)));
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private Set<String> parseModifiers(String raw) {
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

    private String canonicalizeModifier(String value) {
        if (value == null || value.isEmpty()) return null;
        String clean = value.trim();
        if (clean.startsWith("&") || clean.startsWith("§")) clean = clean.substring(1);
        if (clean.length() != 1) return null;
        char c = Character.toLowerCase(clean.charAt(0));
        if (c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'k' || c == 'r') return "&" + c;
        return null;
    }

    private String getPermissionForModifier(String canon) {
        return io.github.fchatcolor.api.util.CatalogReflector.modifierPermission(context.getPlugin(), canon);
    }
}
